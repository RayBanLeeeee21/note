# Chapter 10 RDB持久化

## 10.1 RDB文件的创建与载入

SAVE/BGSAVE
- SAVE: 阻塞到保存结束
- BGSAVE: `fork()`一个子线程执行, 主线程可继续操作
- 载入: 服务器启动时自动载入, 只有AOF持久化关闭时, 才会载入RDB文件, **不用显式载入**

### 10.1.3 BGSAVE命令执行时的服务器状态

BGSAVE互斥
- BGSAVE过程中拒绝SAVE/BGSAVE命令, 防止竞争
- BGSAVE过程中阻塞BGREWRITEAOF, 使其滞后执行
    - 不存在竞争, 但会影响性能
- BGREWRITEAOF过程中阻塞BGSAVE

## 10.2 自动间隔性保存


自动保存
- 语法(在配置中): `SAVE t count`
    - 自上次save以来, 如果t秒内count次, 则自动**BGSAVE** (不用SAVE是防止client无法访问)
    - 可以同时设定多个save, 只要满足一个就会BGSAVE
- 实现:
    -   ```cpp
        typedef struct RedisServer{
            struct saveparam * saveparams;  // save参数数组
            long long dirty;                // 记录上次以来修改(增删改)的次数
            time_t lastsave;                // 记录上次修改的时间
            //...
        };
        ```
    -   ```cpp
            struct saveparam{               // saveparam数组
                time_t seconds;             // 统计时间区间
                int changes;                // 时间区间内的修改次数
            };
        ```
    - 修改数据时更新dirty和lastsave值
    - 100ms 检查一次条件, 在saveparam数组中只要有满足条件的, 就BGSAVE


## RDB文件结构
RDB文件结构
- RDB结构
    -   ```YAML
        RDB:
            "REDIS" flag:       5 byte      # "REDIS"固定开头
            db_version:         4 byte      # ascii表示, 如"0006"保存为0x30303036
            databases:          []          # 可能含有多个database
            EOF flag:           1 byte
            check_num:          8 byte      # 由前面所有内容计算得到的校验码
        ```
    - redisServer中所有database都为空时, databases字段为空
    - check_num由前面所有内容计算得到
- database结构
    -   ```yaml
        database:
            SELECTDB flag:      1 byte
            db_number:          1/2/5 byte
            key_value_pairs:          
        ````
- key_value_pairs结构
    -   ```yaml
        key_value_pairs:
            EXPIRETIME_MS flag: 1 byte      # 仅在对象被设置过期才有
            ms:                 8 byte      # 仅在对象被设置过期才有
            TYPE:               1 byte
            key:                
        ````
- TYPE类型
    - REDIS_RDB_TYPE_STRING
    - REDIS_RDB_TYPE_LIST
    - REDIS_RDB_TYPE_SET
    - REDIS_RDB_TYPE_ZSET
    - REDIS_RDB_TYPE_HASH
    - REDIS_RDB_TYPE_LIST_ZIPLIST
    - REDIS_RDB_TYPE_SET_INSET
    - REDIS_RDB_TYPE_ZSET_ZIPLIST
    - REDIS_RDB_TYPE_HASH_ZIPLIST

对象存储
- string:
    - 对应TYPE: REDIS_RDB_TYPE_STRING
    - 不同encode存储:
        - int:
            ```YAML
            string:
                encoding    1 byte      # REDIS_RDB_ENC_INT8/_INT16/_INT32
                integer     8/16/32bit
            ```
        - raw (非压缩):
            ```YAML
            string:
                len         1 byte      # REDIS_RDB_ENC_INT8/_INT16/_INT32
                string     8/16/32bit
            ```
        - raw (压缩):
            ```YAML
            string:
                REDIS_RDB_ENC_LZF   1 byte
                compressed_len      
                origin_len      
                compressed_string
            ```
- list/set:
    - 对应TYPE: REDIS_RDB_TYPE_LIST/REDIS_RDB_TYPE_SET
    ```yaml
    list/set:
        list_length/set_size
        string1                 # 以string的方式存储, 即len+string
        string2                 # 以string的方式存储, 即len+string
        ...
    ```
- hash
    - 对应TYPE: REDIS_RDB_TYPE_HASH
    ```yaml
    hash:
        hash_size
        key_value_pairs1         # 此处的key_value_pair中, value只能是string
        key_value_pairs2         # 此处的key_value_pair中, value只能是string
        ...
    ```
- hash
    - 对应TYPE: REDIS_RDB_TYPE_HASH
    ```yaml
    hash:
        hash_size
        key_value_pairs1         # 此处的key_value_pair中, value只能是string
        key_value_pairs2         # 此处的key_value_pair中, value只能是string
        ...
    ```
- zset
    - 对应TYPE: REDIS_RDB_TYPE_HASH
    - **分值转成string类型再按string类型保存**
    ```yaml
    zset:
        zset_size
        string                   # 此处的key_value_pair中, value只能是string
        score                    # 分值转成string类型再按string类型保存
        ...
    ```
- REDIS_RDB_TYPE_*_ZIPLIST: 
    - 整个转成string再按string的方法保存
    - 载入时, 先按string的方式读入, 根据TYPE参数, 转成list/hash/zset
- REDIS_RDB_TYPE_SET_INTSET:
    - 整个转成string再按string的方法保存
    - 载入时, 先按string的方式读入再转成SET