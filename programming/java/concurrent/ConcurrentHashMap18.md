

ConcurrentHashMap
* 特性:
    * key/value不能为null (HashMap可以)
    * ConcurrentHashMap18的实际初始化桶表容量比给定桶表容量大
* Node
    * 域
        * final int hash
        * final K key
        * volatile V value
        * volatile HashEntry<K,V> next
* 初始化默认属性:
    * 表容量: 16
    * loadFactor: 0.75
        * ConcurrentHashMap中虽然提供了loadFactor参数, 但实际loadFactor一直为0.75
* 默认属性:
    * DEFAULT_CAPACITY = 16
* 相关属性计算
* sizeCtl的含义:
    * 在表分配之前, 用来保存第一次分配表的大小
    * 分配表时, 用来作为分配表的信号量
    * 在表分配以后, 用来保存阈值
    * sizeCtl == 0: 表示取默认大小DEFAULT_CAPACITY=16
    * sizeCtl == -1: 有线程正在创建表
    * sizeCtl > 0: 表示将要分配的表大小(分配表前); 表示表的阈值(表分配以后)
    ```java
    /**
        初次分配表
    */
    private final Node<K,V>[] initTable() {
        Node<K,V>[] tab; int sc;
        // 1. 循环尝试, 直到自己创建成功或者其它线程创建成功
        while ((tab = table) == null || tab.length == 0) {
            // 1.1. sizeCtl == -1 表示其它线程正在创建, 只要yield就可以了
            if ((sc = sizeCtl) < 0)
                Thread.yield(); 

            // 1.2. cas 抢 sizeCtl, 抢到设为-1; 抢到sizeCtl以后要再检查一次有没初始化
            else if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
                try {
                    if ((tab = table) == null || tab.length == 0) {

                        // 1.2.1. 根据原sizeCtl来确定容量大小
                        int n = (sc > 0) ? sc : DEFAULT_CAPACITY;
                        @SuppressWarnings("unchecked")
                        Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n];
                        table = tab = nt;
                        sc = n - (n >>> 2); // loadFactor为0.75
                    }
                } finally {
                    // 1.2.2. 表初始化完成, sizeCtl用来保存新的阈值
                    sizeCtl = sc;
                }
                break;
            }
        }
        return tab;
    }
    ```
* 构造函数
    ```java
    public ConcurrentHashMap(int initialCapacity) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException();
        
        // sizeCtl和实际容量cap 为 RoundToPower2( initialCapacity / (2/3) )
        int cap = ((initialCapacity >= (MAXIMUM_CAPACITY >>> 1)) ?
                   MAXIMUM_CAPACITY :
                   tableSizeFor(initialCapacity + (initialCapacity >>> 1) + 1));
        
        // 在表创建之前, sizeCtl用来保存第一次分配表的大小
        this.sizeCtl = cap;
    }

    public ConcurrentHashMap(int initialCapacity,
                             float loadFactor, int concurrencyLevel) 
        
        if (!(loadFactor > 0.0f) || initialCapacity < 0 || concurrencyLevel <= 0)
            throw new IllegalArgumentException();
        
        // 给定初始容量不能小于并发级别
        if (initialCapacity < concurrencyLevel)   // Use at least as many bins
            initialCapacity = concurrencyLevel;   // as estimated threads

        // sizeCtl和实际表容量cap 为 RoundToPowerOf2( initialCapacity / loadFactor )
        long size = (long)(1.0 + (long)initialCapacity / loadFactor);
        int cap = (size >= (long)MAXIMUM_CAPACITY) ?
            MAXIMUM_CAPACITY : tableSizeFor((int)size);
        this.sizeCtl = cap;
    }
    ```
