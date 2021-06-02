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

## HyperLogLog
参考
- [基本操作-1](https://www.runoob.com/redis/redis-hyperloglog.html)
- [走近源码：神奇的HyperLogLog](https://zhuanlan.zhihu.com/p/58519480)
- [Reids—神奇的HyperLoglog解决统计问题](https://mp.weixin.qq.com/s/9dtGe3d_mbbxW5FpVPDNow)

特点:
- 底层结构是`SDS`. 但普通string不能当HyperLogLog操作, HyperLogLog开头有个魔数`HYLL`
    ```
    127.0.0.1:6379> PFADD a 1
    (integer) 1
    127.0.0.1:6379> type a
    string
    127.0.0.1:6379> object encoding a
    "raw"

    ```

## 淘汰策略

参考:
- [彻底弄懂Redis的内存淘汰策略](https://zhuanlan.zhihu.com/p/105587132)

淘汰策略
- noeviction: 不过期, 报错
- volatile-ttl: 淘汰最早过期的一批key
- volatile-random: 随机淘汰一批带超时的key
- volatile-lru: 淘汰最近最少使用的一批带超时的key
- volatile-lfu: 淘汰使用频率最少的一批带超时的key
- allkeys-random: 随机淘汰一批key
- allkeys-lru: 淘汰最近最少使用的一批key
- allkeys-lfu: 淘汰使用频率最少的一批key


LRU与LFU实现:
- 近似LRU: 没有准确地排LRU队列, 而是随机采样多次, 每次先最久的key淘汰
- LFU: 除了统计频率外, 还要隔一段时间对频率做递减操作
    - 数据结构: 也是使用`redisObject->lfu`, 高16位表示时间, 低8表示计数(最大255)
    - 参数:
        - `lfu-decay-time`: 以分钟为单位. 访问key时, 计算到过了N个decay, 就要减N
        - `lfu-log-factor`: 影响增长速度的因子, factor越大, 增长越慢
            - 计算方法: 由公式可知counter越大越难增长, factor越大也越难增长, 还会随着时间下降
                ```
                baseVal = counter - LFU_INIT_VAL
                if (rand(0, 1)) < 1.0 / (baseVal * lfu_log_factor + 1) counter++;
                ```

## 多线程

[Redis 6.0 多线程IO处理过程详解](https://zhuanlan.zhihu.com/p/144805500)

## 缓存

缓存分类: 本地, 分布式, 多级缓存
- 一致性问题

