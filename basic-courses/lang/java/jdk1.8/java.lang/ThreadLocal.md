# ThreadLocal

`ThreadLocal`: 用来存线程局部变量
- 实现
    - *并不是在`ThreadLocal`内部用`ConcurrentHashMap`之类的保存线程到值的映射*
    - 而是在`Thread`的内部用一个map来保存`ThreadLocal`对象到值的索引


## 实现 

### 基本操作

基本操作
- set()
    ```java
    public void set(T value) {
        Thread t = Thread.currentThread();
        // 拿到线程内部的Map
            // 如果是InheritableThreadLocal, 拿的就是t.inheritableThreadLocals
            // 如果是ThreadLocal, 拿的就是t.threadLocals
        ThreadLocalMap map = getMap(t);     

        if (map != null)
            map.set(this, value);   // 加入map
        else
            createMap(t, value);    // 先创建map
    }

    // 注意到, 创建这个map是不用保证线程安全的, 线程只会创建它自己的Thread对象的threadLocals
    void createMap(Thread t, T firstValue) {
        t.threadLocals = new ThreadLocalMap(this, firstValue);
    }
    ```
- get()
    ```java
    public T get() {
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null) {

            // 从map中找到就返回
            ThreadLocalMap.Entry e = map.getEntry(this);
            if (e != null) {
                @SuppressWarnings("unchecked")
                T result = (T)e.value;
                return result;
            }
        }

        // 用初始化器初始值
        return setInitialValue();
    }

    private T setInitialValue() {
        // 初始化值放到map中
        T value = initialValue();           
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null)
            map.set(this, value);
        else
            createMap(t, value);
        return value;
    }
    ```
- 带初始化器的`SuppliedThreadLocal`
    ```java
    static final class SuppliedThreadLocal<T> extends ThreadLocal<T> {

        private final Supplier<? extends T> supplier;

        SuppliedThreadLocal(Supplier<? extends T> supplier) {
            this.supplier = Objects.requireNonNull(supplier);
        }

        @Override
        protected T initialValue() {
            return supplier.get();
        }
    }
    ```

### ThreadLocalMap

ThreadLocalMap
- 特点:
    - hash冲突解决方法: **线性探测再散列**: 发生哈希冲突时向后找
        - Q: 为什么不用链地址法? A: 回收时, 遍历数组与遍历链表数组中的结点相比, 能更好地利用局部性原理
    - 负载因子: 2/3
    - 初始容量: 16
    - 哈希计算

ThreadLocalMap的结点
- ThreadLocalMap的`Entry`将key (ThreadLocal引用) 放在软引用中. 当ThreadLocal没有其它引用时, 发生gc的时候Entry中的key就会被回收
-   ```java
        static class Entry extends WeakReference<ThreadLocal<?>> {
            /** The value associated with this ThreadLocal. */
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                super(k);     
                value = v;
            }
        }
    ``` 


#### 清理过期slot

`expungeStaleEntry()`执行一个类似**标记-整理**的过程
- 从坐标`staleSlot`开始, 整理连续一片slot, 空的被清理掉, 非空的整理到靠前的位置
- 直到遇到第一个空slot才停下
-   ```java
    /**
        Compact: 从一个过期slot开始, 向后清理一片过期slot, 并把未过期的整理放到靠前的位置
            遇到第一个空slot时停止
        @return 整理完成后, 从staleSlot往右数起第一个空slot的位置
    */
    private int expungeStaleEntry(int staleSlot) {
        Entry[] tab = table;
        int len = tab.length;

        // 回收第一个
        tab[staleSlot].value = null;
        tab[staleSlot] = null;
        size--;

        // 一直处理到遇到下个空slot
        Entry e; int i;
        for (i = nextIndex(staleSlot, len); (e = tab[i]) != null; i = nextIndex(i, len)) {
            ThreadLocal<?> k = e.get();

            // key被回收, 直接将slot清空
            if (k == null) {
                e.value = null;
                tab[i] = null;
                size--;
            } else {

                // key未回收, 则判断hash是否冲突, 如发生过冲突(推后保存)则重新为Entry找一个更靠前的位置
                int h = k.threadLocalHashCode & (len - 1);
                if (h != i) {
                    tab[i] = null;

                    // 重新找位置
                    while (tab[h] != null) h = nextIndex(h, len);
                    tab[h] = e;
                }
            }
        }
        return i;
    }
    ```

`cleanSomeSlots()`方法连续执行若干次`expungeStaleEntry()`
- 执行次数为 `log_2 (size)`, 即是说size越大, 循环次数越多
- 如果table很大, 但是很稀疏, 那循环的过程会频繁遇到null, 从而快速结束循环
-   ```java
    /** 返回false表示一个都没清理到 */
    private boolean cleanSomeSlots(int i, int n) {
        boolean removed = false;
        Entry[] tab = table;
        int len = tab.length;
        do {
            i = nextIndex(i, len);
            Entry e = tab[i];
            if (e != null && e.get() == null) {
                n = len;
                removed = true;
                i = expungeStaleEntry(i);
            }
        } while ( (n >>>= 1) != 0);
        return removed;
    }
    ```

#### key.threadLocalHashCode计算

key.threadLocalHashCode计算
-   ```java
    // ThreadLocal类

    private static AtomicInteger nextHashCode = new AtomicInteger();

    private static final int HASH_INCREMENT = 0x61c88647;

    private static int nextHashCode() {
        return nextHashCode.getAndAdd(HASH_INCREMENT);
    }
    ```

