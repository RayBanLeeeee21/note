IO工作机制:
* 标准/直接:
    1. 标准访问文件方式:
        * 缓存分为用户地址空间的**应用缓存**和内核地址空间的**高速页缓存**
        * read时缓存有数据则从缓存读取, 否则从磁盘读, 并保存到缓存
        * write时保存到缓存, 写入磁盘的时机不确定, 除非用户显式sync
    2. 直接IO
        * 程序read()/write()操作都不经过高速缓存, 直接从磁盘取到用户空间的应用缓存
        * 适用于数据库管理系统的实现
* 同步/异步:
    1. 同步访问方式:
        * 当数据写入磁盘后, 程序才继续往下走
    2. 异步访问方式: 
        * 程序调用write()后立即返回
* 内存映射
    * 将某一段内存地址与磁盘中的文件相关联,
    * 减少数据从内核空间到用户空间的复制次数

# NIO

SelectorKey (sun.nio.ch.SelectionKeyImpl实现)
* 保存数据:
    * 关联的channel
    * 关联的selector
    * volatile interestOps
    * readyOps
    * attachment (继承自SelectKey)

Selector
* int select() 与 Set<SelectionKey> selectedKeys();
    * select()以阻塞的方式更新就绪的key, 返回就绪的key个数
    * selectedKeys()返回就绪的key集 (**非线程安全的Set**)

AbstractSelectableChannel
* 主要特点:
    * 以线程安全的方式维护一个keys数组, 用于记录关联的Selector
* 注册算法: 
    * AbstractSelectableChannel.register(Selector sel, int ops, Object att): SelectionKey 
    ``` java
        synchronized(regLock){
            检查channel是否关闭
            检查ops参数合法性
            检查是否非阻塞
            在this的keys数组中搜索是否有注册到Selector的key, 如果有
                追加ops参数中的interest
            如果没有
                synchronized(keyLock){
                    在keys数组中加入新key 
                    在sel中注册新key (SelectorImpl.register(AbstractSelectableChannel abstractSelectableChannel, int n, Object object):SelectionKey)
                }
        }
    ```
    * AbstractSelectableChannel abstractSelectableChannel, int n, Object object):SelectionKey 
    ``` java
        创建新key
        key.attach(object)
        sychronized(this.publicKeys){
            在this.keys中加入key
        }
    ```