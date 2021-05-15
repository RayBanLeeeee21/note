# ConcurrentHashMap

## 特性

特点:
- key/value不能为null (HashMap可以)
- ConcurrentHashMap18的实际初始化桶表容量比给定桶表容量大(取整为2的幂)

默认属性
- 容量
    ```java
    /** 
        最大容量
        - 最高两位用于控制
    */
    private static final int MAXIMUM_CAPACITY = 1 << 30; 
    private static final int DEFAULT_CAPACITY = 16;      // 默认容量
    static final int MAX_ARRAY_SIZE;                     // 分配数组的最大尺寸(对象头8byte)    
    ```
- 树化/反树化
    ```java
    static final int TREEIFY_THRESHOLD = 8;
    static final int UNTREEIFY_THRESHOLD = 6;
    static final int MIN_TREEIFY_CAPACITY = 64;          // 桶表容量至少到64才能树化链表
    ```
- resize相关 //TODO
    ```java
    private static final int MIN_TRANSFER_STRIDE = 16;
    private static int RESIZE_STAMP_BITS = 16;
    private static final int MAX_RESIZERS = (1 << (32 - RESIZE_STAMP_BITS)) - 1; // 参与迁移的最大线程数(0xffff)
    private static final int RESIZE_STAMP_SHIFT = 32 - RESIZE_STAMP_BITS;
    ```
- 废弃
    ```java
    private static final int DEFAULT_CONCURRENCY_LEVEL = 16; // 并发级别-1.7遗留属性
    private static final float LOAD_FACTOR = 0.75f;          // 构造器参数有loadFactor但不会被使用, 实际通过移位实现
    ```


sizeCtl的多重语义:
- 表不存在: 表示第一次初始化的容量
    - 特殊值0表示取`DEFAULT_CAPACITY=16`
- 创建表/扩容: 线程通过`CAS(sizeCtl, old, -1)`竞争建表机会, 结束后再改成新的扩容阈值
- 存在表: 表示扩容阈值, 其中`Integer.MAX_VALUE`表示无法再扩容


结点hash的多重语义: 结点hash不仅用来表示
- `hash < 0`:
    - `hash = MOVED    (-1)`: 正在做迁移
        - `ForwardingNode`结点的hash值为MOVE, 该结点用于在迁移时在旧表中迁移完成的slot中进行占位, 并且可以告诉访问线程到哪去找`nextTable`
    - `hash = TREEBIN  (-2)`: 红黑树根结点
    - `hash = RESERVED (-3)`: 保留结点
        - 在`compute[IfAbsent]()`方法中, 不一定会插入新结点(随compute结点而定), 因此会通过一个临时结点`ReservationNode`占位
        - 如果插入了新结点, `ReservationNode`会被替换成`Node`或`TreeNode`
- `hash > 0`: 普通链表结点`Node`的头结点. 
    - 只有这种情况下头结点可以承载kev-value, 其余情况下头结点都是无数据的哨兵结点
    - `HASH_BITS = 0x7fffffff`: 前面可知hash<0时有特殊意义

## 实现
### 初始化

构造函数: 一开始不初始化表, 只计算初始的表大小
- 先用初始容量除以因子0.75, 再向上取整为2的幂

```java
    public ConcurrentHashMap(int initialCapacity) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException();
        
        // 计算初始表大小: 除以因子0.75, 再向上取整为2的幂
            // 调用者期望达到 initialCapacity 之前不扩容, 因此要先除以0.75
            // 先不初始化, 只记录
        int cap = ((initialCapacity >= (MAXIMUM_CAPACITY >>> 1)) ?
                    MAXIMUM_CAPACITY :
                    tableSizeFor(initialCapacity + (initialCapacity >>> 1) + 1));
        this.sizeCtl = cap;
    }

    public ConcurrentHashMap(int initialCapacity,
                                float loadFactor, int concurrencyLevel) 
        
        if (!(loadFactor > 0.0f) || initialCapacity < 0 || concurrencyLevel <= 0)
            throw new IllegalArgumentException();
        
        // 给定初始容量不能小于并发级别
        if (initialCapacity < concurrencyLevel)
            initialCapacity = concurrencyLevel;

        // 计算初始表大小: 除以因子0.75, 再向上取整为2的幂
            // 调用者期望达到 initialCapacity 之前不扩容, 因此要先除以0.75
            // 先不初始化, 只记录
        long size = (long)(1.0 + (long)initialCapacity / loadFactor);
        int cap = (size >= (long)MAXIMUM_CAPACITY) ?
            MAXIMUM_CAPACITY : tableSizeFor((int)size);
        this.sizeCtl = cap;
    }
```

