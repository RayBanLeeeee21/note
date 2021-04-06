

# Striped64

## 先验知识
* ``Thread#threadLocalRandomProbe``
* ``ThreadLocalRandom``
* ``CAS``:
    ```java
    boolean compareAndSet(T expectedVal, T newVal) {
        if (this.val == expoectedVal) {
            this.val = newVal;
            return true;
        } else {
            return false;
        }
    }
    ```
* 双检查锁:
    ```java
    public class Singleton {
        private volatile static Singleton singleton;

        private Singleton() {}

        public Singleton getInstance() {
            if (null == singleton) {
                synchronized (Singleton.class) {
                    if (null == singleton) {
                        singleton = new Singleton();
                    }
                }
            }
            return singleton;
        }
    }
    ```
* 双检查锁 (乐观锁版):
    ```java
    public class Singleton {
        private volatile static Singleton singleton;
        private AtomicBoolean casLock = new AtomicBoolean(false);

        private Singleton() {}

        public Singleton getInstance() {

            whlie (true) {

                // 第一次检查
                if (null == singleton) {
                    if (casLock.compareAndSet(false, true)) {   // CAS尝试上锁
                        try {
                            // 重检查, 防止另一个线程已经创建过, 并且释放了锁
                            if (null == singleton) {         
                                singleton = new Singleton();
                            } 
                            return singleton;
                        } finally {
                            casLock.set(false); // 释放锁
                        }
                    }
                    
                    continue; // 抢不到锁, 说明另一个线程正在创建, 继续循环(自旋)
                }
                
                return singleton; // singleton已经创建过, 直接返回
            }
        }
    }
    ```
* 缓存一致性原理MESI

## 头注释解读

以下是对于类头注释的翻译&解读. 不过有些句子不会按最直白的方法翻译, 而是会做一些调整, 或者加一些额外的内容, 便于理解. *翻译的原则是保证静态的意思一致, 而不是逐字逐句对应*.

> This class maintains a lazily-initialized table of atomically updated variables, plus an extra "base" field. The table size is a power of two. Indexing uses masked per-thread hash codes. Nearly all declarations in this class are package-private, accessed directly by subclasses.

``Strip64``主要用作多线程计数器相关类的功能(如``LongAdder``, ``LongAccumulator``等)的基类. 该类维护一个**懒加载**的, 长度为2的幂的Cell表, 其中每个Cell都存储一个数值, 并且只能原子地对数值进行操作, 其作用类似于``Atomic``类. 线程要将变化量更新到``Strip64``上时, 会基于一个**线程私有的hash值**(线程探针值)对表长度求余, 作为索引定位到一个Cell, 然后将变化量更新到该Cell.

> Table entries are of class Cell; a variant of AtomicLong padded (via @sun.misc.Contended) to reduce cache contention. Padding is overkill for most Atomics because they are usually irregularly scattered in memory and thus don't interfere much with each other. But Atomic objects residing in arrays will tend to be placed adjacent to each other, and so will most often share cache lines (with a huge negative performance impact) without this precaution.

Cell表的元素--``Cell``是一种变种的``AtomicLong``, 与普通``AtomicLong``的区别在于, 实例带有填充值(通过``@sun.misc.Contended``注解实现), 保证一个``Cell``实例能独占一个cache行, 防止CPU cache级别的冲突. ``AtomicLong``对象通常分散在内存中, 因此不用考虑占用同一cache行的问题. 但Atomic类型数组可能在内存中是连续的.

> In part because Cells are relatively large, we avoid creating them until they are needed. When there is no contention, all updates are made to the base field. Upon first contention (a failed CAS on base update), the table is initialized to size 2. The table size is doubled upon further contention until reaching the nearest power of two greater than or equal to the number of CPUS. Table slots remain empty (null) until they are needed.

由于``Cell``占用空间较大, 我们尽量避免``Cell``的创建, 除非不得不创建. 在发生线程竞争之前(多于一个线程), 所有值更新都在``base``上做. 当第一次线程竞争发生的时候, ``Cell``表会被初始化为容量为2的``Cell``数组, 然后以2为幂扩容, 直到刚好大于CPU数. 而``Cell``表中的slot初始为null, 直到需要创建``Cell``的时候再创建.

