# Chapter 16 Sentinel



启动sentinel
- `redis-sentinel sentinel.conf`
- `redis-server sentinel.conf --sentinel`


sentinel专用操作:
- 所有对数据的操作不可用
- 持久化操作不可用
- `SUBSCRIBE`/`PUBLISH`操作内部可用
    - 感知其它sentinel时用到

sentinel可用命令
- `PING`: 探测主从机时用到
- `UNSUBSCRIBE`/`SUBSCRIBE`/`PUNSUBSCRIBE`/`PSUBSCRIBE`/`PUBLISH`/: 感知其它sentinel用到
- `INFO`: 获取主从机信息时用到
- `SENTINEL`: 故障检测与选举时用到


数据结构
- `sentinelState`: sentinel专有状态
    ```cpp
    struct sentinelState {
        // ...
        dict *masters;      // 主机字典, 该sentinel管理的所有主机
        // ...
    } sentinel;
    ```
- `sentinelRedisInstance`: 对应一个主/从机
    ```cpp
    typedef struct sentinelRedisInstance {
        int flags;                  /* 包括SRI_MASTER | SRI_SLAVE | SRI_S_DOWN | SRI_O_DOWN等状态 */
        char *name;                 /* Master name from the point of view of this sentinel. */
        char *runid;                /* runid*/
    
        sentinelAddr *addr;         /* 主机 IP & port */
        
        // ...
        mstime_t down_after_period; /* 主观下线时间 */
        unsigned int quorum;        /* 判断客户下线的阈值 */
        
        /* Master specific. */
        dict *sentinels;            /* 其它监视该主机的sentinel */
        dict *slaves;               /* 该主机的slaves */
        
        int parallel_syncs;         /* How many slaves to reconfigure at same time. */
     
        mstime_t failover_timeout;  /* Max time to refresh failover state. */

    } sentinelRedisInstance;
    
    // 地址
    typedef struct sentinelAddr {
        char *ip;
        int port;
    } sentinelAddr;
    ```

sentinel与主/从机的连接
- 命令连接
- 订阅连接: 订阅``__sentinel__:hello``, 接收其它sentinel推送的信息

定时获取主/从机信息:
- 间隔: 10s
- 命令: INFO
- 获取: 
    - 主机信息
    - 主机下的从机信息
- 更新到:
    - 主机信息: 更新到`sentinelRedisInstance`
    - 从机信息: 更新到主机的`sentinelRedisInstance->masters->slaves`

订阅连接
- sentinel既订阅主/从机的``__sentinel__:hello`, 也通过该频道发布
- 传递信息:
    - 主/从机信息: runid, epoch, ip & port
    - sentinel信息: runid, epoch, ip & port
- 更新到:
    - `sentinelRedisInstance`
    - `sentinelRedisInstance->masters->sentinels`


sentinel之间通信
- 订阅/发布: sentinel之前没有订阅连接, 但会以监视的主/从为中介进行订阅发布, 从而发现彼此
    - 2s发布一次
- 命令连接: 判断机器客观下线&重选主机时用到


主观下线判断过程 
1. 间隔1s发一次`PING`, 如果在`down-after-milliseconds`内没收到有效回复, 设为主观下线`SRI_S_DOWN`
    - 有效回复: `+PING`, `-LOADING`, `-MASTERDOWN`
2. 判断到主观下线后, 通过命令连接向其它sentinel询问, 发`SENTINEL is-master-down-by-addr <IP> <port> <epoch> *`
    - 其它sentinel回复
        ```
        1) <down_state> 
        2) * 
        3) <leader_epoch>
        ```
3. 收集到足够的确定回复后(数量达到`sentinelRedisInstance->quorum`), 设为客观下线`SRI_O_DOWN`


## 16.8 选举领头leader

只有在需要故障转移时会选leader

选举过程
1. 拉票: 发`SENTINEL is-master-down-by-addr <IP> <port> <epoch> <runid>`, 其中runid是自己的, epoch为新的纪元
2. 投票: 回复以下内容, 
    ```
    1) <down_state> 
    2) <leader_runid>
    3) <leader_epoch>
    ```
    - 收到选票的sentinel会将该epoch中第一个拉票的选为leader, 其它的拒绝掉
3. 如果投票失败(多个候选leader都达不到最大多数票), 则过一段时间再选举, 直到选出leader

## 16.9 故障转移

故障转移
1. 挑选一个从机变成主机, 发`SLAVEOF no one`命令
2. 通知其它从机变成新主机的从机, 发`SLAVEOF <ip> <port>`
3. 持续监视下线的主机, 直到其上线后, 通知其成为新主机的从机


挑选新主机的原则:
1. 剔除所有下线的从机
2. 剔除最近**5s内**未回复sentinel的INFO的从机
3. 剔除 `down-after-milliseconds * 10 (ms)` 未连与原主机通信的从机
    - 保证选到的从机的数据是比较新的
3. 按**优先级 -> 复制偏移量 -> id**排序