初始化表: 对表做双检查 + sizeCtl自旋锁
- 与1.7的区别: 
    - 1.7只有一个竞争对象(segment), 而1.8有两个竞争对象()
    - 1.7为Segment创建表时, 经过双检查的第一次检查后, 可能出现多个线程同时创建, 然后cas竞争设置新表 
    - 1.8中如果有线程正在创建, 其它线程可以通过`sizeCtl==-1`感知到有线程正在创建, 只需一直`yield()`

```java
private final Node<K,V>[] initTable() {
    Node<K,V>[] tab; int sc;
    // 1. 循环尝试
    while ((tab = table) == null || tab.length == 0) {

        // 1.1. sizeCtl == -1 表示其它线程正在创建, 只要yield就可以了
        if ((sc = sizeCtl) < 0)
            Thread.yield(); 

        // 1.2. cas竞争建表机会
        else if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
            try {

                // 1.2.1 双检查, 防止表发生了变化, 确认没有再建表
                if ((tab = table) == null || tab.length == 0) {
                    int n = (sc > 0) ? sc : DEFAULT_CAPACITY;
                    @SuppressWarnings("unchecked")
                    Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n];
                    table = tab = nt;
                    sc = n - (n >>> 2); // loadFactor为0.75
                }
            } finally {
                // 1.2.3 释放锁
                sizeCtl = sc;
            }
            break;
        }
    }
    return tab;
}
```

### put

