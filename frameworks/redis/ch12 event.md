# chapter 12 event

服务器需要处理的事件:
* 文件事件: 对socket事件的抽象
* 时间事件


## 文件事件
文件事件处理器
* 模型:IO多路复用
    * 用**单线程**来同时接收多个客户端的请求, **文件事件分派器**根据请求的类型分派给不同的**文件事件处理器**
* 底层实现: 
    * selec (POSIX, 一般系统都支持)
    * epoll (LINUX)
    * evport (Solaris)
    * kqueue (Mac)
    * 对不同的底层实现进行了包装, 但提供了相同的API
    * 不同的底层实现可以替换, 根据实际情况进行选择
* Redis规定的事件
    * AE_READABLE事件
        * 客户端connect()
        * 客户端write()
        * 客户端close()
    * AE_WRITABLE事件
        * 客户端read()
    * 两种事件同时出现时, 优先处理AE_READABLE, 再处理AE_WRITABLE
* 文件事件处理器类型:
    * 连接应答处理器
    * 命令请求处理器
    * 命令回复处理器
    * 复制功能处理器

客户端-服务端连接示例
1. 服务端的监听socket收到客户端connect()请求时, 产生**AE_READABLE事件**, 引发**连接应答处理器**执行
    * 连接应答处理器执行: 产生客户端socket, 将客户端socket的AE_READABLE事件与**命令请求处理器**关联
    * AE_READABLE事件与**命令请求处理器**一直关联到客户端断开连接
2. 客户端socket收到客户端的命令时, 产生AE_READABLE事件, 由关联的**命令请求处理器**执行
    * 命令请求处理器执行: 执行命令, 产生结果, 然后将AE_WRITABLE事件与**命令回复处理器**关联
3. 客户端准备好(执行recv())时, 产生AE_WRITABLE事件, 由关联的**命令回复处理器**执行
    * 命令回复处理器执行: 将结果发给客户端, 然后解除AE_WRITABLE事件与命令回复处理器的关联

## 时间事件

时间事件分类:
* 定时事件
* 周期事件

时间事件属性:
* id: 时间事件id
* when: 执行的时间点
* timeProc: 到时间时要执行的函数
* timeProc返回值(NO_MORE): 用于区分是**定时事件**还是**周期事件**

时间事件执行
* 流程
    ```python
    def processTimeEvents:
        for timeEvent in timeEvents                      # timeEvents: RedisServer中的链表
            if timeEvent.when <= now()
                retVal = timeEvent.timeProc();
                if retVal == NO_MORE
                    deleteTimeEventFromServer(timeEvent)
                else 
                    updateWhen(timeEvent, retVal)
    ```
* serverCron
    * 更新统计信息
    * 清理过期键值对
    * 清理掉线客户端
    * RDB周期持久化
    * 对于主服务器, 定期同步
    * 对于集群模式, 定期同步和连接测试

## 事件驱动机制
* 事件处理
    ```python    
    def aeProcessEvents():
        timeEvent = aeSearchNearestTimer()
        remainMs = timeEvent.when - now()
        if remainMs < 0
            remainMs = 0;

        # 根据remain时间计算应该阻塞的时间
        timeVal = createTimeValWithMs(remainMs)  

        # timeVal为0时直接返回
        aeApiPoll(timeVal)  

        processFileEvents();
        processTimeEvents();
    ```
* main
    ```python
    def main():
        initServer()
        while serverIsNotShutdown()
            aeProcessEvents()
        cleanServer()
    ```
* 特点:
    * 与 **最近时间事件** 相关的阻塞时间可以防止频繁的访问
    * 没有时间事件出现时可以处理文件事件
    * 所有处理都是**原子, 有序, 同步**的
    * **防饥饿机制**: 主动让步
        * 如, 命令回复处理器的命令回复过长时, 可以留到下一次循环再继续处理
    * 时间事件通常处理的执行时间都稍晚

