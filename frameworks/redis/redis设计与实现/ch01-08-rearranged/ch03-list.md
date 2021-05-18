# Chapter 03 list

## 1. 对象编码

list:
- ziplist (<=2.8.9)
    - `len(entry) <= 64 && count(entry) <=512`
- linkedlist (<=2.8.9)
    - `len(key) <= 64 && count(key) <= 512`
- quicklist

### 1.1 linkedlist

数据结构
-   ```c++
    typedef struct listNode{
        struct listNode* prev;
        struct listNode* next;
        void* value;      // void*指针可以指向任何类型
    };
    ```
-   ```c++
    typedef struct list{
        listNode * head;
        listNode * tail;
        unsigned int len;
        void* (*dup)(void *ptr); // 与value类型匹配的复制函数, 扩容等操作时用到
        void (*free)(void *ptr); // 与value类型匹配的释放函数
        int (*match)(void *ptr,  // 与value类型匹配的对比函数
                     void *key) 
    };
    ```

特点:
- 记录长度
- 双向
- 无环
- 带头尾指针
- 多态


应用场景:
- list对象
- 服务端中存的客户端链表(参考章节13.2.1)
- 客户端输出缓存区链(参考章节13.1.7)
- 模式订阅链表
- 慢查询日志链表
- 监视器链表

### 1.2 ziplist

数据结构
- 压缩列表(没有对应的struct)
    ```cpp
    uint32_t zlbytes;               // 总字节数
    uint32_t zltail;                // 尾节点偏移, 追加时用到
    uint16_t zllen;                 // 总元素个数
    struct ziplistEntry *entries;   // 元素
    zint8_t zlend;                  // 结束符0xFF
    ```
- `zlentry`: 该数据结构是解压后的结构, 压缩的更紧凑. 解压的方便处理
    ```cpp
    typedef struct zlentry {
        unsigned int prevrawlensize, prevrawlen; // 表示prev偏移的size, pre偏移量
        unsigned int lensize, len;               // 表示对象len的size, 对象的len
        unsigned int headersize;                 // = prevrawlensize + lensize
        unsigned char encoding;                  // 编码: 表示该
        unsigned char *p;                        // 对象数据
    } zlentry;
    ```
- 压缩列表结点(压缩)
    - previous_entry_length: 前一个结点的偏移量
        - 长度: 1/5字节
    - encoding: 表示该结点存的数据类型: 
        - 长度: 1/2/5字节
        - 字节数组: 长度为(1<<6)-1, (1<<14)-1, (1<<32)-1
        - 整型: 4位长(0-12), 1字节, 3字节, uint16_t, uint32_t, uint64_t
    - content: 内容
    - <img src="../resources/ch07-ziplist-node.png" style="width:100px"/>

特点:
- 本质上是单向链表, 带尾指针
    - 结点空间是连续分配的, 用前向偏移(而不是prev指针)来指向上一个元素
    - 通过记录总长度和终止符(zlend=0xFF)界定总长度
    - 每个结点中表示长度的类型是动态的, 通过`encoding`区分类型
- **不可变**: 所有会改变结点或链表长度的操作都要**重分配**空间
- 占内存小
- 缺点:
    - 连锁更新: 前续结点长度变大时, 可能造成后续结点的`prev_entry_length`类型转换, 再导致该结点长度也变大, 连锁传播到后面结点. 变小也可能有这个问题
        - 概率较小, 只有刚好结点都在边界值时会出现
    - 重分配: 分配结点不够空间时需要重分配内存

应用场景:
- list类型的编码之一
- hash类型的编码之一
- zset类型的编码之一

### 1.3 quicklist

quicklist在新版本中出现, 结合了linkedlist和ziplist
- 宏观是linkedlist, 结点为ziplist


数据结构:
- quicklist
    ```cpp
    typedef struct quicklist {
        quicklistNode *head;
        quicklistNode *tail;
        unsigned long count;        /* total count of all entries in all ziplists */
        unsigned long len;          /* number of quicklistNodes */
        int fill : QL_FILL_BITS;              /* fill factor for individual nodes */
        unsigned int compress : QL_COMP_BITS; /* depth of end nodes not to compress;0=off */
        unsigned int bookmark_count: QL_BM_BITS;
        quicklistBookmark bookmarks[];
    } quicklist;
    ```
- quicklistNode
    ```cpp
    typedef struct quicklistNode {
        struct quicklistNode *prev;
        struct quicklistNode *next;
        unsigned char *zl;           // ziplist作为结点
        unsigned int sz;             /* ziplist size in bytes */
        unsigned int count : 16;     /* count of items in ziplist */
        unsigned int encoding : 2;   /* RAW==1 or LZF==2 */
        unsigned int container : 2;  /* NONE==1 or ZIPLIST==2 */
        unsigned int recompress : 1; /* was this node previous compressed? */
        unsigned int attempted_compress : 1; /* node can't compress; too small */
        unsigned int extra : 10; /* more bits to steal for future usage */
    } quicklistNode;
    ```


## 2. 所有操作

[所有操作](http://redisdoc.com/list/index.html)(**都是原子操作**)

增
- `[LPUSH|RPUSH] key element`: 在列表左边|右边加, 无则创建列表
    - **返回**: 新长度
- `[LPUSHX|RPUSHX] key element`: 只有存在时才加
- `LINSERT key before pivot element`: 在某个旧值前|后加新值
    - **返回**: 新长度, **找不到旧值时返回-1**


删
- `[LPOP|RPOP] key element [count]`: 弹出最左|最右值, count为弹出个数
    - **返回**: 
        - 无count: 返回弹出的值, 无则返回nil.
        - 有count: 返回弹出值列表, 超出长度时弹出所有值
- `[BLPOP|BRPOP] key [key ...] timeout`: 带阻塞的pop, 列表不存在或者为空时阻塞(单位为秒)
    - `timeout=0`表示无限等待
    - **返回**: 空或者左|右值
- `LREM key count element`: 
    - count > 0: 从左开始删除count个指定值
    - count < 0: 从右开始删除-count个指定值
    - count = 0: 删除所有
    - **返回**: 新列表

改
- `LSET key index element`: 将索引为index的值改成element
    - **返回**: OK, 或报错
- `RPOPLPUSH source destination`: 从source右边弹出一个元素, push到destination左边
    - **返回**: 被弹出的元素或空
- `BRPOPLPUSH source destination timeout`: 阻塞版RPOPLPUSH, source不存在或者为空时等到timeout
    - `timeout=0`表示无限等待
    - **返回**: 被弹出的元素或空
- `LTRIM key start end`: 将范围`[start, end]`以外的值去掉
    - **返回**: OK

查
- `LLEN key`: 返回长度
- `LINDEX key index`: 
    - **返回**
        - index >= 0: 索引为index的值
        - index < 0: 索引为`len - index`的值(即从右数)
- `LRANGE key start end`: 
    - **返回**: 范围为`[start, end]`的子列表


与分布式进程协作相关的操作
- `LPOP`|`RPOP`
- `BRPOPLPUSH`