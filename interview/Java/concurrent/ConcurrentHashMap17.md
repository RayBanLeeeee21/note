问题
*  UNSAFE.putOrderedObject(ss, SBASE, s0); 的语义

ConcurrentHashMap
* 特性:
    * key/value不能为null (HashMap可以)
* HashEntry
    * 域
        * final int hash
        * final K key
        * volatile V value
        * volatile HashEntry<K,V> next
* 默认属性:
    * DEFAULT_INITIAL_CAPACITY = 16
    * DEFAULT_LOAD_FACTOR = 0.75
    * DEFAULT_CONCURRENCY_LEVEL = 16
    * MIN_SEGMENT_TABLE_CAPACITY = 2 (实际分配时, segment的表大小默认为2, 表大小总和为32)
* 相关属性计算
    1. ssize(segment的个数): 
        * 不超过MAX_SEGMENTS = 1<<16, 高位留给segment;  // 为了方便用 & 操作来求余
        * 不小于concurrencyLevel的2的幂
    2. 初始容量
        * 不超过MAXIMUM_CAPACITY = 1<<30    
    3. segment容量
        * 不小于 ceil(initCap / ssize) 的2的幂          // 为了方便用 & 操作来求余
        * 不小于MIN_SEGMENT_TABLE_CAPACITY = 2
    ```java
    public ConcurrentHashMap(int initialCapacity,
                             float loadFactor, int concurrencyLevel) {
        if (!(loadFactor > 0) || initialCapacity < 0 || concurrencyLevel <= 0)
            throw new IllegalArgumentException();

        // 并发级别不能高于1<<16
        if (concurrencyLevel > MAX_SEGMENTS)
            concurrencyLevel = MAX_SEGMENTS;
        
        // 实际并发级别ssize为不小于conccurencyLevel的2的幂
        int sshift = 0;
        int ssize = 1;
        while (ssize < concurrencyLevel) {
            ++sshift;
            ssize <<= 1;
        }

        this.segmentShift = 32 - sshift;
        this.segmentMask = ssize - 1;

        // 初始容量不超过 MAXIMUM_CAPACITY = 1<<30
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;

        // segment容量不小于 ceil(initCap / ssize) 的2的幂
        int c = initialCapacity / ssize;
        if (c * ssize < initialCapacity)
            ++c;
        int cap = MIN_SEGMENT_TABLE_CAPACITY;
        while (cap < c)
            cap <<= 1;

        // 创建第0个segment, 这个segment给其它segment的创建当参考
        Segment<K,V> s0 =
            new Segment<K,V>(loadFactor, (int)(cap * loadFactor),
                             (HashEntry<K,V>[])new HashEntry[cap]);
        Segment<K,V>[] ss = (Segment<K,V>[])new Segment[ssize];
        UNSAFE.putOrderedObject(ss, SBASE, s0); // ordered write of segments[0]
        this.segments = ss;
    }
    ```
