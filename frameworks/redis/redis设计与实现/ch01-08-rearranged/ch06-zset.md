

## 1. 对象编码

zset:
- skiplist
- ziplist: `size(element) <= 64 && count(element) <= 128`

### 1.1 skiplist

数据结构
- 跳跃表
    ```c++
    typedef struct zskiplist {
        struct zskiplistNode *header, *tail;    // 头尾指针
        unsigned long length;                   // 结点个数
        int level;                              // 结点最大level
    } zskiplist;
    ```
- 跳跃表点
    ```c++
    typedef struct zskiplistNode {
        
        // 层
        struct zskiplistLevel {
            struct zskiplistNode *forward;      
            unsigned long span;                 // 该指针会向前跳多少个
        } level[];                              // 类似链表的next, 但是会有很多个, 以不同的尺度步进

        // 后退指针, 后退一步
        struct zskiplistNode *backward;         

        // 分值
        double score; 

        // 携带的对象
        robj *obj;
        
    } zskiplistNode;
    ```

特点:
- 越高的层跨度越大
    - 头结点不保存数据, 也没有backward和分值, 只有层
- 查找方法:
    - 对于每个结点, 都要从上往下遍历层, 看哪一层可以最快跨到目标结点

应用场景
- zset类型的编码之一
- 集群结点中的内部结构

#### 1.1.1 skiplist原理解析

-  `zslInsert()`: 
    -  过程:
        1. 先从头结点开始, 利用score做比较, 找到所有前续结点
        2. 随机选择一个level作为新结点的level, 创建新结点
        3. 插入新结点
            1. 低于等于新结点level的重设forward和span, 新结点的也更新
            2. 高于新结点level的前续结点span加1
            3. 高于最大level的前续结点是头结点, 不处理
            4. 设置backward指针
    -  细节
        ```cpp
        zskiplistNode *zslInsert(zskiplist *zsl, double score, sds ele) {
            zskiplistNode *update[ZSKIPLIST_MAXLEVEL], *x;
            unsigned int rank[ZSKIPLIST_MAXLEVEL];
            int i, level;

            serverAssert(!isnan(score));

            // 找到头结点, 从最高层往下开始把每个前续结点都找到
            x = zsl->header;
            for (i = zsl->level-1; i >= 0; i--) {

                // rank是每层前续结点的坐标(排名)
                //  如果是最高层, 则从0开始找
                //  如果不是最高层, 则可以从上一层的结点开始(跳过前面搜索过的结点)
                rank[i] = i == (zsl->level-1) ? 0 : rank[i+1];

                // 找到小于要插入结点但最接近的结点
                while (x->level[i].forward &&
                        (x->level[i].forward->score < score ||
                            (x->level[i].forward->score == score &&
                            sdscmp(x->level[i].forward->ele,ele) < 0)))
                {
                    rank[i] += x->level[i].span;
                    x = x->level[i].forward;
                }

                // 记录下来
                update[i] = x;
            }
            /* we assume the element is not already inside, since we allow duplicated
            * scores, reinserting the same element should never happen since the
            * caller of zslInsert() should test in the hash table if the element is
            * already inside or not. */
            // 给新结点随机选一个层数(第n层的概率为2^n)
            level = zslRandomLevel();
            if (level > zsl->level) {

                // 高出最大level的层可以先设置步长
                for (i = zsl->level; i < level; i++) {
                    rank[i] = 0;
                    update[i] = zsl->header;
                    update[i]->level[i].span = zsl->length;
                }
                zsl->level = level;
            }

            // 以level创建新结点
            x = zslCreateNode(level,score,ele);
            for (i = 0; i < level; i++) {
                x->level[i].forward = update[i]->level[i].forward;
                update[i]->level[i].forward = x;

                // 设置第i层的next指针和步长
                x->level[i].span = update[i]->level[i].span - (rank[0] - rank[i]);

                // 设置前续结点的指针和步长
                update[i]->level[i].span = (rank[0] - rank[i]) + 1;
            }

            // 比level高的前续结点步长要加1(多加了这个新结点)
            for (i = level; i < zsl->level; i++) {
                update[i]->level[i].span++;
            }

            // 设置新结点与新结点之后的backward指针, 以及tail指针
            x->backward = (update[0] == zsl->header) ? NULL : update[0];
            if (x->level[0].forward)
                x->level[0].forward->backward = x;
            else
                zsl->tail = x;
            zsl->length++;
            return x;
        }
        ```
