ReentrantLock
* 行为:
    * void lock()
    * void lockInterruptibly();
    * boolean tryLock(long, TimeUnit);
    * boolean tryLock();
* nonfaiTryAcquire(int acquires)
    ```java
    final boolean nonfairTryAcquire(int acquires) {
        final Thread current = Thread.currentThread();              
        int c = getState();
        if (c == 0) {
            if (compareAndSetState(0, acquires)) {                  // CAS尝试将state+1
                setExclusiveOwnerThread(current);                   // 成功, 将排他锁持有者设为自己
                return true;
            }
        }
        else if (current == getExclusiveOwnerThread()) {            // 占用者为自己
            int nextc = c + acquires;                           
            if (nextc < 0) // overflow                         
                throw new Error("Maximum lock count exceeded");
            setState(nextc);                                    
            return true;
        }
        return false;
    }
    ```
* protected final boolean tryAcquire(int acquires) 
    ```java
    protected final boolean tryAcquire(int acquires) {
        final Thread current = Thread.currentThread();
        int c = getState();
        if (c == 0) {
            if (!hasQueuedPredecessors() &&                         // 判断有没其它线程在等待, 防止插队
                compareAndSetState(0, acquires)) {
                setExclusiveOwnerThread(current);
                return true;
            }
        }
        else if (current == getExclusiveOwnerThread()) {
            int nextc = c + acquires;
            if (nextc < 0)
                throw new Error("Maximum lock count exceeded");
            setState(nextc);
            return true;
        }
        return false;
    }
    ```
* protected final boolean tryRelease(int releases) 
    ```java
    protected final boolean tryRelease(int releases) {
        int c = getState() - releases;
        if (Thread.currentThread() != getExclusiveOwnerThread())  // 占有者不是自己时, 非法
            throw new IllegalMonitorStateException();       
        boolean free = false;
        if (c == 0) {
            free = true;
            setExclusiveOwnerThread(null);                        // 下个state为0时, 没有人占用锁
        }
        setState(c);                                                
        return free;
    }
    ```

CountDownLatch
* 特点: 可中断, 可计时
* countDown: 循环CAS将state减1, state为0时不做操作
    ```java
        public void countDown() {
            sync.releaseShared(1);
        }

        public final boolean releaseShared(int arg) {
            if (tryReleaseShared(arg)) {
                doReleaseShared();
                return true;
            }
            return false;
        }

        protected boolean tryReleaseShared(int releases) {
            for (;;) {
                int c = getState();
                if (c == 0)                                 // state为0时不能释放
                    return false;
                int nextc = c-1;
                if (compareAndSetState(c, nextc))           // 通过循环CAS将state计数减1
                    return nextc == 0;
            }
        }
    ```  
* await
    ```java
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);                 // 可中断
    }

    protected int tryAcquireShared(int acquires) {
        return (getState() == 0) ? 1 : -1;                  // state被减到0时才能释放
    }

    ```

CyclicBarrier
* 特点:
    * 一个中断/异常, 其它都broken
    * reset后或者barrierCommand异常时, 所有线程broken
    * barrierCommand由最后一个await()的线程执行
    * 多个线程同时被中断时, 只有最后抢到锁的线程能抛出中断, 其它非中断线程broken, 而中断的线程broken+interrupted