> A single spinlock ("cellsBusy") is used for initializing and resizing the table, as well as populating slots with new Cells. There is no need for a blocking lock; when the lock is not available, threads try other slots (or the base). During these retries, there is increased contention and reduced locality, which is still better than alternatives.

自旋锁``cellsBusy``用来保证``Cell``表的**创建**和**扩容(并重新安排Cell)**的过程是线程安全的. 并且不会发生阻塞. 当锁不可用时, 线程会尝试其它slot, 或者将值更新到base上, 因此没有必要用会阻塞的锁. During these retries, there is increased contention and reduced locality, which is still better than alternatives (??没看懂).

> The Thread probe fields maintained via ThreadLocalRandom serve as per-thread hash codes. We let them remain uninitialized as zero (if they come in this way) until they contend at slot 0. They are then initialized to values that typically do not often conflict with others. Contention and/or table collisions are indicated by failed CASes when performing an update operation. Upon a collision, if the table size is less than the capacity, it is doubled in size unless some other thread holds the lock. If a hashed slot is empty, and lock is available, a new Cell is created. Otherwise, if the slot exists, a CAS is tried. Retries proceed by "double hashing", using a secondary hash (Marsaglia XorShift) to try to find a free slot.

由``ThreadLocalRandom``维护的**线程探针值**(Thread probe)在``Stripe64``被用作一个线程私有的hash值. 刚开始的时候, 线程探针值未被初始化, 其值为0, 直到在slot 0发生竞争(*补充: 此处注释可能有误, 代码中是在``base``属性上发生竞争时, 竞争失败的一方会触发探针值的初始化*). 探针值通常会被初始化成不容易与其它线程相冲突的值. 在更新值时发生的CAS失败, 意味着发生了竞争或/和碰撞(*竞争指CAS的失败, 而碰撞指两个线程选中同一个Cell*). 当发生碰撞时, 如果表未达到最大容量, CAS失败的线程会尝试将表扩容, 使容量翻倍(除非其它线程拿到了锁). 如果rehash后选中的slot是空的, 并且锁可用, 则线程会创建一个新的Cell; 如果slot非空, 则会进行CAS重试. Retries proceed by "double hashing", using a secondary hash (Marsaglia XorShift) to try to find a free slot. (*不知道该怎么翻译比较恰当*).

> The table size is capped because, when there are more threads than CPUs, supposing that each thread were bound to a CPU, there would exist a perfect hash function mapping threads to slots that eliminates collisions. When we reach capacity, we search for this mapping by randomly varying the hash codes of colliding threads.  Because search is random, and collisions only become known via CAS failures, convergence can be slow, and because threads are typically not bound to CPUS forever, may not occur at all. However, despite these limitations, observed contention rates are typically low in these cases.

表容量之所以有上限, 是因为当线程数大于核数时, 如果每个线程被指派到一个CPU核心, 存在一个完美的hash函数可以把每个线程映射到slot中, 并且不存在碰撞. 当到达容量上限后, 我们以随机地改变碰撞线程hash值的方法搜索该hash函数(*随机改变hash值使最后的映射的结果逐步调整成符合该hash函数的要求*). 因为搜索过程是随机的, 而碰撞只能在CAS失败发生的时候才能被观察到, 因此收敛过程会很慢, 而线程通常不会一直被绑定在一个CPU核上, 因此可能不会收敛. 然而, (实验)观察到的竞争率会非常低.

*笔者的理解: 以4核开8个线程为例, 假设4个核分别被分派了线程{a,b}, {c,d}, {e,f}, {g,h}, 并且分派关系不会发生改变. Cell表容量为4, 经过长时间的收敛后{a,b}, {c,d}, {e,f}, {g,h}分别选中slot 0, slot 1, slot 2, slot 3, 则基本上不会发生碰撞(因为一个核一次只能执行一个线程), 除非线程切换刚好发生在**读到旧值**与**CAS**之间*:
```
T0: core-1: thread-a: long old = cells[0].val
T1: core-1: thread-b: long old = cells[0].val
T2: core-1: thread-b: if (cells[0].cas(old, newVal)) 
T3: core-1: thread-a: if (cells[0].cas(old, newVal)) 
```
*但线程切换发生在这种地方的概率很低*