- `zslGetRank()`:
    - 过程:
        - 从头结点的最高层开始, 判断下一步是否超出rank, 不超出时下一步, 否则进入下一层, 直到找到结点
    - 细节
        ```cpp
        /* Finds an element by its rank. The rank argument needs to be 1-based. */
        zskiplistNode* zslGetElementByRank(zskiplist *zsl, unsigned long rank) {
            zskiplistNode *x;
            unsigned long traversed = 0;
            int i;

            // 从高层往低层找, 每次都跳尽可能多的步长
            x = zsl->header;
            for (i = zsl->level-1; i >= 0; i--) {

                while (x->level[i].forward && (traversed + x->level[i].span) <= rank)
                {
                    traversed += x->level[i].span;
                    x = x->level[i].forward;
                }
                if (traversed == rank) {
                    return x;
                }
            }
            return NULL;
        }
        ```
- `zslDelete()`:
    - 过程:
        1. 从头结点开始找到所有要更新指针的结点
        2. 如果没匹配到结点, 则返回
        3. 删除结点
            1. 低于被删除结点的forward和span要更新
            2. 低于最大level的结点只要span - 1
            3. 高于最大level的结点不处理
        4. 更新被删结点后续结点的backward指针
        5. 从高层往下更新level(根据有没有forward来判断)
    - 细节
        ```cpp

        /* 
        * @param node: 如果为null, 则被删结点会被释放, 否则会被存到node 
        * @return 0: 未找到结点; 1: 找到并删除
        * */
        int zslDelete(zskiplist *zsl, double score, sds ele, zskiplistNode **node) {
            zskiplistNode *update[ZSKIPLIST_MAXLEVEL], *x;
            int i;

            // 从高层到低层找到所有层的前续结点(小于且最接近)
            x = zsl->header;
            for (i = zsl->level-1; i >= 0; i--) {
                while (x->level[i].forward &&
                        (x->level[i].forward->score < score ||
                            (x->level[i].forward->score == score &&
                            sdscmp(x->level[i].forward->ele,ele) < 0)))
                {
                    x = x->level[i].forward;
                }
                update[i] = x;
            }
            
            // 
            x = x->level[0].forward;
            if (x && score == x->score && sdscmp(x->ele,ele) == 0) {

                // 匹配后删除
                zslDeleteNode(zsl, x, update);

                // node不为null则存到node返回
                if (!node)
                    zslFreeNode(x);
                else
                    *node = x; 
                return 1;
            }
            return 0; /* not found */
        }

        /* Internal function used by zslDelete, zslDeleteRangeByScore and
        * zslDeleteRangeByRank. */
        void zslDeleteNode(zskiplist *zsl, zskiplistNode *x, zskiplistNode **update) {
            // 
            int i;
            for (i = 0; i < zsl->level; i++) {
                if (update[i]->level[i].forward == x) {

                    // 重新前续结点的步长, 设置forward
                    update[i]->level[i].span += x->level[i].span - 1;
                    update[i]->level[i].forward = x->level[i].forward;
                } else {
                    // 超出x高度的结点不会指向x, 直接步长减
                    update[i]->level[i].span -= 1;
                }
            }

            // 设置后续结点的forward
            if (x->level[0].forward) {
                x->level[0].forward->backward = x->backward;
            } else {
                // 设置tail
                zsl->tail = x->backward;
            }

            // 如果x是最高level的结点, 就要重新算level
            while(zsl->level > 1 && zsl->header->level[zsl->level-1].forward == NULL)
                zsl->level--;
            zsl->length--;
        }
        ```
