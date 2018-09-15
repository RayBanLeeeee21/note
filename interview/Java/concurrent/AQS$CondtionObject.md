ConditionObject
* 特点:
    * 该类为AbstractQueuedSynchronizer的**非静态**内部类, 可以访问外部类的资源
    * 中断时直接返回的方法
        * void await() throws InterruptedException
        * long awaitNanos(long nanosTimeout) throws InterruptedException
        * boolean awaitUntil(Date deadline) throws InterruptedException
        * boolean await(long time, TimeUnit unit) throws InterruptedException
    * 中断时不直接返回的方法
        * final void awaitUninterruptibly() 
* void awaitUninterruptibly()
    ```java
    public final void awaitUninterruptibly() {
        // 加到队尾, 顺便清理一下CANCELLED结点
        Node node = addConditionWaiter();              

        // 尝试释放锁, 可能会抛出异常(非当前线程占有锁)
        int savedState = fullyRelease(node);            

        // 循环阻塞直到自己被加入同步队列        
        boolean interrupted = false;                            
        while (!isOnSyncQueue(node)) {                          
            LockSupport.park(this);                             
            if (Thread.interrupted())                 // 被唤醒后检查中断情况(并复位中断标志)
                interrupted = true;     
        }
        if (acquireQueued(node, savedState)           // [1] 已进入同步队列(state数还原为savedState), 要继续等到自己获得锁
            || interrupted)                                     // 并检查中断 
            selfInterrupt();
    }
    // [1] interrupted 为 false时, 可能在同步队列中获取锁的过程中并不会发生中断, 所以要双重检查

    /**
        加入新结点之前先检查一下有没CANCELLED结点, 有就先清理一下
    */
    private Node addConditionWaiter() {
        Node t = lastWaiter;
        
        // 最后一个结点不为Node.CONDITION时, 先清理
        if (t != null && t.waitStatus != Node.CONDITION) {              
            unlinkCancelledWaiters();
            t = lastWaiter;
        }

        // 新建结点, 加入队列尾部, 重设firstWaiter和lastWaiter
        Node node = new Node(Thread.currentThread(), Node.CONDITION);   
        if (t == null)
            firstWaiter = node;                                         
        else
            t.nextWaiter = node;                                        
        lastWaiter = node;                                              
        return node;
    }
    

    /**
        从第一个结点开始遍历, 删除所有不为Node.CONDITION的结点
    */
    private void unlinkCancelledWaiters() {
        Node t = firstWaiter;
        Node trail = null;
        while (t != null) {
            Node next = t.nextWaiter;
            if (t.waitStatus != Node.CONDITION) {
                t.nextWaiter = null;
                if (trail == null)
                    firstWaiter = next;
                else
                    trail.nextWaiter = next;
                if (next == null)
                    lastWaiter = trail;
            }
            else
                trail = t;
            t = next;
        }
    }

    /**
        尝试将state减为0
    */
    final int fullyRelease(Node node) {
        boolean failed = true;
        try {
            int savedState = getState();  
            // [1] 尝试直接将state减为0                
            if (release(savedState)) {                      
                failed = false;
                return savedState;
            } else {
                throw new IllegalMonitorStateException();
            }
        } finally {
            // [2] 异常或failed时都将Node取消, 此时该结点已经进入到等待队列了
            if (failed)                                     
                node.waitStatus = Node.CANCELLED;           
        }   
    }
    // [1] 在ReentrantLock的tryRelease()实现中, 如果不是当前线程占用锁, 会抛出IllegalMonitorStateException, 直接到final块
    // [2] 异常时不会有返回值


    final boolean isOnSyncQueue(Node node) {
        // 条件1, 如果状态没变成signal或者没有prev结点
        if (node.waitStatus == Node.CONDITION || node.prev == null)         
            return false;

        // 条件2, 有next结点, 说明在已在队列
        if (node.next != null)                                              
            return true;

        // node.prev不为null, 可能是enq方法中CAS加入队尾失败,
        // 但node很有可能在tail结点附近, 所以从tail开始找
        // 除非再一次CAS失败, 只能再阻塞
        return findNodeFromTail(node);
    }

    /**
    *   从tail开始向前遍历同步队列查找node结点
    */
    private boolean findNodeFromTail(Node node) {
        Node t = tail;
        for (;;) {
            if (t == node)
                return true;
            if (t == null)
                return false;
            t = t.prev;
        }
    }

    ```