> It is possible for a Cell to become unused when threads that once hashed to it terminate, as well as in the case where doubling the table causes no thread to hash to it under expanded mask.  We do not try to detect or remove such cells, under the assumption that for long-running instances, observed contention levels will recur, so the cells will eventually be needed again; and for short-lived ones, it does not matter.

某个slot的``Cell``被创建后, 有可能刚好遇到线程的终结, 或者扩容后其所在slot不会被hash选中, 从而无法被使用到. 对于这种, 我们不会做检测或者移除. 如果应用运行时间够长, 我们假定它总会在某个遥远的时间点被新线程访问到. 如果时间不长, 也没有必要去担心它所占空间的回收.


## 代码解读

``Stripe64``属性:
* ``Cell[] cells``: Cell表, 尺寸是2的幂(便于通过``&``取余)
* ``volatile int cellsBusy``: "Cell表"的锁(乐观锁机制), 保证以下操作的线程安全:
  * 创建Cell表
  * 扩容Cell并迁移旧的Cell
  * 创建Cell并放到一个slot
* ``volatile long base``: 
  * 单线程时, 值会更新到base
  * 线程竞争建表资格失败时, 也会将值更新到这



``Stripe64``行为:
* ``longAccumulate()``: 
    该方法是``Stripe64``中比较核心的方法, 且比较难理解. 
    该方法是包可见的, 调用到该方法的主要有同包的``LongAdder``与``LongAccumulator``. 
    下面会结合``LongAdder#add(long x)``方法进行讲解:

    ```java
    /* Code From: java.util.concurrent.atomic.LongAdder#add(long x) */

    /**
     * 在此处, Stripe64#longAccumulate()方法主要有三个作用  <br/>
     * 1. 初始化Cell()表                                   <br/>
     * 2. 选择一个slot实例化当前线程的Cell实例               <br/>
     * 3. 重选Cell (或者继续cas原来的Cell)                  <br/>
     */
    public void add(long x) {
        Cell[] as; long b, v; int m; Cell a;

        if ((as = cells) != null ||             // 条件-1.1 Cell表已存在  -> 说明出现过竞争, 则不再操作base
                !casBase(b = base, b + x)       // 条件-1.2 cas(base)失败 -> 出现竞争, 不再操作base
        ) {
            boolean uncontended = true;
            if (as == null || (m = as.length - 1) < 0 ||         // 条件-2.1 Cell表未初始化 -> 通过longAccumulate()创建Cell表
                    (a = as[getProbe() & m]) == null ||          // 条件-2.2 Cell未创建 -> 通过longAccumulate()创建Cell
                    !(uncontended = a.cas(v = a.value, v + x)))  // 条件-2.3 Cell.cas()失败 -> 通过LongAccumulate()重选Cell(或者继续cas)
                longAccumulate(x, null, uncontended);
        }
    }
    ```
    可以推演一下多个线程竞争该方法的过程: 
    1. **T1: *Thr-1*独占``LongAdder``**
       * 当前只有一个线程*Thr-1*, 无竞争, 因此可以只更新``base``, 无须初始化Cell表. 只要一直没有新线程, 就能一直成功.
    2. **T2: *Thr-1*``casBase()``失败**
       * 说明出现了第二个线程*Thr-2*, 便不再简单更新``base``, 避免cas竞争的代价
       * 此时, 线程-1将会满足*条件-2.1*, 进入``longAccumulate()``方法, 第一次创建Cell表(长度为2)
       * *Thr-1*创建Cell之前, 会先初始化线程探针值, 以此决定slot的位置.
    3. **T3: *Thr-2*再调用``longAccumulate()``方法**
       * *Thr-2*发现Cell表已存在, 执行``a=as[getProbe()&m]``判断有没与其它线程竞争同一个Cell
       * 如果两个线程的Cell一开始就不同, 并且没有新线程加入, 则可以一直独占自己的Cell (因为探针值不会更新)
    4. **T4: 任一线程``Cell.cas()``失败 (即满足*条件-2.3*)**
       * 造成这一现象的有两个可能的原因: 1. 两个线程定位到了同一个slot; 2. 出现了第3个线程
       * 此时, ``uncontended``被设为``false``, 进入``longAccumulate()``方法, 决定是另选一个Cell还是继续与其它线程竞争同一个Cell  
    <br/>

    主分支梳理:
    ```java
    final void longAccumulate(long x, LongBinaryOperator fn,
                              boolean wasUncontended) {

        // ...

        for (;;) {
            Cell[] as; Cell a; int n; long v;

            /* 1. Cell表已创建 -> 处理Cell的创建 */
            if ((as = cells) != null && (n = as.length) > 0) {
                // ...
            }
            /* 2. Cell表未创建 -> CAS抢Cell表锁 (双检查锁) - 尝试创建Cell表 */
            else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
                // ...
            }
            /* 3. 未抢到创建Cell表的资格 -> 循环对base做cas */
            else if (casBase(v = base, ((fn == null) ? v + x :
                                        fn.applyAsLong(v, x))))
                break;                          // Fall back on using base
        }
    }
    ```

    双检查锁创建Cell表的过程:
    ```java
    final void longAccumulate(long x, LongBinaryOperator fn,
                              boolean wasUncontended) {

        // ...

        for (;;) {

            // ...
            // 先第1次检查, 然后尝试通过cas抢锁
            else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
                boolean init = false;
                try {
                    // 上锁成功以后还得重新判断前述条件, 防止cas的ABA问题
                    if (cells == as) {
                        // Cell表创建好后, 创建Cell一定是安全的
                        Cell[] rs = new Cell[2];
                        rs[h & 1] = new Cell(x);
                        cells = rs;
                        init = true;
                    }
                } finally {
                    // 成功/失败都要释放锁
                    cellsBusy = 0;
                }
                // 创建成功则返回
                if (init)
                    break;
            }
            // ...
        }
    }
    ```

    双检查锁尝试创建Cell:
    ```java
    /* Code From: java.util.concurrent.atomic.Striped64#longAccumulate() */

    final void longAccumulate(long x, LongBinaryOperator fn,
                              boolean wasUncontended) {
        
        boolean collide = false;
        for (;;) {
            Cell[] as; Cell a; int n; long v;

            /* Cell表已经存在 */
            if ((as = cells) != null && (n = as.length) > 0) { // 顺便获取Cell表引用与长度

                /* Cell未创建 -> 尝试创建Cell */
                if ((a = as[(n - 1) & h]) == null) {    // 计算Cell索引, 定位slot

                    // 确定Cell锁未上
                    if (cellsBusy == 0) {
                        Cell r = new Cell(x);

                        /* CAS抢Cell表锁 (双检查锁) - 创建Cell表 */
                        if (cellsBusy == 0 && casCellsBusy()) {

                            boolean created = false;
                            try {               
                                Cell[] rs; int m, j;

                                // 上锁成功以后还得重新判断前述条件, 防止cas的ABA问题
                                if ((rs = cells) != null &&
                                    (m = rs.length) > 0 &&
                                    rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    created = true;
                                }
                            } finally {
                                cellsBusy = 0;  // 成功失败都得释放锁
                            }
                            
                            if (created)        // 成功创建了Cell(Cell里面已经存了新值), 可以退出
                                break;
                            
                            continue;           // 否则接着尝试
                        }
                    }
                    // Cell表被锁, 说明另一个线程正在处理(可能会换Cell表)
                    //      如果另一个线程在做扩容, 扩完后暂不确定下一个slot的位置
                    //      因此先把"collide"标志重置了, 假设无竞争
                    collide = false;
                }
                // ...
            }
            // ...
        }
    }
    ```
    Cell表和Cell皆已存在:
    ```java
    final void longAccumulate(long x, LongBinaryOperator fn,
                              boolean wasUncontended) {

        // 获取线程探针值作为初始hash
        int h;
        if ((h = getProbe()) == 0) {
            ThreadLocalRandom.current(); // 初始化探针值
            h = getProbe();

            // 由于探针值已更新, 不确定下一步会选中哪个slot, 因此先假设不会有竞争
            wasUncontended = true;      
        }
        
        boolean collide = false;
        for (;;) {
            Cell[] as; Cell a; int n; long v;

            /* Cell表已存在 */
            if ((as = cells) != null && (n = as.length) > 0) { // 顺便获取Cell表引用与长度

                // ...
                // Cell已存在

                /* 进入方法之前就知道有发生过Cell.cas()失败 */
                else if (!wasUncontended) 
                    // 第一次rehash, 因为要重选slot, 因此先假设不会有竞争
                    // 但该标志是"一次性"的, 下次遇到相同情况会跳过该分支
                    wasUncontended = true;  

                /* Cell.cas()尝试更新值, 成功则返回 */
                else if (a.cas(v = a.value, ((fn == null) ? v + x : fn.applyAsLong(v, x))))
                    break;
                /* 再次Cell.cas()失败 */
                else if (n >= NCPU || cells != as) // 先检查扩容条件
                    // 如不满足扩容条件, 则只能重置"collide"标志, 后面继续Cell.cas()
                    collide = false;   
                
                /* Cell.cas()失败 && 满足扩容条件 */
                else if (!collide)
                    // 第二次rehash, 因为要重选slot, 因此先假设不会有竞争
                    // 但该标志是"一次性"的, 下次遇到相同情况会跳过该分支
                    collide = true;

                /* CAS抢Cell表锁 (双检查锁) - 尝试扩容Cell表 */
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        // 重检查, 防止cas的ABA问题
                        if (cells == as) {  
                            Cell[] rs = new Cell[n << 1];
                            for (int i = 0; i < n; ++i)
                                rs[i] = as[i];
                            cells = rs;
                        }
                    } finally {
                        // 成功/失败都要释放锁
                        cellsBusy = 0;      
                    }
                    
                    // Cell表容量发生了变化, slot位置可能发生了变化, 因此清除"collide"标志, 再继续循环 
                    collide = false;        
                    continue;                   
                }

                h = advanceProbe(h); // 发生 !wasUncontended 或者 collide时, 都会跳到这里来更新探针值, 做rehash
            }
            // ...
        }
    }
    ```
    在这段代码中, 比较令人费解的是``wasUncontended``与``collide``两个标志, 这两个标志的作用比较类似, 都是"**一次性**"的标志, 用来记录线程冲突的升级:  
    * ``unUncontented`` (翻译为"无发生竞争的")
      * 该标志是在进入``longAccumulate()``方法之前确定的, ``unUncontented==false``表示已知发生过``Cell.cas()``失败
      * 在循环中, 如果``unUncontented==false``, 该标志会先被翻转, 然后线程更新其探针值, 做第一次rehash, 尝试另选一个空的slot, 避开原来冲突的slot
      * 如果第一次rehash后选中的slot还是存在其它线程创建的Cell, 就只能接受与其它线程共用Cell
    * ``collide`` (翻译为"碰撞")
      * 线程与其它线程共用Cell以后, 如果发生``Cell.cas``失败, 则会清除该标志位, 然后更新线程探针值, 做第二次rehash, 再次尝试避开冲突的slot
      * 如果还是``Cell.cas``失败, 就只能尝试扩容Cell表, 并且期望后面重新选择的slot没有其它线程占用 (``collide``标志再被清除掉)