- `zslUpdateScore`:
    - 过程:
        1. 从头结点的最高层开始往下找, 找到每一层的前续结点
        2. 如果找到的结点, 修改score后位置不变(newScore仍在前续后续结点的score范围之间), 则更新完直接返回
        3. 把旧结点从原来的位置删掉, 重新插入
    - 细节
        ```cpp
        zskiplistNode *zslUpdateScore(zskiplist *zsl, double curscore, sds ele, double newscore) {
            zskiplistNode *update[ZSKIPLIST_MAXLEVEL], *x;
            int i;

            // 从高层到低层找到所有层的前续结点(小于且最接近)
            x = zsl->header;
            for (i = zsl->level-1; i >= 0; i--) {
                while (x->level[i].forward &&
                        (x->level[i].forward->score < curscore ||
                            (x->level[i].forward->score == curscore &&
                            sdscmp(x->level[i].forward->ele,ele) < 0)))
                {
                    x = x->level[i].forward;
                }
                update[i] = x;
            }

            // 确定score和ele是匹配的
            x = x->level[0].forward;
            serverAssert(x && curscore == x->score && sdscmp(x->ele,ele) == 0);

            // 如果更新score后不改变位置, 则直接更新后返回
            if ((x->backward == NULL || x->backward->score < newscore) &&
                (x->level[0].forward == NULL || x->level[0].forward->score > newscore))
            {
                x->score = newscore;
                return x;
            }

            // 从原来的位置删除, 然后重新插入
            zslDeleteNode(zsl, x, update);
            zskiplistNode *newnode = zslInsert(zsl,newscore,x->ele);
            /* We reused the old node x->ele SDS string, free the node now
            * since zslInsert created a new one. */
            x->ele = NULL;
            zslFreeNode(x);
            return newnode;
        }
        ```
### 1.2 ziplist

参考[ziplist](./ch03-list.md#12-ziplist)


## 2. 所有操作

[所有操作](http://redisdoc.com/sorted_set/index.html)(**都是原子操作**)

增
- `ZADD [NX|XX] key score member [score member ...]`: 
    - **返回**: 有序集合长度

查
- `ZSCORE key member`:
    - **返回**: 某个元素的分值
- `ZCARD key`: 
    - **返回**: zset的元素个数

- `ZRANGE key min max [BYSCORE|BYLEX] [REV] [LIMIT offset count] [WITHSCORES]`
    - 无`BYSCORE`或`BYLEX`: 返回rank范围为`[min, max]`的member列表
    - `BYSCORE`: 返回score范围为`[min, max]`的member列表
    - `BYLEX`: 返回member为指定范围的列表. 
        - 匹配规则为c语言的`memcmp()`的匹配规则, 逐字符比较
        - "["和"("分别表示闭区间和开区间, "-"和"+"表示负无穷和正无穷.
        - **BYLEX只能对score相同的member使用, 否则结果是未定义的**
        ```
        ZADD zlist 1.0 10 2.0 20 3.0 30 4.0 40

        ZRANGE zlist -   [40     =>     1) "10"  2) "20"  3) "30"  4) "40"
        ZRANGE zlist (10 +       =>     1) "20"  2) "30"  3) "40"
        ZRANGE zlist [10 [40     =>     1) "10"  2) "20"  3) "30"  4) "40"
        ZRANGE zlist (10 [40     =>     1) "20"  2) "30"  3) "40"
        ZRANGE zlist [10 (40     =>     1) "10"  2) "20"  3) "30"
        ZRANGE zlist (10 (40     =>     1) "20"  2) "30"
        ```
    - `REV`: 倒序. 指定范围的时候, 也要写成max在前, min在后
    - **返回**:
        - 无`WITHSCORES`: member列表
        - `WITHSCORES`: 由member和score组成的列表: `[m1, s1, m2, s2...]`
- `[ZRANK|ZREVRANK] key member`: 
    - **返回**: 某个member的rank|反向rank(即length - rank)
- `ZCOUNT key min max`:
    - **返回**: score范围为`[min, max]`的元素个数
- `ZLEXCOUNT key min max`:
    - **返回**: 返回member为指定范围的元素个数

删
- `ZREM key element [element ...]`: 删除一到多个元素
    - **返回**: 被删的member个数
- `ZREMRANGEBYSCORE key min max`: 根据score的范围删除
    - **返回**: 被删除的元素个数
- `ZREMRANGEBYRANK key min max`: 根据rank的范围删除
    - **返回**: 被删除的元素个数
- `ZREMRANGEBYLEX key min max`: 根据member的范围删除
    - **返回**: 被删除的元素个数
- `ZUNIONSTORE`
- `ZINTERSTORE`
// TODO