* put
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
            Node<K,V> f; int n, i, fh;      // f: 头结点; n: 数组长度; i: 桶id; fh: first.hash

            // 2.1. 检查tab是否存在, 不存在先initTable
            if (tab == null || (n = tab.length) == 0)
                tab = initTable();

            // 2.2. 非CAS读快速检查桶数组是否为空, 是则尝试CAS写入新结点, 失败则回到2重新尝试
            else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
                if (casTabAt(tab, i, null,
                             new Node<K,V>(hash, key, value, null)))
                    break;                   
            }
            // 2.3. 表正在被迁移, 去参与迁移
            else if ((fh = f.hash) == MOVED)
                tab = helpTransfer(tab, f);
            
            else {
                V oldVal = null;
                // 2.4. 搜索结点, 搜索之前要锁表头结点
                synchronized (f) {
                    
                    // 2.4.1. 如果表头已经改变, 下次循环再尝试
                    if (tabAt(tab, i) == f) {
                        
                        // 2.4.1.1 桶为链表, 在链表中寻找结点位置
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
                                // 2.4.1.1.2 未找到结点则继续往下找
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
                            
                            if ((p = ((TreeBin<K,V>)f).putTreeVal(hash, key,
                                                           value)) != null) {
                                oldVal = p.val;
                                if (!onlyIfAbsent)
                                    p.val = value;
                            }
                        }
                    }
                }

                // 2.5 链表长度超过TREEIFY_THRESHOLD, 转成树
                if (binCount != 0) {
                    if (binCount >= TREEIFY_THRESHOLD)
                        treeifyBin(tab, i);
                    if (oldVal != null)
                        return oldVal;
                    break;
                }
            }
        }
        
        // 3. 计数
        addCount(1L, binCount);
        return null;
    }


    private final void treeifyBin(Node<K,V>[] tab, int index) {
        Node<K,V> b; int n, sc;
        // 1. 检查表, 表存在才能treeify
        if (tab != null) {
            // 1.1. 表容量达到MIN_TREEIFY_CAPACITY = 64才真正转成红黑树, 否则只是数组扩容
            if ((n = tab.length) < MIN_TREEIFY_CAPACITY)
                tryPresize(n << 1);
            // 1.2 头结点是否存在, 则抢表头结点
            else if ((b = tabAt(tab, index)) != null && b.hash >= 0) {
                synchronized (b) {
                    // 1.2.1 抢到还要再检查一次表头有没被更新
                    // 只有删除才可能改变头结点, 删完不用更新
                    // 如果删除完还有新线程加入结点, 那就由新线程来treeifyBin
                    if (tabAt(tab, index) == b) {
                        TreeNode<K,V> hd = null, tl = null;
                        // 1.2.1.1 复制链表, 新链表的结点为TreeNode
                        for (Node<K,V> e = b; e != null; e = e.next) {
                            TreeNode<K,V> p =
                                new TreeNode<K,V>(e.hash, e.key, e.val,
                                                  null, null);
                            if ((p.prev = tl) == null)
                                hd = p;
                            else
                                tl.next = p;
                            tl = p;
                        }
                        // 1.2.1.2 建树, 并放到对应的桶
                        setTabAt(tab, index, new TreeBin<K,V>(hd));
                    }
                }
            }
        }
    }

    /**
        TreeBin继承自Node(这样才能放到table中)
        hash设为TREEBIN(-1)表示是TreeBin
        与putTreeVal的区别是, 不用检查key是否存在
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
* 扩容
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

        // 1. 没有线程私有counter或者全局counter计数失败, 尝试1.1给线程私有counter计数
        if ((as = counterCells) != null ||
            !U.compareAndSwapLong(this, BASECOUNT, b = baseCount, s = b + x)) { 
            CounterCell a; long v; int m;
            boolean uncontended = true;

            // 1.1 尝试给线程私有count计数
            if (as == null || (m = as.length - 1) < 0 ||                        // 1. count表为空
                (a = as[ThreadLocalRandom.getProbe() & m]) == null ||           // 2. 线程指针与其它线程冲突, 导致counter在同一个位置
                !(uncontended =
                  U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))) {    // 3. 存在竞争(cas尝试计数失败)
                // 1.1.1 线程私有计数器计数失败, 则fullAddCount
                fullAddCount(x, uncontended);                                   
                return;
            }
            // 1.2. 不检查resize()则直接返回, 
            if (check <= 1)
                return;
            // 1.3. 如果要检查resize(), 则要汇总线程私有计数器的和
            s = sumCount(); // 计算所有count的和
        }

        // 2. 如果需要检查
        if (check >= 0) {
            Node<K,V>[] tab, nt; int n, sc;
            // 2.1 超出阈值, 要进行迁移
            while (s >= (long)(sc = sizeCtl) && (tab = table) != null &&
                   (n = tab.length) < MAXIMUM_CAPACITY) {
                // 假设tab.length 前面 有10个零,
                // 则resize标志, 为 0x0000800A
                int rs = resizeStamp(n);                                            
                if (sc < 0) {
                    if ((sc >>> RESIZE_STAMP_SHIFT) != rs   // 迁移标志被撤, 迁移已完成
                        || sc == rs + 1                      
                        || sc == rs + MAX_RESIZERS 
                        || (nt = nextTable) == null         // nextTable被撤, 迁移已完成
                        || transferIndex <= 0)              // 所有stripe已被处理完
                        break;
                    // 未迁移完成时, 尝试参与迁移
                    if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1))
                        transfer(tab, nt);
                }

                // 自己尝试发起迁移
                //     sc第1位为0表示正在迁移, 2-16位用来表示迁移的轮次 
                //     后16位用来记录参与迁移的线程数
                //     争夺成功则sizeCtl改成0x800A0000
                else if (U.compareAndSwapInt(this, SIZECTL, sc,
                                             (rs << RESIZE_STAMP_SHIFT) + 2))
                    transfer(tab, null);
                s = sumCount();
            }
        }
    }
    // [1] A thread' s "probe" value is a non-zero hash code that (probably) does not collide with other existing threads with respect to any power of two collision space. When it does collide, it is pseudo-randomly adjusted (using a Marsaglia XorShift)
    // 线程探针, 可理解成线程独有的一个随机数, 

    /**
     * Helps transfer if a resize is in progress.
     */
    final Node<K,V>[] helpTransfer(Node<K,V>[] tab, Node<K,V> f) {
        Node<K,V>[] nextTab; int sc;
        // 表不为空 && 再次检查首结点为ForwardingNode结点 && nextTabl
        if (tab != null && (f instanceof ForwardingNode) &&
            (nextTab = ((ForwardingNode<K,V>)f).nextTable) != null) {
            int rs = resizeStamp(tab.length);
            while (nextTab == nextTable && table == tab &&
                   (sc = sizeCtl) < 0) {
                // 如果没有迁移结束就要参与迁移
                if ((sc >>> RESIZE_STAMP_SHIFT) != rs   // 迁移结束
                    || sc == rs + 1 
                    || sc == rs + MAX_RESIZERS          // 满员
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
            // 如果桶数组为空, 初始化桶数组
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
                        sizeCtl = sc;
                    }
                }
            }
            else if (c <= sc || n >= MAXIMUM_CAPACITY)
                break;
            else if (tab == table) {
                int rs = resizeStamp(n);
                // 检查需不需要参与, 未完成迁移则要参与迁移
                if (sc < 0) {
                    Node<K,V>[] nt;
                    if ((sc >>> RESIZE_STAMP_SHIFT) != rs // 1. resize标志被撤, 迁移完成
                        || sc == rs + 1 
                        || sc == rs + MAX_RESIZERS      
                        || (nt = nextTable) == null       // 2. nextTable被撤, 迁移完成
                        || transferIndex <= 0)            // 3. stripe已被分配完
                        break;
                    if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1))
                        transfer(tab, nt);
                }
                // sizeCtl未被占有, CAS尝试占有sizeCtl, 把sizeCtl的高位设成resize标志
                else if (U.compareAndSwapInt(this, SIZECTL, sc,
                                             (rs << RESIZE_STAMP_SHIFT) + 2))
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

        // 检查nextTable是否已经被分配
        if (nextTab == null) {            
            try {
                @SuppressWarnings("unchecked")
                Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n << 1];
                nextTab = nt;
            } catch (Throwable ex) {      // try to cope with OutOfMemoryError
                sizeCtl = Integer.MAX_VALUE;
                return;
            }
            nextTable = nextTab;
            transferIndex = n;                                         // 设置第一个stripe的起点
        }
        int nextn = nextTab.length;
        ForwardingNode<K,V> fwd = new ForwardingNode<K,V>(nextTab);    //[1] 
        boolean advance = true;
        boolean finishing = false; 
        for (int i = 0, bound = 0;;) {
            Node<K,V> f; int fh;

            // 每个线程处理之前先申请stripe, 处理完一stripe再尝试申请一个stripe
            // 处理的时候从后往前处理
            while (advance) {
                int nextIndex, nextBound;
                // 分配成功, 不再向前找
                if (--i >= bound || finishing)
                    advance = false;

                // 下一个起点已经到头了, 不再向前找
                else if ((nextIndex = transferIndex) <= 0) {
                    i = -1;
                    advance = false;
                }
                // cas申请一个stripe, 申请成功后, 起点向前移动一stripe, 并且为当前stripe的下界
                else if (U.compareAndSwapInt
                         (this, TRANSFERINDEX, nextIndex,
                          nextBound = (nextIndex > stride ?
                                       nextIndex - stride : 0))) {
                    bound = nextBound;
                    i = nextIndex - 1;
                    advance = false;
                }
            }
            

            if (i < 0 || i >= n || i + n >= nextn) {
                int sc;
                // 迁移完成, 把新表赋给table    
                if (finishing) {
                    nextTable = null;
                    table = nextTab;
                    sizeCtl = (n << 1) - (n >>> 1);     // 更新阈值
                    return;
                }

                if (U.compareAndSwapInt(this, SIZECTL, sc = sizeCtl, sc - 1)) {
                    // 如果自己是最后一个完成的, 那直接返回
                    if ((sc - 2) != resizeStamp(n) << RESIZE_STAMP_SHIFT)       
                        return;
                    
                    // 最后一个完成的需要负责用新表替换旧表
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
            // 桶未被迁移过
            else {
                // 争夺头结点, 上锁并再次检查是否有更新
                synchronized (f) {
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
                            setTabAt(tab, i, fwd);                              // 把fwd结点放在旧表的对应位置
                            advance = true;
                        }
                    }
                }
            }
        }
    }
    //[1] 该结点用来通知访问结点的线程, 该桶已经成功迁移到nextTable
    ```