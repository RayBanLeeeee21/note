# Chapter 04 hash


## 1. 对象编码

hash:
- hashtable: 不满足ziplist条件的都用hashtable表示
- ziplist: `len(key) <= 64 && len(val) <= 64 && count(key) <= 512`

### 1.1 hashtable

数据结构
- 字典:
    ```c++
    typedef struct dict {
        dictType *type;      // 与值类型相关的特定操作
        void *privdata;      // 与值类型相关的私有数据
        dictht ht[2];        // 渐进式hash时用到
        long rehashidx;      // 渐进式hash时用到记录下一个需要拆分的链表. 未处于rehash阶段时, 设为-1
        int iterators; /* number of iterators currently running */
    } dict;
    ```
- 字典哈希表:
    ```c++
    typedef struct dictht { // Dict Hash Table
        dictEntry **table;      // entry链表首结点指针的数组
        unsigned long size;     // 哈希表大小
        unsigned long sizemask; // hash取余用的mask
        unsigned long used;     // 已有结点数
    } dictht;
    ```
- Entry:
    ```c++
    typedef struct dictEntry {
        void *key; 
        union {
            void *val; uint64_t u64; int64_t s64; double d;
        } v;    // 可保存不同类型数据
        struct dictEntry *next; // 链表指针
    } dictEntry;
    ```
- 类型操作:
    ```c++
    typedef struct dictType {
        // 该类型的hash计算方法
        unsigned int (*hashFunction)(const void *key);

        // 该类型的key匹配方法
        int (*keyCompare)(void *privdata, const void *key1, const void *key2);

        // 该类型的key复制方法
        void *(*keyDup)(void *privdata, const void *key);   // 扩容时用到

        // 该类型的key回收方法
        void (*keyDestructor)(void *privdata, void *key);

        // /该类型的value复制方法
        void *(*valDup)(void *privdata, const void *obj);   // 扩容时用到

        // 该类型的value回收方法
        void (*valDestructor)(void *privdata, void *obj);
    } dictType;
    ```

特点:
- hash算法: murmurHash2
- 链冲突解决: 字典中不保存尾指针, 因此新结点都是加到队头, 使复杂度为O(1)
- 扩容与收缩: 
    - 动态路由因子: 
        - 未做后台保存(BGSAVE/BGREWRITEAOF)时, 负载因子阈值为1
        - 进行后台保存(BGSAVE/BGREWRITEAOF)时, 负载因子阈值提到5, 避免一边扩容一边后台线程遍历hash写文件
            - *有些系统使用copy-on-write的方式创建子进程, 为防止创建子进程后发生write, 故提高阈值, 避免发生内存的修改*
        - 收缩阈值: 负载因子小于0.1

渐进式rehash:
- 设计原因: 防止一次性rehash造成某个插入请求的响应时间过长(都在处理rehash)
- 实现方法:
    - `dict->ht[2]`中的第二个dictht用来新的扩展后的哈希表
    - `dict->rehashidx`用来记录下一次需要进行拆分的链表
        - 进入渐进式rehash状态后, 该值设为0
        - 每个对字典元素的访问请求(增删改查等)都要顺便拆分`rehashidx`指定的链表, 放到新的哈希表, 然后将`rehashidx`+1
        - 每个对字典元素的访问请求都会在两个哈希表都找一遍
        - 渐进式rehash完成后, `rehashidx`又改回-1

应用场景
- hash类型的编码之一
- **redis数据库**

### 1.2 ziplist

参考[ziplist](./ch03-list.md##12-ziplist)

## 2. 所有操作

[所有操作](http://redisdoc.com/hash/index.html)(**都是原子操作**)

set & get
- `HSET key field value [field value]`
    - **返回**: 新增加的field的个数
- `HSETNX key field value`: 不存在key时才创建
- `HMSET key field value [field value]`: 类似HSET
    - **返回**: OK
- `HGET key field`: 
    - **返回**: field对应的值
- `HMGET key field [field ...]`: 
    - **返回**: 多个field对应值的列表
- `HKEYS key`: 
    - **返回**: 所有field组成的列表
- `HVALS key`: 
    - **返回**: 所有field的值组成的列表
- `HMGETALL key`
    - **返回**: 所有键值组成的列表, 顺序为`[f1, v1, f2, v2, ...]`


改
- `[HINCRBY|HINCRBYFLOAT] key field increment`: field对应的值增加
    - **返回**: 新值

查
- `HEXISTS key field`
    - **返回**: 0: 不存在; 1: 存在
- `HLEN`: 
    - **返回**: 哈希表的元素个数

删
- `HDEL key field [field ...]`:
    - **返回**: 1: field存在并删除成功; 0: 不存在