put()
- 保证线程安全: `put()`中既有自旋乐观锁, 也有synchronize
    - 乐观锁: 通过一个大循环来尝试多个乐观锁, 只要有一个失败就返回原点重试
        - `initTable()`尝试初始化桶表: 参考[初始化](#初始化)小节
        - 初始化头结点: 在确认表创建成功后再使用循环+cas尝试创建头结点, 失败则重新开始循环
    - 悲观锁
        - 前两步确定表和头结点都存在时, 再对头结点上锁, 再进行插入
        - `addCount()`检查扩容时也会上悲观锁

```java
    public V put(K key, V value) {
        return putVal(key, value, false);
    }

    
    final V putVal(K key, V value, boolean onlyIfAbsent) {
        if (key == null || value == null) throw new NullPointerException();

        // 1. 计算hash: 高位扩散到低位中 (h ^ (h >>> 16)) & HASH_BITS(0x7fffffff);
        int hash = spread(key.hashCode());                      
        int binCount = 0;

        // 2. 循环尝试
        for (Node<K,V>[] tab = table;;) {
            Node<K,V> f; int n, i, fh;      // f(irst): 头结点; n: 数组长度; i: 桶id; fh: first.hash

            // 2.1. 检查tab是否存在, 不存在先initTable
            if (tab == null || (n = tab.length) == 0)
                tab = initTable();

            // 2.2. volatile读确认头结点不存在时, cas尝试插入头结点, 成功后返回, 失败则重新循环
            else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
                if (casTabAt(tab, i, null, new Node<K,V>(hash, key, value, null)))
                    break;                   
            }
            // 2.3. 表正在被迁移, 去参与迁移
            else if ((fh = f.hash) == MOVED)
                tab = helpTransfer(tab, f);
            
            else {
                V oldVal = null;
                // 2.4. 搜索结点, 搜索之前要锁表头结点
                synchronized (f) {
                    
                    // 2.4.1. 进入互斥临界区一定要先重检查头结点有没发生变化
                        // 失败则回到循环起点
                    if (tabAt(tab, i) == f) {
                        
                        // 2.4.1.1 hash > 0 表示是链表
                        if (fh >= 0) {
                            binCount = 1;
                            for (Node<K,V> e = f;; ++binCount) {

                                // 2.4.1.1.1 判断是否找到结点
                                K ek;                                   
                                if (e.hash == hash &&                   // 1. hash不相等时一定不相等 (快)
                                    ((ek = e.key) == key ||             // 2. 对象id相等时一定相等 (快)
                                     (ek != null && key.equals(ek)))) { // 3. 最后才调用equals方法 (慢)
                                    oldVal = e.val;
                                    if (!onlyIfAbsent)
                                        e.val = value;
                                    break;
                                }
                                // 2.4.1.1.2 未找到结点则继续往下找, 确认没有就插入尾结点然后返回
                                Node<K,V> pred = e;
                                if ((e = e.next) == null) {
                                    pred.next = new Node<K,V>(hash, key,
                                                              value, null);
                                    break;
                                }
                            }
                        }
                        // 2.4.1.2. 表头结点为红黑树结点(根结点的hash设为-2)
                        // 在红黑树中寻找结点, 找到则更新, 找不到则加入 
                        else if (f instanceof TreeBin) {
                            Node<K,V> p;
                            binCount = 2;
                            
                            if ((p = ((TreeBin<K,V>)f).putTreeVal(hash, key, value)) != null) {
                                oldVal = p.val;
                                if (!onlyIfAbsent)
                                    p.val = value;
                            }
                        }
                    }
                }

                // 2.5 链表长度超过TREEIFY_THRESHOLD, 转成树. 该过程会再次上锁
                if (binCount != 0) {
                    if (binCount >= TREEIFY_THRESHOLD)
                        treeifyBin(tab, i);
                    if (oldVal != null)
                        return oldVal;
                    break;
                }
            }
        }
        
        // 3. 计数, 可能发生扩容
        addCount(1L, binCount);
        return null;
    }

```
### 红黑树

链表转树. 转之前要通过**双检查锁**来竞争头结点, 竞争成功再建表
```java
    /** 双检查锁头结点 */
    private final void treeifyBin(Node<K,V>[] tab, int index) {
        Node<K,V> b; int n, sc;
        
        if (tab != null) {
            // 1. 表容量未达到MIN_TREEIFY_CAPACITY = 64时只做数组扩容
            if ((n = tab.length) < MIN_TREEIFY_CAPACITY)
                tryPresize(n << 1);

            // 2. 确认结点存在, 并且是链表状态
            else if ((b = tabAt(tab, index)) != null && b.hash >= 0) {
                synchronized (b) {
                    // 2.1 双检查头结点. 
                        // 如果发生过变化, 则说明有线程抢先一步, 因此可以直接退出
                    if (tabAt(tab, index) == b) {

                        TreeNode<K,V> hd = null, tl = null;
                        // 2.1.1 复制出一条TreeNode链表(TreeNode也有next指针)
                        for (Node<K,V> e = b; e != null; e = e.next) {
                            TreeNode<K,V> p = new TreeNode<K,V>(e.hash, e.key, e.val, null, null);
                            if ((p.prev = tl) == null) hd = p;
                            else tl.next = p;
                            tl = p;
                        }
                        // 2.1.2 把TreeNode链表转成树后放到位置上
                        setTabAt(tab, index, new TreeBin<K,V>(hd));
                    }
                }
            }
        }
    }

    /**
        TreeBin继承自Node(这样才能放到table中)
        hash设为TREEBIN(-1)表示是TreeBin
        其过程与putTreeVal()相似, 区别在于不用检查key是否存在(全都不存在)
    */
    TreeBin(TreeNode<K,V> b) {
        // 1. hash 设为 TREEBIN = -2
        super(TREEBIN, null, null, null);   

        this.first = b;
        TreeNode<K,V> r = null;
        // 2. 循环把结点加入到红黑树中, 并进行平衡操作
        for (TreeNode<K,V> x = b, next; x != null; x = next) {
            next = (TreeNode<K,V>)x.next;
            x.left = x.right = null;

            // 2.1 如果树为空, 只设置第一个结点
            if (r == null) {
                x.parent = null;
                x.red = false;
                r = x;
            }
            // 2.2 循环寻找插入点
            else {
                K k = x.key;
                int h = x.hash;
                Class<?> kc = null;

                for (TreeNode<K,V> p = r;;) {
                    int dir, ph;
                    K pk = p.key;
                    // 2.2.1 确定方向
                    if ((ph = p.hash) > h)
                        dir = -1;
                    else if (ph < h)
                        dir = 1;
                    // 2.2.2 hash相等, 无法分出上下时, 尝试用Comparable的方法比较key
                    // 如果无法用Comparable的方法比较key或者比较key的结果依然相等
                    // 再用System.identityHashCode(key)的结果来比较
                    else if ((kc == null &&
                                (kc = comparableClassFor(k)) == null) ||
                                (dir = compareComparables(kc, k, pk)) == 0)    
                        dir = tieBreakOrder(k, pk);
                        TreeNode<K,V> xp = p;

                    // 2.2.2 向下
                    if ((p = (dir <= 0) ? p.left : p.right) == null) {
                        x.parent = xp;
                        if (dir <= 0)
                            xp.left = x;
                        else
                            xp.right = x;
                        // 加入结点后, 进行平衡, 并得到新的root
                        r = balanceInsertion(r, x);
                        break;
                    }
                }
            }
        }
        // 3. 保存root结点
        this.root = r;
        assert checkInvariants(root);
    }
    

    final TreeNode<K,V> putTreeVal(int h, K k, V v) {
        Class<?> kc = null;
        boolean searched = false;
        // 1. 从root开始搜索
        for (TreeNode<K,V> p = root;;) {
            int dir, ph; K pk;
            // 1.1. root为null时直接加入
            if (p == null) {
                first = root = new TreeNode<K,V>(h, k, v, null, null);
                break;
            }
            // 1.2. 用hash确定搜索方向
            else if ((ph = p.hash) > h)
                dir = -1;
            else if (ph < h)
                dir = 1;
            // 1.3. hash相等 && key相等, 则返回
            else if ((pk = p.key) == k || (pk != null && k.equals(pk)))
                return p;
            // 1.4. hash相等 && key不相等, 尝试利用Comparable的方法来比较key
            else if ((kc == null &&
                        (kc = comparableClassFor(k)) == null) ||        
                        (dir = compareComparables(kc, k, pk)) == 0) {
            
                // 1.4.1 hash相等 && (compare(key1, key2)相等) && key不相等 
                // 直接搜索结点, 看能不能找到对应的key(只搜索一次)
                if (!searched) {
                    TreeNode<K,V> q, ch;
                    searched = true;
                    if (((ch = p.left) != null &&
                            (q = ch.findTreeNode(h, k, kc)) != null) ||
                        ((ch = p.right) != null &&
                            (q = ch.findTreeNode(h, k, kc)) != null))
                        return q;
                }

                // 1.4.2 hash相等 && (compare(key1, key2)相等) && key不相等, 又没搜索到结点
                // 只能通过 System.identityHashCode() 来确定
                dir = tieBreakOrder(k, pk);
            }

            // 1.5 方向确定, 向子结点走, 插入以后再重新平衡
            TreeNode<K,V> xp = p;
            if ((p = (dir <= 0) ? p.left : p.right) == null) {
                TreeNode<K,V> x, f = first;
                first = x = new TreeNode<K,V>(h, k, v, f, xp);
                if (f != null)
                    f.prev = x;
                if (dir <= 0)
                    xp.left = x;
                else
                    xp.right = x;
                if (!xp.red)
                    x.red = true;
                else {
                    lockRoot();
                    try {
                        // 1.5.1 平衡并更新root结点 
                        root = balanceInsertion(root, x);
                    } finally {
                        unlockRoot();
                    }
                }
                break;
            }
        }
        assert checkInvariants(root);
        return null;
    }
```

红黑树建立过程:
![红黑树](../java.util(collection)/RBTree.jpg)

### 结点计数器 & 扩容

结点的计数器也是线程安全的, 其机制类似于[LongAdder](./atomic/LongAdder.md)
- `baseCount`: 基础计数器, 只有没发生过线程冲突就一直在`baseCount`上计数
- `counterCells`: 计数器数组. 
    - 通过线程探针值(一种hash, 参考[ThreadLocalRandom](./ThreadLocalRandom.md))取余来决定线程要将新值加到哪个cell, 以此避免多个线程竞争同一个cell
    - `CounterCell`类加了`@Contended`注解, 通过缓存行填充来避免缓存行**伪共享**问题
        ```java
        @sun.misc.Contended static final class CounterCell {
            volatile long value;
            CounterCell(long x) { value = x; }
        }
        ```

计数器部分原理与[LongAdder](./atomic/LongAdder.md)几乎一致. `fullAddCount()`部分的解读可以直接跳过, 直接参考LongAdder.

结点计数与扩容实现: 
```java
        /**
     * Adds to count, and if table is too small and not already
     * resizing, initiates transfer. If already resizing, helps
     * perform transfer if work is available.  Rechecks occupancy
     * after a transfer to see if another resize is already needed
     * because resizings are lagging additions.
     * 增加计数, 如果表太小, 则要初始化transfer(迁移)
     * 如果已经开始resize, 帮助transfer
     * 在转移后重新检查占用空间，看看是否需要重新调整大小
     * 因为resize是滞后的。
     *
     * @param x the count to add
     * @param check: 
     *    check >=2 (即链表长度>=2 或者桶中为红黑树), 则要检查是否需要扩容
     */
    //
    private final void addCount(long x, int check) {
        CounterCell[] as; long b, s;            // CounterCell只有一个用来保存count的域(volatile long)

        // 1. couterCells未初始化说明未发生过竞争, 直接cas尝试加到baseCount上, 成功就到2去检查扩容
        if ((as = counterCells) != null ||
            !U.compareAndSwapLong(this, BASECOUNT, b = baseCount, s = b + x)) { 
            CounterCell a; long v; int m;
            boolean uncontended = true;

            // 1.1 尝试给线程私有counter计数
            if (as == null || (m = as.length - 1) < 0 ||                        // 1. counterCells未初始化 -> 在fullAddCount()中初始化
                (a = as[ThreadLocalRandom.getProbe() & m]) == null ||           // 2. 线程对应的counter未初始化 -> 在fullAddCount()中初始化
                !(uncontended =                                                 // 3. counter冲突 -> 自旋尝试增加计数值
                  U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))) {    

                // 1.1.1 线程私有计数器计数失败, 则fullAddCount
                    // fullCount有三个作用: 创建counterCells, 创建counter, 自旋尝试增加计数值
                fullAddCount(x, uncontended);                                   
                return;
            }
            // 1.2. 不检查resize()则直接返回
            if (check <= 1) return;

            // 1.3. 如果要检查resize(), 则要拿到结点个数(size())
            s = sumCount(); // 计算所有count的和
        }

        // 2. 如果需要检查
        if (check >= 0) {
            Node<K,V>[] tab, nt; int n, sc;
            // 2.1 超出阈值, 要进行迁移
            while (s >= (long)(sc = sizeCtl) && (tab = table) != null &&
                   (n = tab.length) < MAXIMUM_CAPACITY) {

                // rs = Integer.numberOfLeadingZeros(n) | (1 << (RESIZE_STAMP_BITS - 1))
                    // RESIZE_STAMP_BITS = 16
                    // 假设tab.length 前面 有10个零, 则resizeStamp计算结果为 0x0000800A
                    // resizeStamp对应迁移时sizeCtrl的高16位
                int rs = resizeStamp(n);                                            
                if (sc < 0) {
                    if ((sc >>> RESIZE_STAMP_SHIFT) != rs   // 未进行迁移或者正在进行其它轮的迁移
                        || sc == rs + 1                      
                        || sc == rs + MAX_RESIZERS 
                        || (nt = nextTable) == null         // nextTable被撤, 迁移已完成
                        || transferIndex <= 0)              // 所有stripe已被处理完
                        break;
                    // 未迁移完成时, 尝试参与迁移
                    if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1))
                        transfer(tab, nt);
                }

                // 自己尝试发起迁移, 把sizeCtrl改成小于0的值
                //     高2-16位用来表示迁移的轮次 
                //     低16位用来记录参与迁移的线程数
                //     争夺成功则sizeCtl改成0x800A0000
                else if (U.compareAndSwapInt(this, SIZECTL, sc,
                                             (rs << RESIZE_STAMP_SHIFT) + 2))
                    transfer(tab, null);
                s = sumCount();
            }
        }
    }

    // 该过程与Striped64.longAccumulate()方法几乎一致
    private final void fullAddCount(long x, boolean wasUncontended) {

        // 为线程初始化探针值, 初始化完就不会再改
        int h;
        if ((h = ThreadLocalRandom.getProbe()) == 0) {
            ThreadLocalRandom.localInit();      // force initialization
            h = ThreadLocalRandom.getProbe();
            wasUncontended = true;
        }

        // 通过一个for循环来承载多个乐观锁
        boolean collide = false;                // True if last slot nonempty
        for (;;) {
            CounterCell[] as; CounterCell a; int n; long v;

            // 1. 表已存在
            if ((as = counterCells) != null && (n = as.length) > 0) {

                // 1.1 线程对应的counter未初始化, 尝试初始化counter
                if ((a = as[(n - 1) & h]) == null) {

                    // 双检查乐观锁 - 抢到cellsBusy的线程可以创建counterCells数组或单个counter
                    if (cellsBusy == 0) {            // Try to attach new Cell
                        CounterCell r = new CounterCell(x); // Optimistic create

                        // 第二次检查并cas竞争cellsBusy
                        if (cellsBusy == 0 && U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
                            boolean created = false;
                            try {               // Recheck under lock

                                // 抢到锁还得重新检查counterCells数组和单个counter
                                CounterCell[] rs; int m, j;
                                if ((rs = counterCells) != null &&
                                    (m = rs.length) > 0 &&
                                    rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    created = true;
                                }
                            } finally {
                                // 释放乐观锁
                                cellsBusy = 0;
                            }
                            // 成功创建了cell就退出循环.
                            if (created) break;

                            // 否则失败(重检查时可能发现cell已经有了)就要回到循环的起点开始
                            continue;
                        }
                    }
                    collide = false;
                }
                // 知道自己跟其它线程竞争counter了, 就放弃cas, 直接跑到下面去advanceProbe(), 期望换到一个空的slot上创建counter
                else if (!wasUncontended) 
                    wasUncontended = true; // 只能放弃一次cas, 第一次就会把标志清掉, 使下次强制CAS竞争

                // cas竞争counter, 成功后离开
                else if (U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))
                    break;
                // 如果有线程正在扩容, 或者已经到扩容上限了, 就只能每次都清理collide标志
                    // 即是说每次失败都会advanceProbe(), 然后再尝试CAS (打一枪换一个地方)
                else if (counterCells != as || n >= NCPU)
                    collide = false;            // At max size or stale

                // CAS失败了, 跑到下面advanceProbe(), 期望换个counter可以成功
                else if (!collide)
                    collide = true;  // 清除标志, 下次再失败就尝试扩容counterCells
                
                // 期望通过扩容counterCells来减少冲突
                else if (cellsBusy == 0 &&
                         U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
                    try {

                        // 重新确认 counterCells 未变化
                        if (counterCells == as) {// Expand table unless stale
                            CounterCell[] rs = new CounterCell[n << 1];
                            for (int i = 0; i < n; ++i)     // 迁移结点
                                rs[i] = as[i];
                            counterCells = rs;
                        }
                    } finally {

                        // 释放锁
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                h = ThreadLocalRandom.advanceProbe(h);
            }

            // 2. counterCells不存在 - 双检查乐观锁竞争cellsBusy, 成功后创建counterCells及线程的counter
            else if (cellsBusy == 0 && counterCells == as &&
                     U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
                boolean init = false;
                try {                           
                    // 重新确认counterCells未被修改
                    if (counterCells == as) {
                        CounterCell[] rs = new CounterCell[2];
                        rs[h & 1] = new CounterCell(x);
                        counterCells = rs;
                        init = true;
                    }
                } finally {

                    // 释放锁
                    cellsBusy = 0;
                }

                // 成功后退出循环
                if (init) break;

                // 否则回到循环起点重新开始尝试
            }
            // 3. 未抢到创建counterCells的机会 - 只能将增量加到baseCount上(避免等待数组的创建)
            else if (U.compareAndSwapLong(this, BASECOUNT, v = baseCount, v + x))
                break;                          // Fall back on using base
        }
    }
    
```



```java

    /**
     * Helps transfer if a resize is in progress.
     */
    final Node<K,V>[] helpTransfer(Node<K,V>[] tab, Node<K,V> f) {
        Node<K,V>[] nextTab; int sc;
        // 保守检查:
            // 1. 表不为空
            // 2.首结点为ForwardingNode结点表示在做迁移 
            // 3. nextTable不为空表示在做迁移
        if (tab != null 
            && (f instanceof ForwardingNode) 
            && (nextTab = ((ForwardingNode<K,V>)f).nextTable) != null) {

            int rs = resizeStamp(tab.length);

            while (nextTab == nextTable && table == tab &&
                   (sc = sizeCtl) < 0) {
                // 如果没有迁移结束就要参与迁移
                if ((sc >>> RESIZE_STAMP_SHIFT) != rs   // 迁移结束
                    || sc == rs + 1                     // ???
                    || sc == rs + MAX_RESIZERS          // ???
                    || transferIndex <= 0)              // stripe已被分配完
                    break;
                // 尝试参与迁移
                if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1)) {
                    transfer(tab, nextTab);
                    break;
                }
            }
            return nextTab;
        }
        return table;
    }

    /**
     * Tries to presize table to accommodate the given number of elements.
     *
     * @param size number of elements (doesn't need to be perfectly accurate)
     */
    private final void tryPresize(int size) {
        int c = (size >= (MAXIMUM_CAPACITY >>> 1)) ? MAXIMUM_CAPACITY :
            tableSizeFor(size + (size >>> 1) + 1);
        int sc;
        while ((sc = sizeCtl) >= 0) {
            Node<K,V>[] tab = table; int n;
            // 分支1: 桶数组未初始化的话, 要先初始化, 然后重新开始循环
            if (tab == null || (n = tab.length) == 0) {
                n = (sc > c) ? sc : c;
                // 争夺sizeCtl, 取得sizeCtl时可以初始化桶数组
                if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
                    try {
                        if (table == tab) {
                            @SuppressWarnings("unchecked")
                            Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n];
                            table = nt;
                            sc = n - (n >>> 2);
                        }
                    } finally {

                        // 释放锁
                        sizeCtl = sc;
                    }
                }
            }
            // 分支2: 达到最大容量, 不能再扩容, 退出
            else if (c <= sc || n >= MAXIMUM_CAPACITY)
                break;

            // 分支3: 尝试参与或发起迁移
            else if (tab == table) {
                int rs = resizeStamp(n);
                // 检查需不需要参与, 未完成迁移则要参与迁移
                if (sc < 0) {
                    Node<K,V>[] nt;
                    if ((sc >>> RESIZE_STAMP_SHIFT) != rs // 1. 未进行迁移或者在进行其它epoch的迁移
                        || sc == rs + 1                   // ???
                        || sc == rs + MAX_RESIZERS        // ???
                        || (nt = nextTable) == null       // 2. nextTable被撤, 迁移完成
                        || transferIndex <= 0)            // 3. stripe已被分配完
                        break;

                    // resizer计数 + 1, 参与到迁移中
                    if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1)) transfer(tab, nt);
                }
                
                // 尝试把resizeStamp标志写到sizeCtrl的高16位, 第一个发起迁移
                    // 失败则重新循环, 可能会参与迁移
                else if (U.compareAndSwapInt(this, SIZECTL, sc, (rs << RESIZE_STAMP_SHIFT) + 2))
                    transfer(tab, null);
            }
        }
    }

    /**
     * Moves and/or copies the nodes in each bin to new table. See
     * above for explanation.
     */
    private final void transfer(Node<K,V>[] tab, Node<K,V>[] nextTab) {
        int n = tab.length, stride;
        if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE)
            stride = MIN_TRANSFER_STRIDE; // subdivide range

        // 检查nextTable是否初始化过
        if (nextTab == null) {            
            try {
                @SuppressWarnings("unchecked")
                Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n << 1];
                nextTab = nt;
            } catch (Throwable ex) {      // 发生OutOfMemoryError, 把sizeCtrl改到最大, 表示无法再扩容
                sizeCtl = Integer.MAX_VALUE;
                return;
            }
            nextTable = nextTab;
            transferIndex = n;                                         // 设置第一个stripe的起点
        }


        int nextn = nextTab.length;
        ForwardingNode<K,V> fwd = new ForwardingNode<K,V>(nextTab);    //[1] 
        boolean advance = true;                 // 只要没申请到strip, advance都为true
        boolean finishing = false;              // 
        for (int i = 0, bound = 0;;) {
            Node<K,V> f; int fh;

            // 每个线程处理之前先申请stripe, 处理完一stripe再尝试申请一个stripe
                // 处理的时候从后往前处理
            while (advance) {
                int nextIndex, nextBound;

                // 手头上已经有一个strip没处理完, 不申请, 只是推进i(向前推)
                if (--i >= bound || finishing) advance = false;

                // transferIndex <= 0表示所有桶都被处理完了, 停止申请strip
                else if ((nextIndex = transferIndex) <= 0) {
                    i = -1;
                    advance = false;
                }
                // cas尝试申请一个stripe, 失败则回到while循环起点重新申请
                else if (U.compareAndSwapInt
                         (this, TRANSFERINDEX, nextIndex,
                          nextBound = (nextIndex > stride ?
                                       nextIndex - stride : 0))) {
                    bound = nextBound;
                    i = nextIndex - 1;
                    advance = false;
                }
            }
            
            // i 已经超出范围, 开始收尾
            if (i < 0 || i >= n || i + n >= nextn) {
                int sc;
                // 迁移完成, 把新表赋给table    
                if (finishing) {
                    nextTable = null;
                    table = nextTab;
                    sizeCtl = (n << 1) - (n >>> 1);     // 更新阈值
                    return;
                }

                // resizer计数 -1, 失败则回到循环起点重试
                if (U.compareAndSwapInt(this, SIZECTL, sc = sizeCtl, sc - 1)) {

                    // 如果自己是不最后一个完成的, 那直接返回
                    if ((sc - 2) != resizeStamp(n) << RESIZE_STAMP_SHIFT) return;
                    
                    // 最后一个完成的需要负责用新表替换旧表
                        // 但这里不替换新表, 要重检查, 下个迭代再更换
                    finishing = advance = true;                                 
                    i = n; // recheck before commit
                }
            }
            // 当前桶本来就是空的, 那设置成已迁移
            else if ((f = tabAt(tab, i)) == null)
                advance = casTabAt(tab, i, null, fwd);
            // 当前桶已经被成功迁移了, 向下一个走
            else if ((fh = f.hash) == MOVED)
                advance = true; 
            // 桶未被迁移过, 上锁做迁移
            else {
                // 头结点上锁
                synchronized (f) {

                    // 重检查确认头结点未发生变化 
                    if (tabAt(tab, i) == f) {
                        Node<K,V> ln, hn;

                        // 如果是链表头, 以1.7的方法复制链表, 并拆分, 最后赋给新桶数组
                        if (fh >= 0) {
                            int runBit = fh & n;
                            Node<K,V> lastRun = f;
                            for (Node<K,V> p = f.next; p != null; p = p.next) {
                                int b = p.hash & n;
                                if (b != runBit) {
                                    runBit = b;
                                    lastRun = p;
                                }
                            }
                            if (runBit == 0) {
                                ln = lastRun;
                                hn = null;
                            }
                            else {
                                hn = lastRun;
                                ln = null;
                            }
                            for (Node<K,V> p = f; p != lastRun; p = p.next) {
                                int ph = p.hash; K pk = p.key; V pv = p.val;
                                if ((ph & n) == 0)
                                    ln = new Node<K,V>(ph, pk, pv, ln);
                                else
                                    hn = new Node<K,V>(ph, pk, pv, hn);
                            }
                            setTabAt(nextTab, i, ln);
                            setTabAt(nextTab, i + n, hn);
                            setTabAt(tab, i, fwd);                                // 把fwd结点放在旧表的对应位置
                            advance = true;
                        }
                        // 如果是红黑树, 则复制链表(结点为TreeNode), 并拆分
                        else if (f instanceof TreeBin) {
                            TreeBin<K,V> t = (TreeBin<K,V>)f;
                            TreeNode<K,V> lo = null, loTail = null;
                            TreeNode<K,V> hi = null, hiTail = null;
                            int lc = 0, hc = 0;
                            for (Node<K,V> e = t.first; e != null; e = e.next) {
                                int h = e.hash;
                                TreeNode<K,V> p = new TreeNode<K,V>
                                    (h, e.key, e.val, null, null);
                                if ((h & n) == 0) {
                                    if ((p.prev = loTail) == null)
                                        lo = p;
                                    else
                                        loTail.next = p;
                                    loTail = p;
                                    ++lc;
                                }
                                else {
                                    if ((p.prev = hiTail) == null)
                                        hi = p;
                                    else
                                        hiTail.next = p;
                                    hiTail = p;
                                    ++hc;
                                }
                            }
                            // 复制好两个链表后, 如果小于UNTREEIFY_THRESHOLD, 那就untreeify, 否则重建红黑树
                            ln = (lc <= UNTREEIFY_THRESHOLD) ? untreeify(lo) :
                                (hc != 0) ? new TreeBin<K,V>(lo) : t;
                            hn = (hc <= UNTREEIFY_THRESHOLD) ? untreeify(hi) :
                                (lc != 0) ? new TreeBin<K,V>(hi) : t;
                            // 加入新表
                            setTabAt(nextTab, i, ln);
                            setTabAt(nextTab, i + n, hn);
                            setTabAt(tab, i, fwd);      // 把forward结点放在旧表的对应位置, 表示在做迁移
                            advance = true;
                        }
                    }
                }
            }
        }
    }
    //[1] 该结点用来通知访问结点的线程, 该桶已经成功迁移到nextTable
```