* put
    ```java
    public V put(K key, V value) {
        Segment<K,V> s;
        if (value == null)                      // value不能为null
            throw new NullPointerException();

        // 计算hash, 用hash的高位给segment定位
        int hash = hash(key);
        int j = (hash >>> segmentShift) & segmentMask;

        // 非volatile快速确认segment是否存在, 不存在再用volatile读写尝试新建第j个segment
        if ((s = (Segment<K,V>)UNSAFE.getObject          
             (segments, (j << SSHIFT) + SBASE)) == null)
            s = ensureSegment(j);

        // 在segment中加入新结点
        return s.put(key, hash, value, false);
    }

    //
    final V put(K key, int hash, V value, boolean onlyIfAbsent) {

        // 循环CAS尝试取得锁, 多次不成功再以阻塞方式获取. 循环的过程中顺便找结点, 或者创建新结点 
        HashEntry<K,V> node = tryLock() ? null :
            scanAndLockForPut(key, hash, value);

        // 上一步中已取得锁
        V oldValue;
        try {
            HashEntry<K,V>[] tab = table;
            int index = (tab.length - 1) & hash;

            // 非volatile读(已取得锁)的方式取得首结点, 从首结点开始搜索
            HashEntry<K,V> first = entryAt(tab, index);     
            for (HashEntry<K,V> e = first;;) {
                if (e != null) {
                    K k;
                    // 判断key是否相等, 如果相等, 那就只做修改, 更新modCount
                    if ((k = e.key) == key ||   // 1. 如果对象id相等, 那一定相等, 速度最快
                        (e.hash == hash &&      // 2. 如果hash不等, 那一定不相等
                        key.equals(k))) {       // 3. 最后再用equals方法比较
                        oldValue = e.value;
                        if (!onlyIfAbsent) {
                            e.value = value;
                            ++modCount;
                        }
                        break;
                    }
                    e = e.next;
                }
                // key不存在
                else {
                    // 先检查新结点的准备情况
                    if (node != null)
                        node.setNext(first);
                    else
                        node = new HashEntry<K,V>(hash, key, value, first);
                    int c = count + 1;

                    // 检查需不需要rehash, 再加入新结点
                    if (c > threshold && tab.length < MAXIMUM_CAPACITY)
                        rehash(node);                           // rehash并加入新结点
                    else
                        setEntryAt(tab, index, node);           // putOrderedObject加入新结点
                    ++modCount;
                    count = c;
                    oldValue = null;
                    break;
                }
            }
        } finally {
            unlock();
        }
        return oldValue;
    }

    /**
        volatile读+双检查确认segment的存在, 并以循环volatile读 & CAS写入的方式创建segment
    */
    private Segment<K,V> ensureSegment(int k) {
        final Segment<K,V>[] ss = this.segments;
        long u = (k << SSHIFT) + SBASE; 
        Segment<K,V> seg;

        // volatile确认是否存在
        if ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u)) == null) {
            // 参照第0个segment的参数, 先做准备工作(相对耗时间)
            Segment<K,V> proto = ss[0]; // use segment 0 as prototype
            int cap = proto.table.length;
            float lf = proto.loadFactor;
            int threshold = (int)(cap * lf);
            HashEntry<K,V>[] tab = (HashEntry<K,V>[])new HashEntry[cap];

            // 第二次volatile检查
            if ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u))
                == null) { // recheck
                Segment<K,V> s = new Segment<K,V>(lf, threshold, tab);

                // 循环做 volatile读+CAS写
                while ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u))
                       == null) {
                    if (UNSAFE.compareAndSwapObject(ss, u, null, seg = s))
                        break;
                }
            }
        }
        return seg;
    }

    private HashEntry<K,V> scanAndLockForPut(K key, int hash, V value) {
        // volatile读取segment对应位置的首结点
        HashEntry<K,V> first = entryForHash(this, hash);    
        HashEntry<K,V> e = first;                           // 滑动指针
        HashEntry<K,V> node = null;
        int retries = -1; 
        while (!tryLock()) {
            HashEntry<K,V> f; 
            if (retries < 0) {

                // 在segment的指定链表, 查找到了尾部都没找到key对应结点, 则新建结点
                if (e == null) {
                    if (node == null) 
                        node = new HashEntry<K,V>(hash, key, value, null);
                    // 确认没找到结点, 才开始记录尝试取锁的次数
                    retries = 0;
                }
                // 找到结点也要开始记录尝试取锁的次数
                else if (key.equals(e.key))
                    retries = 0;
                else
                    e = e.next;
            }
            // 超过尝试次数, 以阻塞的方式等待锁
            else if (++retries > MAX_SCAN_RETRIES) {
                lock();
                break;
            }
            // 超出次数之前, 每两次循环用volatile读检查一下首结点有没更新
            // 有的话, 要重新搜索, 重新尝试, 重新计数
            else if ((retries & 1) == 0 &&
                        (f = entryForHash(this, hash)) != first) {
                e = first = f; 
                retries = -1;
            }
        }
        return node;
    }

    private void rehash(HashEntry<K,V> node) {
        // 计算新容量, 更新table和threshold
        HashEntry<K,V>[] oldTable = table;
        int oldCapacity = oldTable.length;
        int newCapacity = oldCapacity << 1;
        threshold = (int)(newCapacity * loadFactor);
        HashEntry<K,V>[] newTable =
            (HashEntry<K,V>[]) new HashEntry[newCapacity];
        int sizeMask = newCapacity - 1;

        // 处理每个桶
        for (int i = 0; i < oldCapacity ; i++) {
            HashEntry<K,V> e = oldTable[i];
            if (e != null) {
                HashEntry<K,V> next = e.next;
                int idx = e.hash & sizeMask;
                if (next == null)   //  Single node on list
                    newTable[idx] = e;
                else {
                    // 先找最长一段连续相同的链尾
                    HashEntry<K,V> lastRun = e;
                    int lastIdx = idx;
                    for (HashEntry<K,V> last = next;
                            last != null;
                            last = last.next) {
                        int k = last.hash & sizeMask;
                        if (k != lastIdx) {
                            lastIdx = k;
                            lastRun = last;
                        }
                    }

                    // 把连续的链尾先加入新表 (链表被复用, 此时从原表中还可以访问)
                    newTable[lastIdx] = lastRun;
                    
                    // 处理原链表中剩余的结点, 拆分并克隆
                    for (HashEntry<K,V> p = e; p != lastRun; p = p.next) {
                        V v = p.value;
                        int h = p.hash;
                        int k = h & sizeMask;
                        HashEntry<K,V> n = newTable[k];
                        newTable[k] = new HashEntry<K,V>(h, p.key, v, n);
                    }
                }
            }
        }

        // 最后把新结点加入新表中的位置
        int nodeIndex = node.hash & sizeMask; 
        node.setNext(newTable[nodeIndex]);
        newTable[nodeIndex] = node;
        
        //最后才替换表
        table = newTable;
    }
    ```
