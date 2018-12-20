## 模型
顺序一致性内存模型
* 特点:
    * 单一共享内存
    * 一次只有一个线程能访问内存
    * 所有操作**原子性**
    * 所有操作对其它线程立即可见
    * 每个线程内的线程都按顺序执行(不能重排序)

JMM模型
* 特点:
    * 线程 <-> 本地内存 <-> 共享内存
    * 不保证程序的执行具有顺序一致性 (顺序一致性由用户负责)
    * 不保证单线程的所有操作按顺序执行, 只遵守as-if-serial语义
    * 不保证线程看到一致的操作顺序 (不保证操作对其它线程立即可见)
    * 不保证对64位的long, double有原子性操作
* 内存屏障:
    * LoadStore
    * StoreStore
    * StoreLoad
    * LoadLoad

## 概念/定义
概念/定义:
* 重排序:
    * 分类:
        * 编译级优化的重排序 (编译器重排序): 对程序语句的重排序  
        * 指令级优化的重排序 (处理器重排序): 指令重排序, 并行化
        * 内存级优化的重排序 (处理器重排序): 内存/缓存的读/写操作重排序
    * 重排序禁止:
        * 编译器重排序: 由JMM的规范来禁止
        * 处理器重排序: JMM通过加入**内存屏障**来实现禁止
            * as-if-serial: 单线程的执行结果必须与顺序执行的结果一致, **编译器**和**处理器**都要遵守
            * 数据依赖性: 有数据依赖性的操作不能被重排序
            * 无数据依赖性的Store-Load一般可以被重排序
* 重排序缓冲: 预先计算结果, 操作时直接赋值 
* **顺序一致性**: 如果程序正确同步, 则程序执行结果与顺序一致性内存模型中的执行结果一致 

happens-before关系定义:
* 对程序员: A happens-before B, 表示A操作对B可见, 而A操作按顺序排在B操作之前
* 对编译器与处理器: A happens-before B, 则A操作不一定要在B操作之前执行, 只要保证执行结果与重排序之前一致, 那重排序就合法
* // 类似于一个多线程版的as-if-serial

happens-before规则:
* 程序顺序规则: 一个线程内的每个操作, happens-before于该线程中任意后续操作. 通过编译器和处理器遵守**as-if-serials语义**保证
* 监视器锁规则: 一个锁的unlock操作happens-before于后续的lock操作
* volatile规则: 一个volatile变量的写操作, happens-before于后续的任意volatile变量读操作
* start()规则: t1 调用t2.start(), 那t2.start()操作, happens-before于t2线程的任意操作
* join()规则: t1 调用t2.join()规则, t2线程的任意操作都happens-before于t2.join()操作成功返回
* 传递性: A happens-before B, B happens-before C, 则 A happens-before C

## 操作语义
内存屏障: 
* JMM语义:
    ```
    a=b; c=d;
    //-----------------------编译后----------------------
    1 load b; 
    2 store a; 
    3 load d; 
    4 store c;
    // -------------------------------------------------
    LoadLoad   保证 1 happens-before 3
    StoreLoad  保证 2 happens-before 3
    LoadStore  保证 1 happens-before 4
    StoreStore 保证 2 happens-before 4
    可看出StoreLoad最严格
    ```
* 硬件实现
    * StoreLoad: e.g., 操作后加Lock指令锁缓存行, Lock指令强迫数据刷新到内存, 使其它处理器缓存行无效化


volatile
* 特性:
    * 原子性: volatile变量的读写有原子性        //其它操作不一定
    * 可见性: 读volatile变量一定是最新的
* JMM语义:
    * 读: 读一个volatile变量时, 本地内存设为无效, 直接从共享内存中读取
    * 写: 写一个volatile变量时, JMM会将变量刷新到内存中
* 规则及其保守实现:
    | 规则 | 实现 | 语境 |
    |:-:|:-:|:-:|
    |1. v读     <-hb->  任意操作 | v读; LoadLoad, LoadStore |if(flag) A; |
    |2. 任意操作 <-hb->  v写     | StoreStore; v写          |B; set(flag);|
    |3. v写     <-hb->  v读      | v写; StoreLoad; v读      |             |
    * e.g., x86只支持无数据依赖的StoreLoad重排序, 此时JMM只需要考虑StoreLoad屏障的实现


锁
* JMM语义:
    * 释放: A释放一个锁时, 实质是A向将要获得锁的线程发出消息 (消息立即对其它线程可见)
    * 获取: B获取一个锁时, 实质上是获得了A释放了锁的消息
* 实现:
    * ReentrantLock: 用volatile变量表示加锁层数, 配合CAS机制实现 (因为volatile除读写外无原子性, 原子性通过CAS机制实现)
* CAS:
    * Java的CAS同时具有volatile读和volatile写的内存语义 (先读后写, 前后指令都不能被重排)
* volatile+CAS同步模式:
    * 声明共享变量为volatile
    * CAS机制原子操作共享变量
    * volatile与CAS的JMM内存语义保证顺序一致性

final
* 先验: 非静态final域只能在构造块/构造函数/定义域时被赋值
* JMM语义:
    1. 在构造函数内对final域的写不能被重排序到后续将对象实例写入对象引用之后
    2. 对对象实例的读入不能被重排序到后续读该对象的final域操作之后
* 实现:
    1. StoreStore屏障
    2. LoadLoad屏障
* 思想:
    1. 如果final域的写入被重排序到构造函数外, 则可能在读取final域时, **构造已经完成**, 但final域未被初始化
    2. 如果final域的读入被重排序到读取对象引用之前, 则可能在读取final域时, **构造并未完成**, final域未被赋值