*   源代码
    ```java

    private void breakBarrier(){
        generation.broken = true;   // broken符号置位
        count = parties;            // count 重置
        trip.signalAll();           // 通知所有阻塞的线程
    }

    // 每一个周期都属于一个generation(generation 只有一个broken flag)
    private void nextGeneration() {
        
        trip.signalAll();               // 换代时要先唤醒await中的线程
        count = parties;                // count 重置
        generation = new Generation();  // 换代
    }

    private int dowait(boolean timed, long nanos)
        throws InterruptedException, BrokenBarrierException,
               TimeoutException {
        // 上锁
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {

            // 取得当前年代
            final Generation g = generation;

            // 如果年代已经broken, 抛异常         
            if (g.broken)
                throw new BrokenBarrierException();

            // 如果发生中断, 抛中断异常 (可能会触发其它线程的BrokenBarrierException) 
            if (Thread.interrupted()) {
                breakBarrier();
                throw new InterruptedException();
            }

            int index = --count;
            // 如果全部线程已经到达
            if (index == 0) {  
                boolean ranAction = false;
                try {
                    // 如果有barrierCommand, 执行barrierCommand
                    final Runnable command = barrierCommand;
                    if (command != null)
                        command.run();
                    ranAction = true;

                    // 换代(换代时会signalAll())
                    nextGeneration();
                    return 0;
                } finally {
                    // 如果barrierCommand发生了异常
                    if (!ranAction)
                        breakBarrier();
                }
            }

            // 循环阻塞
            for (;;) {

                // 阻塞或计时阻塞
                try {
                    if (!timed)
                        trip.await();
                    else if (nanos > 0L)
                        nanos = trip.awaitNanos(nanos);
                } catch (InterruptedException ie) {

                    // 发生中断时, 如果没换代, 抛出中断异常
                    if (g == generation && ! g.broken) {
                        breakBarrier();
                        throw ie;
                    } else {
                        // 已经发生换代, 不能抛中断异常(中断异常留给下一代), 只能给线程的中断标志置位 (不能影响新的年代)
                        // 这种情况发生在两个线程同时interrupted, 其中一个先完成nextGeneration()的情况 
                        Thread.currentThread().interrupt();
                    }
                }

                // 检查broken
                if (g.broken)
                    throw new BrokenBarrierException();

                // 已经换代, 直接退出
                if (g != generation)
                    return index;

                // 如果超时, 抛出超时异常
                if (timed && nanos <= 0L) {
                    breakBarrier();
                    throw new TimeoutException();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public int getNumberWaiting() {
        // final 局部变量引用域的意义:
        // 1. lock是局部变量, 赋值操作将域从堆复制到栈中(局部变量表), 提高访问速度
        // 2. 利用final的happens-before语义禁止指令重排序, 防止在多线程的情况下, this.lock还没完成初始化, 其引用就赋给了lock;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return parties - count;
        } finally {
            lock.unlock();
        }
    }
    ```

Semaphore
* 行为:
    * void acquire()/acquire(int): 可中断, 阻塞尝试 (共享型)
    * void acquireUninterruptibly()/acquireUninterruptibly(int): 不可中断, 阻塞尝试 (共享型)
    * void tryAcquire()/tryAcquire(int):    非阻塞尝试
    * void tryAcquire(long, TimeUnit)/tryAcquire(int, long, TimeUnit) 可中断, 阻塞尝试, 可超时
* 特点:
    * 一个线程acquire次数超过Semaphore的state数时, 会死锁
* 源代码
    ```java
    final int nonfairTryAcquireShared(int acquires) {
        for (;;) {
            int available = getState();
            int remaining = available - acquires;
            if (remaining < 0 ||
                compareAndSetState(available, remaining))       // 循环CAS尝试将状态-1
                return remaining;
        }
    }
    protected final boolean tryReleaseShared(int releases) {
        for (;;) {
            int current = getState();
            int next = current + releases;
            if (next < current) // overflow
                throw new Error("Maximum permit count exceeded");
            if (compareAndSetState(current, next))          // 循环CAS尝试将状态+1
                return true;
        }
    }

    /**
     *  Fair
    */
    protected int tryAcquireShared(int acquires) {
        for (;;) {
            if (hasQueuedPredecessors())                    // 判断队列中有没有线程在等待, 防止插队
                return -1;
            int available = getState();
            int remaining = available - acquires;
            if (remaining < 0 ||
                compareAndSetState(available, remaining))
                return remaining;
        }
    }
    ```

Exchanger
* 行为特点:
    * exchange(): 中断发生时抛出中断, 并复位中断标志
    * actions prior to the exchange() in each thread happen-before those subsequent to a return from the corresponding exchange()
* 