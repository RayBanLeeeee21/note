## 5.1 Lock接口

Lock接口:
* 方法:
    1. void Lock()  
    2. void LockInterruptibly() throws InterruptedException
    3. boolean tryLock(long time, TimeUnit unit) throws InterruptedException
    4. boolean tryLock()
    5. void unlock()
    6. Condition newCondition()
* 特性:
    * 方法1: 阻塞
    * 方法2: 阻塞 + 可中断
    * 方法3: 阻塞 + 可中断 + 可超时
    * 方法4: 非阻塞

## 5.2 队列同步器

AbstractQueuedSynchronizer
* 设计模式: 模板方法模式:
    * 重写方法: 全部**非阻塞**
        1. boolean tryAcquire(int arg)
        2. boolean tryRelease(int arg)
            * 返回值定义: true: 成功; false: 失败
        3. int tryAcquireShared(int arg)
        4. int tryAcquireShared(int arg)
            * 返回值定义: \<0: 失败
        5. boolean isHeldExclusively()
    * 模板方法:
        1. **void acquire(int arg)**
        2. **void acquireInterruptibly(int arg)**  throws InterruptedException
        3. **boolean tryAcquireNano(int arg, long nanos)**  throws InterruptedException
        4. **void acquireShared(int arg)**
        5. void acquireSharedInterruptibly(int arg)  throws InterruptedException
        6. boolean tryAcquireSharedNano(int arg, long nanos)  throws InterruptedException
        7. boolean release(int arg);
        8. boolean releaseShared(int arg);
        9. Collection<Thread>getQueuedThreads();
* 算法:
    * void acquire(int arg):
        ```python
        1   if tryAcquire成功 return;
        2   循环CAS尝试结点node加入队尾    // node为保存当前线程的结点
        3   failed := true
        4   try
        5       while(true)
        6           if p为头结点 && tryAcquire成功
        7               删除p
        8               failed := false
        9               return interrupted 
        10          if 应该被阻塞           // 只有前续结点为signal, 才有可能在阻塞后被唤醒 
        11             阻塞
        12             interrupted: = currentThread.interrupted()   //此时阻塞结束
        13  finally
        14      if failed
        15          取消Acquire 
        ```    
    * void release(int arg)
        ```python
        1   if tryRelease成功
        2       从队列中找到第一个可以被唤醒的结点(非CANCELLED)并唤醒
        3       return true;
        4   return false;
        ```
    * void acquireInterruptibly(int arg): 与acquire类似
        * 与acquire区别:
            * 返回中断状态改为**抛中断异常**
    * void tryAcquireNanos(int arg): 与acquire类似
        * 与acquire区别:
            * 等待/通知机制改成等待/超时机制
                * 等待时间过短时不阻塞, 自旋等待
            * 返回中断状态改为**抛中断异常**
    * void acquireInterruptibly(int arg): 与acquire类似, 区别在于返回中断状态改为**抛中断异常**
    * void acquireShared(int arg): 

ReentrantLock
* 算法:
    * 非公平锁的boolean tryAcquire(int arg)
        ```python
        1   state := getState()
        2   if state == 0
        3       if CAS尝试更新state成功
        4           将锁的占有线程改为当前线程
        5           return true
        6   else
        7       if 锁的占有线程为当前线程
        8           setState(state+1)
        9           return true;
        10      else
        11          return false
        ```
    * 公平锁的boolean tryAcquire(int arg)
        ```python
        1   state := getState()
        2   if state == 0
        3       if 队列中没有等待的线程 && CAS尝试更新state成功 
        4           将锁的占有线程改为当前线程
        5           return true
        6   else
        7       if 锁的占有线程为当前线程
        8           setState(state+1)
        9           return true;
        10      else
        11          return false
        ```
    * 
* 公平锁原理:
    * 上一个线程操作lock.unlock()时, 会将队列中下一个线程从等待队列移到阻塞队列
    * 此时如果另一个新线程操作lock.lock(), 那这个新线程会与阻塞队列中即将进入RUNNABLE状态的线程竞争
    * 公平锁的条件会使排在队列前面的线程优先于半路加入的新线程, **防止插队**
