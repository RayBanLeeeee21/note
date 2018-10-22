Condition接口:
* await()
* awaitUninterruptibly()
* awaitNanos(long)
* await(long, TimeUnit)
* awaitUntil(Date)
* signal()
* signalAll()

先验知识:
* LockSupport.park()在发生中断时直接返回, 不会抛中断异常, 但是可以通过中断标志来查询
* wait()和sleep(long)在发生中断时会直接抛中断异常, 但会**复位中断标志**, 无法通过中断标志来查询
    * 思考: 中断位与中断异常的组合可以区分不同的状态(4种)

ConditionObject
* 特点:
    * 该类为AbstractQueuedSynchronizer的**非静态**内部类, 可以访问外部类的资源
    * 中断时直接抛中断的方法
        * void await() throws InterruptedException
        * long awaitNanos(long nanosTimeout) throws InterruptedException
        * boolean awaitUntil(Date deadline) throws InterruptedException
        * boolean await(long time, TimeUnit unit) throws InterruptedException
    * 中断时不直接返回的方法
        * final void awaitUninterruptibly() 
    * 与Object的wait比较
        * **Object没有不可中断的wait**
        * wait()之前都要先占有锁(synchronize/lock)
* void awaitUninterruptibly()
    ```java
    /**
        awaitUninterruptibly()中发生中断只能通过查询中断标志来检查, 不会抛中断异常
    */
    public final void awaitUninterruptibly() {
        // 1. 先清理一下CANCELLED结点, 再加入等待队列
        Node node = addConditionWaiter();              

        // 2. 检查是否自己持有锁
        //      是则一次性释放锁
        //      否则会抛IllegalMonitorStateException, 并把当前结点设为CANCELLED, 交给其它结点清理
        int savedState = fullyRelease(node);            

        // 3. 循环阻塞直到自己被加入同步队列
        boolean interrupted = false;                            
        while (!isOnSyncQueue(node)) {                // 未加入到同步队列, 即仍处在等待队列     
            LockSupport.park(this);                             
            if (Thread.interrupted())                 
                interrupted = true;                   // 在同步队列中发生中断时, 只是简单记录中断情况
        }
        if (acquireQueued(node, savedState)           // [1] 加入同步队列, 在同步队列中会检查中断
            || interrupted)                           // 双重检查中断
            selfInterrupt();
    }
    // [1] acquireQueued中发生中断时不会直接抛异常, 只会把中断记录下来

    /**
        等待队列中加入新结点之前先检查一下有没CANCELLED结点, 有就先清理一下
    */
    private Node addConditionWaiter() {
        Node t = lastWaiter;
        
        // 最后一个结点不为Node.CONDITION时, 先清理CANCELLED结点
        if (t != null && t.waitStatus != Node.CONDITION) {              
            unlinkCancelledWaiters();
            t = lastWaiter;
        }

        // [1] 新建结点, 加入队列尾部, 重设firstWaiter和lastWaiter
        Node node = new Node(Thread.currentThread(), Node.CONDITION);   
        if (t == null)
            firstWaiter = node;                                         
        else
            t.nextWaiter = node;                                        
        lastWaiter = node;                                              
        return node;
    }
    // [1] 此处对于等待队列的操作并非线程安全
    //      如果一个线程A在持有锁的时候调用await*(), 另一个线程B在未持有锁的情况下调用await*()
    //      那可能造成A的结点没有成功加入等待队列(另一个线程把结点覆盖掉), 最终一直阻塞下去
    // 但如果所有线程都保证在持有锁时才await*(), 那await*()的调用就不会有并发问题, 从而线程安全

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
        尝试失败(非本线程占有)抛异常(无返回值)
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
            // 结点已经进入等待队列,
            // [2] 所以异常或failed时都将Node取消
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

        // 如果在被unpark以后进入此方法, 那结点一定会在同步队列中, 一定能找到结点
        // 如果还未进行park之前就被signal, 此时负责signal的线程可能正在enq()方法中尝试把当前结点加入同步队列
        //      此时node.prev != null并不代表结点已经进行同步队列(可能CAS失败), 所以要从tail开始检查
        //          如果检查时已经enq成功, 那可以找到结点
        //          如果依然没有enq成功, 那就只能阻塞
        //              由于负责signal的线程仍然在enq中循环尝试, 一旦成功就会再次唤醒当前结点, 所以不会死锁
        return findNodeFromTail(node);
    }

    /**
    *   从tail到head检查node是否在同步队列中
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
        // 遍历等待队列, 只到成功保证等待的结点可以被唤醒
        do {
            if ( (firstWaiter = first.nextWaiter) == null)           
                lastWaiter = null;
            first.nextWaiter = null;                          
        } while (!transferForSignal(first) &&                // 成功时返回
                    (first = firstWaiter) != null);          // 或遍历到等待队列的结尾时退出
    }

    final boolean transferForSignal(Node node) {

        // [1] 如果node的状态不是CONDITION, 可能是CANCELLED结点或者与可中断await()竞争失败
        //      失败时直接跳过当前结点
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))       
            return false;                                           

        // 循环CAS将node加入同步队列, 得到node的pre结点
        // 以下条件唤醒node:
        //     1. 上个结点p变成CANCELLED
        //     2. 当前线程没有为node设置SIGNAL结点
        // node被唤醒以后会清理CANCELLED结点, 并为自己设置SIGNAL结点, 或者作为头结点获得锁
        Node p = enq(node);                                          
        int ws = p.waitStatus;                                          
        if (ws > 0                                                   
            || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))         
            LockSupport.unpark(node.thread);
        return true;
    }
    // [1] 这个操作说明了同步队列中不可能有状态为CONDITION的结点

    ```
