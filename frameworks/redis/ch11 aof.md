# chapter 10 AOF
AOF
* 特点：
    * 纯文本格式
    * 只保存修改操作(select也会保存)

## 保存
循环事件
* 过程
    ```python
    def loopEvent():
        while true:
            # 处理文件事件(save等), 
            # 接收用户命令请求
            # 发送命令回复等
            processFileEvents();
            
            # 过期键检查
            # 定时bgsave
            # 定时bgrewriteaof等
            processTimeEvents();

            # 根据保存策略来决定要不要进行aof
            flushAppendOnlyFile();
    ```

flushAppendOnlyFile()中的保存策略
* appendfsync配置 : no/everysec/always
    * always: 每次修改操作都触发aof追加保存
        * 效率低, 最安全
    * everysec: 每次事件循环时检查离上次aof追加有没有达到1sec, 达到并且有修改时, 把缓冲区记录的修改操作都flush
        * 效率中, 安全性中
    * no: 由操作系统决定, 如等到缓冲区满时自动flush 
        * 效率高, 安全性低

## 载入
载入方式
* 在初始化时循环进行(解析命令-执行命令)直到所有命令执行完

## AOP重写
实现:
1. 遍历数据库, 对每个数据库生成一条select命令追加到aof文件中
    1. 对数据库中的每个对象生成一条/多条插入/增加元素的命令
        * 如果元素过多(如SADD/RPUSH超过64个元素)
        * LIST要用**RPUSH**而不是LPUSH保持顺序
    2. 最后把键的过期命令加上
优点: **不会有多余的命令**

## AOP后台重写
实现:
1. 主线程的遇到BGREWRITEAOF命令时, 产生一个新的子线程处理重写
2. 重写阶段:
    * 主线程执行完修改命令后, 要将命令写入**AOF缓冲区**和**AOF重写缓冲区**
        * AOF缓冲区用于定时进行AOF追加的任务, 保证数据库与当前AOF文件的一致
        * AOF重写缓冲区用于最后在重写的AOF文件中追加
    * 子线程遍历数据库, 生成命令, 写入到新AOF文件
3. 子线程处理完AOF重写后向主线程发信号
4. 主线程接到信号调用**信号处理函数**, 此时会阻塞, 无法接收命令
    1. 将缓冲区的数据flush到新AOF文件结尾
    2. 以**原子**的方式替换旧AOF