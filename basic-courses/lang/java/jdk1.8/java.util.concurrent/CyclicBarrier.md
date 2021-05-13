## CyclicBarrier



CyclicBarrier
* 特点:
    * 一个中断/异常, 其它都broken
    * reset后或者barrierCommand异常时, 所有线程broken
    * barrierCommand由最后一个await()的线程执行
    * 多个线程同时被中断时, 只有最后抢到锁的线程能抛出中断, 其它非中断线程broken, 而中断的线程broken+interrupted
* 与CountDownLatch相比
    * CyclicBarrier可重复使用
    * 到达以后可以调用command.run()
*   源代码
    ```java

    /**
        将generation设为broken, 并通知所有等待线程, 重置count
            由于breakBarrier只在上锁的情况下被调用, 因此三个语句的顺序并不重要
    */
    private void breakBarrier(){
        generation.broken = true;   // broken符号置位
        count = parties;            // count 重置
        trip.signalAll();           // 通知所有阻塞的线程
    }

    /**
        将generation替换掉, 并通知所有等待线程, 重置count
            由于nextGeneration只在上锁的情况下被调用, 因此三个语句的顺序并不重要
    */
    private void nextGeneration() {   
        trip.signalAll();               // 换代时要先唤醒await中的线程
        count = parties;                // count 重置
        generation = new Generation();  // 换代
    }

    public void reset() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            breakBarrier();   // break the current generation
            nextGeneration(); // start a new generation
        } finally {
            lock.unlock();
        }
    }

    /**
        循环阻塞之前
            检查broken
            检查中断
            检查到达情况, 到达则执行command.run(), 成功后通知其它线程已经换代
        循环中
            检查中断
            检查broken
            检查超时
    */
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
            // 清除中断标志
            if (Thread.interrupted()) {
                breakBarrier();
                throw new InterruptedException();
            }

            // 更新count
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

                    // 只有在完全成功的时候才会换代
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
                } catch (InterruptedException ie) {         //检查中断

                    // 发生中断时, 如果没换代, 而且没有broken
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