* void signalAll()
    ```java
    public final void signalAll() {
        // 检查锁的持有
        if (!isHeldExclusively())
            throw new IllegalMonitorStateException();
        Node first = firstWaiter;
        if (first != null)
            doSignalAll(first);
    }

    /**
        从first开始, 逐个transferForSignal, 处理完一个删掉一个
        保证每个结点被加入到同步队列, 并且有机会被唤醒
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
        * 如果在signal()后被中断, 只是**置位中断符号**
    ```java
    public final void await() throws InterruptedException {
        // 1. 首先检查中断
        if (Thread.interrupted())                                           
            throw new InterruptedException();                               
        
        // 2. 清理CANCELLED结点, 然后加入队尾
        Node node = addConditionWaiter();
        
        // 3. 如果自己持有锁则释放锁, 否则抛异常, 并把当前结点设为CANCELLED, 由其它结点清理
        int savedState = fullyRelease(node);

        // 4. 循环阻塞, 直到被加入到同步队列, 同时要判断中断情况
        int interruptMode = 0;
        while (!isOnSyncQueue(node)) {
            LockSupport.park(this);
            if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)   // 发生中断时判断中断发生时机, 然后跳出循环
                break;
        }

        // 在同步队列中发生中断, 都只是在中断标志上置位
        if (acquireQueued(node, savedState) && interruptMode != THROW_IE)   //[1]
            interruptMode = REINTERRUPT;

        // 后续不为null时, 清理CANCELLED结点 (当前结点可能发生中断而成为CANCELLED)
        if (node.nextWaiter != null) 
            unlinkCancelledWaiters();

        // 如果中断发生在被signal()之前, 则抛Interrupted异常
        // 如果中断发生在被signal()之后, 则置位中断符号
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
        将结点加入同步队列, 
            与signal竞争失败时返回false, 否则true
    */
    final boolean transferAfterCancelledWait(Node node) {
        // CAS尝试把waitStatus从CONDITION变成0 (与signal线程竞争)
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
                
                // 1. 超时时, 尝试将结点加入同步队列 (与signal线程竞争)
                if (nanosTimeout <= 0L) {                         
                    transferAfterCancelledWait(node);             
                    break;
                }

                // 2. 剩余超时大于阈值时, 才阻塞, 否则自旋
                if (nanosTimeout >= spinForTimeoutThreshold)                   
                    LockSupport.parkNanos(this, nanosTimeout);                    

                // 3. 被唤醒时, 检查有没中断, 有中断则根据signal()发生情况来确定中断模式
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)   
                    break;

                // 4. 更新时间
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