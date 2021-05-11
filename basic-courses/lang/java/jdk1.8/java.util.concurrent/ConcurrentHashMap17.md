问题
- `UNSAFE.putOrderedObject(ss, SBASE, s0)`
    - 以`putOrderedsObject()`的方式put的时候, 结果不是立即可见, 但是保证顺序

ConcurrentHashMap
- 特性:
    - key/value不能为null (HashMap可以): 如果支持null, 则`containsKey()`会带来歧义
    - `Segment`是`ReentranLock`的子类
        - `put()`的时候不直接`lock()`, 而是`tryLock()`到一定次数再`lock()`
    - 每个`Segment`的桶表独立扩展
- 默认属性:
    - 容量:
        ```java
        /** 默认初始化容量
            - 指全部segment加起来的. 
            - 但是分下去segment以后, 还会取整为2的幂, 并且不能小于MIN_SEGMENT_TABLE_CAPACITY
        */
        static final int DEFAULT_INITIAL_CAPACITY = 16;                                                        
        static final float DEFAULT_LOAD_FACTOR = 0.75f;     // 默认负载因子
        ```
    - segment:
        ```java
        static final int DEFAULT_CONCURRENCY_LEVEL = 16;    // 默认segment数
        static final int MAX_SEGMENTS = 1 << 16;            // 最大segment数
        static final int MIN_SEGMENT_TABLE_CAPACITY = 2;    // segment中table最小值
        ```
    - retries: 
        ```java
        static final int RETRIES_BEFORE_LOCK = 2;           // contains()/size()方法中上锁之前重试的次数
        ```


初始化
1. `ssize`: segment的个数
    - 不小于concurrencyLevel的2的幂 - 为了方便用`&`操作来求余
    - 不超过`MAX_SEGMENTS`(即 1<<16)  
2. `initCap`: 初始容量
    - 不超过MAXIMUM_CAPACITY = 1<<30
3. `cap`: segment的表容量
    - `cap = Math.max(roundToPowerOf2(initCap / ssize), MIN_SEGMENT_TABLE_CAPACITY)`
    

### 方法实现

#### 初始化

```java
    public ConcurrentHashMap(int initialCapacity,
                             float loadFactor, int concurrencyLevel) {
        if (!(loadFactor > 0) || initialCapacity < 0 || concurrencyLevel <= 0)
            throw new IllegalArgumentException();

        // 1. 计算并发级别: 不高于 1 << 16
        if (concurrencyLevel > MAX_SEGMENTS)
            concurrencyLevel = MAX_SEGMENTS;
        
        // 并发级别取整为2的幂
        int sshift = 0;
        int ssize = 1;
        while (ssize < concurrencyLevel) {
            ++sshift;
            ssize <<= 1;
        }

        this.segmentShift = 32 - sshift;
        this.segmentMask = ssize - 1;

        // 2. 计算初始容量
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;

        // 3. 计算segment中表容量
        int c = initialCapacity / ssize;
        if (c * ssize < initialCapacity)
            ++c;
        int cap = MIN_SEGMENT_TABLE_CAPACITY;
        while (cap < c)
            cap <<= 1;

        // ...
    }
```

### 初始化Segment和数组

初始segment与segment数组
1. 先在构造器中创建`segment[0]`, 以保存一些容量参数
2. 在`put()`的时候懒加载创建其它`segment`, 以**双检查 + volatile读写 + 循环CAS**的方法保证线程安全

```java
public ConcurrentHashMap(int initialCapacity,
                             float loadFactor, int concurrencyLevel) {
        // ...

        // segments 和 segments[0]要先创建, 供后面其它segment的创建进行参考
        Segment<K,V> s0 =
            new Segment<K,V>(loadFactor, (int)(cap * loadFactor),
                             (HashEntry<K,V>[])new HashEntry[cap]);
        Segment<K,V>[] ss = (Segment<K,V>[])new Segment[ssize];
        UNSAFE.putOrderedObject(ss, SBASE, s0);     // 没必要volatile
        this.segments = ss;
    }
```