## 整体解读
```java
/* Code From: java.util.concurrent.atomic.Striped64#longAccumulate() */

final void longAccumulate(long x, LongBinaryOperator fn,
                            boolean wasUncontended) {

    // 获取线程探针值作为初始hash
    int h;
    if ((h = getProbe()) == 0) {
        ThreadLocalRandom.current(); // 初始化探针值
        h = getProbe();

        // 由于探针值已更新, 不确定下一步会选中哪个slot, 因此先假设不会有竞争
        wasUncontended = true;      
    }
    
    boolean collide = false;
    for (;;) {
        Cell[] as; Cell a; int n; long v;

        /* Cell表已创建 -> 决定是否创建Cell */
        if ((as = cells) != null && (n = as.length) > 0) { // 顺便获取Cell表引用与长度

            /* Cell未创建 -> 尝试创建Cell */
            if ((a = as[(n - 1) & h]) == null) {    // 计算Cell索引, 定位slot

                // 确定Cell锁未上
                if (cellsBusy == 0) {
                    Cell r = new Cell(x);

                    /* CAS抢Cell表锁 (双检查锁) - 创建Cell表 */
                    if (cellsBusy == 0 && casCellsBusy()) {

                        boolean created = false;
                        try {               
                            Cell[] rs; int m, j;

                            // 上锁成功以后还得重新判断前述条件, 防止cas的ABA问题
                            if ((rs = cells) != null &&
                                (m = rs.length) > 0 &&
                                rs[j = (m - 1) & h] == null) {
                                rs[j] = r;
                                created = true;
                            }
                        } finally {
                            cellsBusy = 0;  // 成功失败都得释放锁
                        }
                        
                        if (created)        // 成功创建了Cell(Cell里面已经存了新值), 可以退出
                            break;
                        
                        continue;           // 否则接着尝试
                    }
                }
                // Cell表被锁, 说明另一个线程正在处理(可能会换Cell表)
                //      如果另一个线程在做扩容, 扩完后暂不确定下一个slot的位置
                //      因此先把"collide"标志重置了, 假设无竞争
                collide = false;
            }
            
            /* 进入方法之前就知道有发生过Cell.cas()失败 */
            else if (!wasUncontended) 
                // 第一次rehash, 因为要重选slot, 因此先假设不会有竞争
                // 但该标志是"一次性"的, 下次遇到相同情况会跳过该分支
                wasUncontended = true;  

            /* Cell.cas()尝试更新值, 成功则返回 */
            else if (a.cas(v = a.value, ((fn == null) ? v + x : fn.applyAsLong(v, x))))
                break;
            /* 再次Cell.cas()失败 */
            else if (n >= NCPU || cells != as) // 先检查扩容条件
                // 如不满足扩容条件, 则只能重置"collide"标志, 后面继续Cell.cas()
                collide = false;   
            
            /* Cell.cas()失败 && 满足扩容条件 */
            else if (!collide)
                // 第二次rehash, 因为要重选slot, 因此先假设不会有竞争
                // 但该标志是"一次性"的, 下次遇到相同情况会跳过该分支
                collide = true;

            /* CAS抢Cell表锁 (双检查锁) - 尝试扩容Cell表 */
            else if (cellsBusy == 0 && casCellsBusy()) {
                try {
                    // 重检查, 防止cas的ABA问题
                    if (cells == as) {  
                        Cell[] rs = new Cell[n << 1];
                        for (int i = 0; i < n; ++i)
                            rs[i] = as[i];
                        cells = rs;
                    }
                } finally {
                    // 成功/失败都要释放锁
                    cellsBusy = 0;      
                }
                
                // Cell表容量发生了变化, slot位置可能发生了变化, 因此清除"collide"标志, 再继续循环 
                collide = false;        
                continue;                   
            }

            h = advanceProbe(h); // 发生 !wasUncontended 或者 collide时, 都会跳到这里来更新探针值, 做rehash
        }

        /* Cell表未创建 -> CAS抢Cell表锁 (双检查锁) - 尝试创建Cell表 */
        else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
            boolean init = false;
            try {
                // 上锁成功以后还得重新判断前述条件, 防止cas的ABA问题
                if (cells == as) {
                    Cell[] rs = new Cell[2];
                    rs[h & 1] = new Cell(x);
                    cells = rs;
                    init = true;
                }
            } finally {
                // 成功/失败都要释放锁
                cellsBusy = 0;
            }
            // 创建成功则返回
            if (init)
                break;
        }

        /* 未抢到创建Cell表的资格 -> 循环对base做cas */
        else if (casBase(v = base, ((fn == null) ? v + x :
                                    fn.applyAsLong(v, x))))
            break;
    }
}
```