#### ThreadLocalMap.set()

set(): 
- set()的过程中遇到过期slot会清理掉过期slot并占用原来过期slot的位置
- set()完以后也会顺便做一下清理
- set()的时候顺便清理, 是防止GC造成一大片过期slot在那里占用位置, 影响查询效率
-   ```java
    private void set(ThreadLocal<?> key, Object value) {

        Entry[] tab = table;
        int len = tab.length;
        int i = key.threadLocalHashCode & (len-1); // 定位slot

        for (Entry e = tab[i];
                e != null;
                e = tab[i = nextIndex(i, len)]) { // 发生冲突时向后走
            ThreadLocal<?> k = e.get();

            // 匹配到key时, 设置后返回
            if (k == key) {
                e.value = value;
                return;
            }

            // 当前slot的Entry.key被回收了, 则去往后找到key所在的Entry, 换到这里来
            if (k == null) {
                replaceStaleEntry(key, value, i);
                return;
            }
        }

        // 创建新Entry
        tab[i] = new Entry(key, value);
        int sz = ++size;

        // 先清理, 如果一个都没清理成功, 并且达到阈值, 那就扩容
        if (!cleanSomeSlots(i, sz) && sz >= threshold) rehash();
    }

    private void replaceStaleEntry(ThreadLocal<?> key, Object value,
                                       int staleSlot) {
        Entry[] tab = table;
        int len = tab.length;
        Entry e;
        
        // slotToExpunge是后面清理过程的起点
        int slotToExpunge = staleSlot;

        // 先向前找最靠前的key被回收掉的entry位置, 作为slotToExpunge, 直到被空的slot挡住
        for (int i = prevIndex(staleSlot, len); (e = tab[i]) != null; i = prevIndex(i, len))
            if (e.get() == null) slotToExpunge = i;

    
        // 从staleSlot向后匹配key, 直到遇到空slot
        for (int i = nextIndex(staleSlot, len); (e = tab[i]) != null; i = nextIndex(i, len)) {
            ThreadLocal<?> k = e.get();

            // 匹配到了要找的key
            if (k == key) {

                // 设置新值
                e.value = value;

                // 将Entry与过期的Entry交换
                    // 交换过程一直不会把Entry放到 hash & (len - 1)之前的位置
                tab[i] = tab[staleSlot];
                tab[staleSlot] = e;

                // 退出之前顺便做一下清理
                if (slotToExpunge == staleSlot) slotToExpunge = i;
                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
                return;
            }

            // 重新选择清理的起点
            if (k == null && slotToExpunge == staleSlot)
                slotToExpunge = i;
        }


        // 匹配不到key(之前没set或者被回收), 就创建新Entry放到过期slot的位置
        tab[staleSlot].value = null;
        tab[staleSlot] = new Entry(key, value);

        // 退出之前顺便做一下清理
        if (slotToExpunge != staleSlot) cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
    }
    ```

#### 扩容

扩容过程比较简单, 建个新的双倍容量的表替换上去
-   ```java
    private void rehash() {

        // 清理所有过期slot
        expungeStaleEntries();

        // 可能会清理掉一些, 因此清理完以 0.75 * threshold 为阈值
        if (size >= threshold - threshold / 4) resize();
    }

    /**
        清理所有过期slot 
     */
    private void expungeStaleEntries() {
        Entry[] tab = table;
        int len = tab.length;
        for (int j = 0; j < len; j++) {
            Entry e = tab[j];
            if (e != null && e.get() == null)
                expungeStaleEntry(j);
        }
    }

    /**
        建一个双倍容量的新表, 按线性探测再散列法重新计算在新容量下Entry的位置, 放到新表上, 最后替换新表
    */
    private void resize() {
        Entry[] oldTab = table;
        int oldLen = oldTab.length;
        int newLen = oldLen * 2;
        Entry[] newTab = new Entry[newLen];
        int count = 0;

        for (int j = 0; j < oldLen; ++j) {
            Entry e = oldTab[j];
            if (e != null) {
                ThreadLocal<?> k = e.get();
                if (k == null) {
                    e.value = null; // Help the GC
                } else {
                    int h = k.threadLocalHashCode & (newLen - 1);
                    while (newTab[h] != null) h = nextIndex(h, newLen);
                    newTab[h] = e;
                    count++;
                }
            }
        }

        setThreshold(newLen);
        size = count;
        table = newTab;
    }
    ```

#### getEntry()

```java
    private Entry getEntry(ThreadLocal<?> key) {
        int i = key.threadLocalHashCode & (table.length - 1);
        Entry e = table[i];
        if (e != null && e.get() == key)
            return e;  // 没有发生冲突, 找到就返回
        else
            return getEntryAfterMiss(key, i, e); // 向后找
    }

    /**
        从坐标(hash & (len - 1))开始往后找, 遇到过期的清理一下, 找到匹配的就返回
    */ 
    private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
        Entry[] tab = table;
        int len = tab.length;

        while (e != null) {
            ThreadLocal<?> k = e.get();
            if (k == key)
                return e;
            if (k == null)
                expungeStaleEntry(i);
            else
                i = nextIndex(i, len);
            e = tab[i];
        }
        return null;
    }
```


