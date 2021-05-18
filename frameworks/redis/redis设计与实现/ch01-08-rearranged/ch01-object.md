# Chapter 01 对象

## 1. 数据结构
- `redisObject`
    ```cpp
    typedef struct redisObject {
        unsigned type:4;        // 类型
        unsigned encoding:4;    // 编码
        unsigned lru:LRU_BITS;  // LRU 时间 (与全局lru_clock相关), 可以用来计算空转时长
        int refcount;           // 指向该对象的引用个数, 回收时用到
        void *ptr;              // 指向实际数据结构的指针
    } robj;
    ```

## 2. 类型与编码

redis对象:
- 类型(逻辑结构): list, hash, set, zset, string
    ```c++
    #define OBJ_STRING  0       /* String object. */
    #define OBJ_LIST    1       /* List object. */
    #define OBJ_SET     2       /* Set object. */
    #define OBJ_ZSET    3       /* Sorted set object. */
    #define OBJ_HASH    4       /* Hash object. */
    ```
- 编码(数据结构): 即具体实现
    ```c++
    #define OBJ_ENCODING_RAW        0   // raw SDS
    #define OBJ_ENCODING_INT        1   // 整型
    #define OBJ_ENCODING_HT         2   // 哈希表
    #define OBJ_ENCODING_ZIPMAP     3   /* Encoded as zipmap */
    #define OBJ_ENCODING_LINKEDLIST 4   // 链表
    #define OBJ_ENCODING_ZIPLIST    5   // 压缩列表
    #define OBJ_ENCODING_INTSET     6   // 整数集合
    #define OBJ_ENCODING_SKIPLIST   7   // 跳表
    #define OBJ_ENCODING_EMBSTR     8   // embstr
    #define OBJ_ENCODING_QUICKLIST  9   // quicklist: 以压缩列表为结点的链表
    ```

类型的编码
- string:
    - int
    - embstr
    - raw
- list
    - ziplist (<=2.8.9)
    - linkedlist (<=2.8.9)
    - quicklist
- set
    - hashtable
    - intset
- hash
    - hashtable
    - ziplist
- zset
    - skiplist
    - ziplist

## 3. 类型检查与多态

类型检查: 检查`redisObject->type`是否匹配
![类型检查](./resources/ch08-robj-type-check.png)

多态命令实现: 根据`redisObject->type`确定类型, 然后根据`redisObject->encoding`确定编码, 然后进行类型转换, 进行具体操作
![多态](./resources/ch08-robj-polymorphic.png)


## 4. 内存回收

引用计数法: redis服务器中对对象的引用进行计数
- 创建时引用为1
- 有另一个程序使用同一个对象时, 引用+1
- 某个程序不再使用该对象时, 引用-1
- 引用为0时释放

**为什么使用引用计数法?**: 
- redis不支持对象的互相引用, 不会存在循环引用的问题
- 引用计数法进行内存回收实现起来简单

## 5. 共享对象

共享对象: 在字符串/整数值相同的情况下, redis可能不会为键创建新对象, 而是指向已有对象, 然后引用+1
- 静态对象: 
    - redis启动时会为0-9999创建字符串实例
    - 在新版本中, 这些静态对象的引用计数值为`OBJ_SHARED_REFCOUNT(2^31-1)`

**为什么不共享其它对象**? 共享的前提是对象值相等, 但判断复杂对象是否相等开销比较大

## 6. 对象空转时长

`redisObject->lru`: 记录了对象上一次访问的时间
- `OBJECT IDLETIME key`可以返回上次访问时间到现在的间隔
    - 除了`IDLETIME`命令名, 所有对key的命令都会造成lru更新