* void signal()
    ```java
    public final void signal() {
        // 检查对锁的持有
        if (!isHeldExclusively())                                    
            throw new IllegalMonitorStateException();

        // 检查null
        Node first = firstWaiter;
        if (first != null)                                           
            doSignal(first);
    }

    /**
        从first开始, 逐个transferForSignal, 处理完一个删掉一个, 直到成功一个结点或者全部丢弃
    */
    private void doSignal(Node first) {
        // 每次从等待队列取一个结点
        do {
            if ( (firstWaiter = first.nextWaiter) == null)           
                lastWaiter = null;
            first.nextWaiter = null;                          
        } while (!transferForSignal(first) &&                // 成功唤醒一个结点时退出
                    (first = firstWaiter) != null);          // 或遍历到等待队列的结尾时退出
    }

    final boolean transferForSignal(Node node) {

        // [1] 如果node的状态不是CONDITION, 那就是CANCELLED结点, 返回false表示跳过该结点
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))       
            return false;                                           

        // 循环CAS将node加入同步队列, 得到node的pre结点    
        Node p = enq(node);                                          
        int ws = p.waitStatus;                                          
        if (ws > 0                                                   // 检查上一个结点有没变成CANCELLED, 若有则唤醒node来清理
            || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))         // CAS竞争失败时, 唤醒结点后, 结点发现还会获得锁, 继续循环阻塞
            LockSupport.unpark(node.thread);
        return true;
    }
    // [1] 这个操作说明了同步队列中不可能有状态为CONDITION的结点

    ```
* void signalAll()
    ```java
    public final void signalAll() {
        if (!isHeldExclusively())
            throw new IllegalMonitorStateException();
        Node first = firstWaiter;
        if (first != null)
            doSignalAll(first);
    }

    /**
        从first开始, 逐个transferForSignal, 处理完一个删掉一个
    */
    private void doSignalAll(Node first) {
        lastWaiter = firstWaiter = null;
        do {
            Node next = first.nextWaiter;
            first.nextWaiter = null;
            transferForSignal(first);
            first = next;
        } while (first != null);
    }
    ```
* void await() 
    * 特点:
        * 中断发生时需要在同步队列中等待, 所以不一定能及时抛出中断 
        * 如果在signal()后被中断, 那不会抛异常, 但是中断符号会被置位
    ```java
    public final void await() throws InterruptedException {
        // 先检查中断
        if (Thread.interrupted())                                           
            throw new InterruptedException();                               
        
        // 加入队尾, 顺便清理CANCELLED结点
        Node node = addConditionWaiter();
        
        // 尝试释放全部state, 可能抛异常(非当前线程占有锁)
        int savedState = fullyRelease(node);

        // 循环阻塞, 直到被加入到同步队列, 同时要判断中断情况
        int interruptMode = 0;
        while (!isOnSyncQueue(node)) {
            LockSupport.park(this);
            if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)   // THROW_IE(1)中断发生在
                break;
        }

        // 上面的checkInterruptWhileWaiting()操作完成后, 结点已进入到同步队列
        // 在同步队列中获取锁时还要检查中断发生的情况, 此时interruptMode可能为0
        if (acquireQueued(node, savedState) && interruptMode != THROW_IE)   //[1]
            interruptMode = REINTERRUPT;

        // 后续不为null时, 清理CANCELLED结点
        if (node.nextWaiter != null) 
            unlinkCancelledWaiters();

        // 中断发生, 处理中断(抛出异常或置位)
        if (interruptMode != 0)
            reportInterruptAfterWait(interruptMode);
    }
    // [1] 由于node要获取同步队列, 所以interrupted发生后, await函数不一定能立即抛出中断


    /** 
        如果有发生中断, 将结点加入同步队列
        如果加入同步队列的操作在signal()之前, 那中断模式为THROW_IE, 否则为REINTERRUPT
    */
    private int checkInterruptWhileWaiting(Node node) {
        return Thread.interrupted() ?
            (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) : // THROW_IE:-1; REINTERRUPT:1
            0;
    }

    /**
        将结点加入同步队列, 并判断该操作在signal()之前(true)还是之后(false)
    */
    final boolean transferAfterCancelledWait(Node node) {
        // 循环CAS尝试加入等待队列
        if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
            enq(node);
            return true;
        }
        
        // 如果CAS尝试把状态从CONDITION设为0失败(没抢过signal信号), 则让步, 直到signal()将线程加入到同步队列
        // 这个时候的CANCELLED情况很少发生
        while (!isOnSyncQueue(node))
            Thread.yield();
        return false;
    }

    /**
        中断处理方法:
        中断模式为THROW_IE时, 抛中断
        中断模式为REINTERRUPT时, 只是置位中断符号
    */
    private void reportInterruptAfterWait(int interruptMode)
        throws InterruptedException {
        if (interruptMode == THROW_IE)
            throw new InterruptedException();
        else if (interruptMode == REINTERRUPT)
            selfInterrupt();
    }
    
    ```
