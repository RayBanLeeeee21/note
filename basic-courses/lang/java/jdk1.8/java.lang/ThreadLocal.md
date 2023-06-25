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
    - hash冲突解决方法: **线性探测再散列**: 
        - 每个ThreadLocal创建时带一个hash值
        - 发生哈希冲突时向后找
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


#### 清理

##### 快速清理 - 清理一片连续区域
`expungeStaleEntry()`执行一个类似标记-整理的过程
- 实现
  - 从坐标staleSlot开始, 向右清理一片连续的slot(遇到空slot时停下)
  - 过期的Entry被清理掉
  - 未过期的尝试放在更靠近 hash % (len - 1) 的位置, 便于查询
- 目的:
  - 只清理局部区域是为了防止过度占用用户线程的CPU
  - 移动未过期的entry是为了加速查询
-   ```java
    /**
     * Compact: 从一个过期slot开始, 向后清理一片过期slot, 并把未过期的整理放到靠前的位置
     * 遇到第一个空slot时停止
     *
     * @return 整理完成后, 从staleSlot往右数起第一个空slot的位置
     */
    private int expungeStaleEntry(int staleSlot) {
        Entry[] tab = table;
        int len = tab.length;

        // 回收第一个
        tab[staleSlot].value = null;
        tab[staleSlot] = null;
        size--;

        // 向右循环搜索, 直到遇到下个空slot为止, 或者是把所有都清理完了
        Entry e;
        int i;
        for (i = nextIndex(staleSlot, len); (e = tab[i]) != null; i = nextIndex(i, len)) {
            ThreadLocal<?> k = e.get();

            // entry过期, 直接将slot清空
            if (k == null) {
                e.value = null;
                tab[i] = null;
                size--;

                // entry未过期. 判断一个entry所在位置是否为 hash % (len-1)
                // 如果不是则尝试找一个更靠近 hash % (len - 1)的位置
            } else {
                int h = k.threadLocalHashCode & (len - 1);
                if (h != i) {  // h在i左边
                    tab[i] = null;

                    // 重新为其找个更靠左(靠近 hash % (len - 1)) 的位置
                    while (tab[h] != null) h = nextIndex(h, len);
                    tab[h] = e;
                }
            }
        }
        return i;
    }
    ```

##### 动态清理多片连续区域
`cleanSomeSlots()`方法连续执行若干次`expungeStaleEntry()`
- 实现:
  - 从i开始向右查找, 如果找了log_2 (n)个都没找到可以清理的, 就结束清理过程
  - 如果有找到可以清理的则重置n = length, 清理多点
- 目的:
  - 动态预测可回收的区域数量
  - 如果能回收的较少, 就快速结束, 防止长时间占用用户线程做清理工作
  - 如果能回收的较多, 就多回收一点, 使回收过程有所收益
-   ```java
    /**
     * 返回false表示一个都没清理到
     */
    private boolean cleanSomeSlots(int i, int n) {
        boolean removed = false;
        Entry[] tab = table;
        int len = tab.length;
        do {
            // 向右循环查找
            i = nextIndex(i, len);
            Entry e = tab[i];

            // 一旦找到一个可以回收的就回收一片
            if (e != null && e.get() == null) {
                n = len;
                removed = true;
                i = expungeStaleEntry(i);
            }

            // 如果找了log_2 (n)个都没找到可以清理的, 就结束清理过程
        } while ((n >>>= 1) != 0);
        return removed;
    }
    ```

##### 清理所有连续区域
```java
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
- replaceStaleEntry()过程中, 如果key存在, 假设其位置为idx, 
  一定不能把 [hash % (len - 1), idx) 这段位置清理掉, 否则会出现重复key的问题
-   ```java
        private void set(ThreadLocal<?> key, Object value) {

        Entry[] tab = table;
        int len = tab.length;
        int i = key.threadLocalHashCode & (len - 1); // 初始计算hash

        // 发生冲突时向后走
        for (Entry e = tab[i]; e != null; e = tab[i = nextIndex(i, len)]) {
            ThreadLocal<?> k = e.get();

            // 没有被gc回收掉
            if (k == key) {
                e.value = value;
                return;
            }

            // slot被回收了, 需要向右找到相同key的slot并替换, 或者是找到一个空slot并设置
            // - 期间会顺便清理
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

        // 先向左搜索一个清理起点 slotToExpunge 
        // - 风险: 必须有可回收的key, 不然这里会死循环
        int slotToExpunge = staleSlot;
        for (int i = prevIndex(staleSlot, len); (e = tab[i]) != null; i = prevIndex(i, len))
            if (e.get() == null) slotToExpunge = i;


        // 从staleSlot向右搜索一个空slot, 或者是key相等的slot
        for (int i = nextIndex(staleSlot, len); (e = tab[i]) != null; i = nextIndex(i, len)) {
            ThreadLocal<?> k = e.get();

            // 匹配到了要找的key
            if (k == key) {

                // 设置新值
                e.value = value;

                // 第一个过期的结点交换到位置i, 后面64行会被批量清理掉
                tab[i] = tab[staleSlot];
                tab[staleSlot] = e;  // 放到靠近 hash & (len - 1)的位置

                // slotToExpunge == staleSlot 表示 staleSlot左边没有可回收的
                // - 此时要从staleSlot右边找一个最接近的清理起点
                if (slotToExpunge == staleSlot) slotToExpunge = i;

                // 退出之前顺便做一下清理
                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
                return;
            }

            // slotToExpunge == staleSlot 表示 staleSlot左边没有可回收的
            // - 此时要从staleSlot右边找一个最接近的清理起点
            if (k == null && slotToExpunge == staleSlot)
                slotToExpunge = i;
        }

        // 上文向右搜索的过程没搜到key, 遇到了空slot, 则创建新Entry放到过期slot的位置
        tab[staleSlot].value = null;
        tab[staleSlot] = new Entry(key, value);

        // 退出之前顺便做一下清理
        if (slotToExpunge != staleSlot) cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
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


#### 扩容

扩容过程比较简单, 建个新的双倍容量的表替换上去
-   ```java
    private void rehash() {

        // 清理每一片连续区域
        expungeStaleEntries();

        // 可能会清理掉一些, 因此清理完以 0.75 * threshold 为阈值
        if (size >= threshold - threshold / 4) resize();
    }

    /**
        建一个双倍容量的新表, 按线性探测再散列法重新计算新位置, 放到新表上, 最后替换新表
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
    
    private void setThreshold(int len) {
        threshold = len * 2 / 3;
    }
    ```
