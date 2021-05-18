# Topics

对象:
- 5大基本逻辑结构: string, list, set, zset, hash
- 数据结构: 
    - 大尺寸: SDS, linkedList, quicklist, hashtable, skiplist
    - 小尺寸: int, embstr, ziplist, intset
- 对象特性: 多态, LRU, 过期

存储
- RDB
    - 保存和载入时机
    - 自动保存
    - 并发处理(与其它保存过程并发)
    - 过期键处理
- AOF
    - 保存和载入时机
    - AOF重写
    - AOF后台重写(BGREWRITEAOF)
    - 过期键处理
- 与主从复制的关系


# 服务器

键空间
- 键空间基本信息: dict, expires
- 过期
    - 过期对象存储
    - 过期对象清理策略
    - 与RDB, AOF, 主从复制的关系
- [淘汰策略](https://zhuanlan.zhihu.com/p/91539644)
    - noeviction
    - volatile-lru
    - volatile-lfu
    - volatile-random
    - volatile-ttl
    - allkeys-lru
    - allkeys-lfu
    - allkeys-random

服务端: 
- **事件环**(aeMain)
    - 实现方法: IO多路复用
    - 事件类型: 
        - 文件事件
            - 读事件(AE_READABLE)
            - 写事件(AE_WRITABLE)
        - 时间事件
            - **serverCron**
- **命令执行过程解析**



# 客户端

客户端
- 已连接客户端在服务端的保存方法
- 特殊客户端
    - 主从复制时, 主从互为客户端
    - Lua伪客户端
    - AOF伪客户端
- 命令执行实现: 
    - 输入缓冲区
    - 命令解析与执行
    - 输出缓冲区

# 主从复制
- SYNC
- PSYNC
    - 部分重同步
- 主从关系建立过程
- 主从连接保活: 
    - 心跳
    - min-slave

# 事务
- 命令: watch, multi, discard, exec
- 完整事务执行过程
- ACID分析