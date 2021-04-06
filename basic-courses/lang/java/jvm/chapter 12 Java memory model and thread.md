## 12.1 概述
* TPS(Transaction Per Second): 衡量性能的指标
## 12.2 硬件的效率与一致性
* **缓存一致性**
* **乱序执行优化**
* **指令重排序优化**

## 12.3 Java内存模型
* Java内存模型(JMM)

### 12.3.1 主内存与工作内存
内存模型:
* Java线程 <-> 工作内存 <-> save和load操作 <-> 主内存

### 12.3.2 内存间交互操作
8个原子操作:
* 作用于主内存
    * lock: 将一个变量标识为线程独占
    * unlock: 
    * read: 从内存将变量值读到工作内存
* 作用于工作内存
    * load: 将变量值赋给变量副本
    * use: 将变量值传给执行引擎
    * assign: 将执行结果从执行引擎赋给变量副本
    * store: 将变量值从工作内存传到主内存
* 作用于主内存:
    * write: 将收到的变量值写回变量

规则:
* read, load, store, write
    1. read & load, store & write 顺序执行
        * // 可以非连续执行 read a, read b, load a, load b
    2. read & load, store & write 成对出现
    3. assign & 同步(store+write)必须成对
        * // assign结果不能丢弃, 同步不能无原因
    4. use/store 操作的对象副本必须经过初始化(load/assign)
        * // 一个变量必须来自内存或者经过初始化
* lock, unlock
    1. 一个变量一次只能被一个线程lock
    2. lock与unlock的次数必须相等
    3. 不能unlock另一个线程lock的变量
* lock与同步 
    1. lock一个变量会使已有变量副本无效
    2. unlock之前必须同步

### 12.3.3 volatile
* **内存屏障**: 指令重排序时不能把内存屏障以下的指令排到内存屏障以上

volatile语义:
* A线程修改变量, B线程立即可见
    * // e.g., inc问题
* 禁止指令重排序

volatile特殊规则:
* (read+load)必须与use关联使用
* assign 必须与同步(store, write)关联使用
* 对于同一线程T的不同(read+load+use)过程/(assign+store+write)过程A和B, A的(use/assign)先于B的(use/assign)时, A的(read/load)也先于B的(read/load)

不加锁volatile适用场景:
* 执行结果不依赖于变量当前值 || 单一线程
    * // 反例: inc实验
* 变量(作为状态变量), 不用与其它的状态变量参与不变约束
    * // 反例
        ```Java
        public void setInterval(Date newStart, Date newEnd) {  
            // thread2: start<end为true
            start = newStart;  
            // thread2: start<end可能为false
            end = newEnd;  
            // thread2: start<end为true
        ```

### 12.3.4 对于long和double的特殊规则
* **非原子协定**: jvm规范不需要对long, double的**load, store, read, write**实现原子操作
    * 但一般都会实现



## 12.4 线程实现

概念:
* ULT: 用户级线程. 线程调用由用户来完成
* LWP: 轻量级进程. KLT提供的接口, 连接ULT与KLT
* KLT: 内核级线程. 线程调度由操作系统来完成

实现:
1. 内核线程实现 (ULT:KLT = 1:1)
    * 一个进程有多个LWP
    * 优点: 
        * 调度由操作系统完成
        * 一个线程阻塞不会影响另一个线程
    * 缺点: 
        * 需要完成用户态和内核态的切换, 代价大
        * 系统提供的KLT资源有限
2. 用户线程实现 (ULT:KLT = n:1)
    * 一个进程有一个LWP
    * 优点: 不用用户态与内核态的切换, 代价小
    * 缺点: 
        * 程序自己实现线程管理(ULT创建, 调度, 同步, 销毁)
        * 同一个LWP对应的其中一个ULT阻塞时, 整个进程会被阻塞
3. 混合实现 (ULT:KLT = m:n)
    * 一个进程有多个LWP
    * 避免了一个ULT阻塞导致整个进程被阻塞

线程调度方式:
* 协同式线程调度: 切换对于线程可见, 在线程运行结束时切换
    * 缺点: 程序开发者自己负责切换, 容易造成阻塞而无法切换
* 抢占式线程调度: 切换对于线程不可见, 由系统进行切换
    * 线程可以让步, 但不能自己抢
    * 可以有优先级

Java实现:
* 线程实现: 未规定. Windows和Linux中为一对一
* 线程调度: 抢占式线程调度

Java线程状态
* NEW: 
    * 触发条件: new线程
* RUNNABLE: 
    * 触发条件: Thread.start()
* WAITING: 在获得锁后发现无法得到资源, 通过wait()等待并**释放锁**, 由其它线程notify
    * 触发条件:
        * Object.wait()
        * Thread.join()
        * LockSupport.park()
* TIME_WAITING: 
    * 触发条件:
        * Object.wait(long)
        * Thread.join(long)
        * Thread.sleep(long)        // **sleep不会释放锁**
        * LockSupport.parkNano()
        * LockSupport.parkUtil()
* BLOCKED:
    * 触发条件: 拿不到锁
* TERMINATED