* long awaitNanos(long nanosTimeout)
    *   特点: 
        *   可抛出中断异常或给中断标志置位, (异常不一定立即抛出)
        *   返回剩余时间, (如果线程在同步队列里中没有立即获取到锁, 那么会继续阻塞, 最后剩余时间可能为负数)
    *   源代码
        ```java
        public final long awaitNanos(long nanosTimeout)
                throws InterruptedException {
            // 先检查一次中断
            if (Thread.interrupted())
                throw new InterruptedException();

            // 加入等待队列队尾, 并顺便清理CANCELLED结点
            Node node = addConditionWaiter();

            // 尝试一次性释放所有state,  可能抛异常(非当前线程占有锁)
            int savedState = fullyRelease(node);

            // 与await()相比, 多了超时等待机制
            final long deadline = System.nanoTime() + nanosTimeout;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                
                // 超时时, 尝试将结点加入同步队列
                if (nanosTimeout <= 0L) {                         
                    // 判断signal()发生前后的结果被丢弃                
                    transferAfterCancelledWait(node);             
                    break;
                }

                // 剩余超时大于阈值时, 才阻塞, 否则自旋
                if (nanosTimeout >= spinForTimeoutThreshold)                   
                    LockSupport.parkNanos(this, nanosTimeout);                    

                // 被唤醒时, 检查有没中断, 有中断则根据signal()发生情况来确定中断模式
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)   
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }

            // 在同步队列时, 再次检查中断
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;

            // 清理CANCELLED结点
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            
            //处理中断
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);

            // 返回剩余时间
            return deadline - System.nanoTime();
        }
        ```   
* boolean await(long time, TimeUnit unit)  
    * 特点:
        * 可抛中断异常(在signal()之前中断)或者置位中断符号(在signal()之后中断)
        * 执行后的现象与对应状态(有些现象可同时存在):
            * InterruptedException: 在等待队列中发生中断. 在两个阶段都发生中断时, InterruptedException的优先级更高`
            * isInterrupted(): 在同步队列中发生中断
            * 返回值==false: 超时
            * 返回值==true: 未超时
    * 源代码
    ```java
    public final boolean await(long time, TimeUnit unit)
            throws InterruptedException {
        long nanosTimeout = unit.toNanos(time);

        // 先检查中断情况
        if (Thread.interrupted())
            throw new InterruptedException();

        // 将node加入队列, 顺便清理CANCELLED结点
        Node node = addConditionWaiter();

        // 尝试将所有state释放, 可能抛出异常
        int savedState = fullyRelease(node);
        final long deadline = System.nanoTime() + nanosTimeout;
        boolean timedout = false;
        int interruptMode = 0;

        // 循环阻塞, 直到中断或者超时
        while (!isOnSyncQueue(node)) {

            // nanosTimeout超出时, 
            if (nanosTimeout <= 0L) {
                // 加入同步队列, 并检查有没发生过signal(), 没发生说明真的超时(timeout), 发生说明未超时
                timedout = transferAfterCancelledWait(node); 
                break;
            }

            // 只有剩余时间高于阈值时才阻塞, 否则自旋
            if (nanosTimeout >= spinForTimeoutThreshold)
                LockSupport.parkNanos(this, nanosTimeout);

            // 中断发生时, 尝试加入同步队列, 并检查signal()有没在中断之前发生, 以此来设置interruptMode
            if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) 
                break;
            nanosTimeout = deadline - System.nanoTime();
        }

        // 在同步队列中也要检查中断
        if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
            interruptMode = REINTERRUPT;

        // 清理CANCELLED结点
        if (node.nextWaiter != null)
            unlinkCancelledWaiters();

        // 处理异常
        if (interruptMode != 0)
            reportInterruptAfterWait(interruptMode);
        return !timedout;
    }
    ```