```java
    /**
        创建Segment: 双检查 + 循环CAS实现
            - 对数组和segment的读取必须要用volatile
    */
    private Segment<K,V> ensureSegment(int k) {     // k为segment索引
        final Segment<K,V>[] ss = this.segments;
        long u = (k << SSHIFT) + SBASE; 
        Segment<K,V> seg;

        // 双检查-1 + volatile读
        if ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u)) == null) {

            // 参照第0个segment的参数, 创建新表(相对耗时间)
            Segment<K,V> proto = ss[0]; // use segment 0 as prototype
            int cap = proto.table.length;
            float lf = proto.loadFactor;
            int threshold = (int)(cap * lf);
            HashEntry<K,V>[] tab = (HashEntry<K,V>[])new HashEntry[cap];

            // 双检查-2 + volatile读
            if ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u))
                == null) { // recheck
                Segment<K,V> s = new Segment<K,V>(lf, threshold, tab);

                // 循环CAS尝试 + volatile写
                while ((seg = (Segment<K,V>)UNSAFE.getObjectVolatile(ss, u))
                       == null) {
                    if (UNSAFE.compareAndSwapObject(ss, u, null, seg = s))
                        break;
                }
            }
        }
        return seg;
    }
```

#### put
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

        // 找结点并尝试上锁
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
                    if ((k = e.key) == key ||   // 1. 如果对象id相等, 那一定相等, 速度快
                        (e.hash == hash &&      // 2. 如果hash不等, 那一定不相等, 速度快
                        key.equals(k))) {       // 3. 最后再用equals方法比较, 速度慢
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
                    // 检查要不要创建结点
                    if (node != null)
                        node.setNext(first);
                    else
                        node = new HashEntry<K,V>(hash, key, value, first);
                    int c = count + 1;

                    // 检查需不需要rehash, 再加入新结点
                    if (c > threshold && tab.length < MAXIMUM_CAPACITY)
                        rehash(node);                           // rehash并加入新结点
                    else
                        setEntryAt(tab, index, node);           // 已经上锁, 只用非volatile写加入新结点
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

    
    // 找结点, 找到后重试上锁, 并且随时检查一下头结点有没变
    private HashEntry<K,V> scanAndLockForPut(K key, int hash, V value) {

        // volatile读取segment对应位置的首结点
        HashEntry<K,V> first = entryForHash(this, hash);    
        HashEntry<K,V> e = first;
        HashEntry<K,V> node = null;
        int retries = -1; 
        while (!tryLock()) {
            HashEntry<K,V> f; 

            // 阶段1: 找结点 - 每个迭代tryLock()一次, 移动指针一次, 
                // 直到找到结点或确认不存在
            if (retries < 0) {

                if (e == null) {
                    if (node == null) 
                        node = new HashEntry<K,V>(hash, key, value, null);
                    retries = 0;
                }
                else if (key.equals(e.key))
                    retries = 0;
                else
                    e = e.next; // 指针向前
            }
            // 阶段2: 重试64次 tryLock()
            else if (++retries > MAX_SCAN_RETRIES) {
                lock();
                break;
            }
            // 隔两次重试检查一次头结点有没改变, 变了则回到阶段1重找
            else if ((retries & 1) == 0 && (f = entryForHash(this, hash)) != first) {
                e = first = f; 
                retries = -1;
            }
        }
        return node;
    }

    // 拆分链表, 然后换新的table
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

        // 最后把新结点加入新表的对应位置的表头
        int nodeIndex = node.hash & sizeMask; 
        node.setNext(newTable[nodeIndex]);
        newTable[nodeIndex] = node;
        
        //最后才替换表
        table = newTable;
    }
    ```

#### get

`get()`时不上锁
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
#### remove
    ```java
    /**
        Object value: value为null时或者value匹配时才删除
    */
    final V remove(Object key, int hash, Object value) {

        // 尝试上锁
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
                    if (value == null 
                        || value == v || value.equals(v)) { // key不为null时, 表示需要满足value也相同的条件才能删除
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

    // 不会返回结点, 但可以预热缓存
    private void scanAndLock(Object key, int hash) {
        // volatile读获取首结点
        HashEntry<K,V> first = entryForHash(this, hash);
        HashEntry<K,V> e = first;
        int retries = -1;
        while (!tryLock()) {
            HashEntry<K,V> f;
            // 阶段1: 找结点 - 每个迭代tryLock()一次, 移动指针一次
            if (retries < 0) {
                if (e == null || key.equals(e.key))
                    retries = 0;
                else
                    e = e.next;
            }
            // 阶段2: 循环tryLock()直到达到64次后阻塞
            else if (++retries > MAX_SCAN_RETRIES) {
                lock();
                break;
            }
            // 每两次循环检查一下首结点有没被更新, 更新了要重新找
            else if ((retries & 1) == 0 &&
                        (f = entryForHash(this, hash)) != first) {
                e = first = f;
                retries = -1;
            }
        }
    }
    ```