* get方法
    ```java
    public V get(Object key) {
        Segment<K,V> s; 
        HashEntry<K,V>[] tab;

        // hash计算
        int h = hash(key);
        long u = (((h >>> segmentShift) & segmentMask) << SSHIFT) + SBASE;

        // 以volatile方式读取segment, 并且判断segment和它的表是否为null
        if ((s = (Segment<K,V>)UNSAFE.getObjectVolatile(segments, u)) != null &&
            (tab = s.table) != null) {

            // 以volatile方式读取hash值对应的链表首结点, 并在链表中查找
            for (HashEntry<K,V> e = (HashEntry<K,V>) UNSAFE.getObjectVolatile
                     (tab, ((long)(((tab.length - 1) & h)) << TSHIFT) + TBASE);
                 e != null; e = e.next) {
                K k;
                if ((k = e.key) == key || (e.hash == h && key.equals(k)))
                    return e.value;
            }
        }
        return null;
    }
    ```
* remove方法
    ```java
    /**
        Object value: value为null时或者value匹配时才删除
    */
    final V remove(Object key, int hash, Object value) {
        if (!tryLock())
            scanAndLock(key, hash);
        V oldValue = null;

        // 已上锁
        try {
            // 计算hash
            HashEntry<K,V>[] tab = table;
            int index = (tab.length - 1) & hash;

            // 非volatile读(已锁)获取首结点
            HashEntry<K,V> e = entryAt(tab, index);
            HashEntry<K,V> pred = null;
            while (e != null) {
                K k;
                HashEntry<K,V> next = e.next;

                // 判断key, 找到则删除
                if ((k = e.key) == key ||
                    (e.hash == hash && key.equals(k))) {
                    V v = e.value;
                    if (value == null || value == v || value.equals(v)) {
                        if (pred == null)
                            setEntryAt(tab, index, next);
                        else
                            pred.setNext(next);
                        ++modCount;
                        --count;
                        oldValue = v;
                    }
                    break;
                }
                pred = e;
                e = next;
            }
        } finally {
            unlock();
        }
        return oldValue;
    }

    private void scanAndLock(Object key, int hash) {
        // volatile读获取首结点
        HashEntry<K,V> first = entryForHash(this, hash);
        HashEntry<K,V> e = first;
        int retries = -1;
        while (!tryLock()) {
            HashEntry<K,V> f;
            // 先查找结点
            if (retries < 0) {
                if (e == null || key.equals(e.key))
                    retries = 0;
                else
                    e = e.next;
            }
            // 找到结点或者找到表尾才开始计数. 
            // 尝试次数过多, 阻塞等待锁
            else if (++retries > MAX_SCAN_RETRIES) {
                lock();
                break;
            }
            // 每两次循环检查一下首结点有没被更新
            else if ((retries & 1) == 0 &&
                        (f = entryForHash(this, hash)) != first) {
                e = first = f;
                retries = -1;
            }
        }
    }
    ```