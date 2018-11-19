AQS
* Node状态说明:
    * Node.SIGNAL: 下一个线程需要被唤醒/该结点负责唤醒下一个结点
    * 0: 未初始化或者运行结点
* 注意:
    * 共享锁不完全符合共享的语义: 一个写锁释放时, 不是所有的读线程都能同时获得读锁, 只有同步队列中靠近head的能被通知到的读线程才能获得
* 结点处理机制:
    * 中断或超时: 
        * 被中断或者超时时转成CANCELLED
        * 该结点会把前后连续的CANCELLED结点都清理掉
        * 然后将前续结点设成signal并连接到下一个有效的结点(如果可以的话), 或者自己唤醒后续结点
    * 进入阻塞:
        * 结点在进入阻塞之前, 把自己前面的CANCELLED结点清理掉, 然后把第一个可以设为SIGNAL的结点设成SIGNAL, 告诉它唤醒自己
        * 把前一个结点设成SIGNAL以后不能马上阻塞, 还要再检查一次自己是否为首结点的下一个
    * 被唤醒:
        * 结点在成为首结点的下个结点时, 会把首结点清理掉
        * 如果是共享锁, 那还要把下一个共享结点唤醒(传播)
    * 释放:
        * 结点在释放成功时, 检查有没需要被唤醒的结点, 有的话就唤醒一个
        * 被唤醒的结点负责清理上一个首结点
* 队列加入算法 (acquire中用到):
    ```java
    private Node addWaiter(Node mode) {
        Node node = new Node(Thread.currentThread(), mode);
        Node pred = tail;
        // 先尝试快速加入队尾
        if (pred != null) {                             
            node.prev = pred;
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        enq(node);                                      // 失败后以循环CAS的方式加入队尾
        return node;                                    // 返回当前结点
    }

    /**
        以循环CAS的方法将结点加入队尾, 并返回前续结点
    */
    private Node enq(final Node node) {
        for (;;) {
            Node t = tail;              
            if (t == null) {
                // 没有tail结点时, 先加入一个占位结点                            
                if (compareAndSetHead(new Node()))
                    tail = head;
            } else {
                node.prev = t;                          // 先让node指向自认为的tail
                if (compareAndSetTail(t, node)) {       // 以CAS的方式把对象的tail引用指向node
                    t.next = node;                      // 将原来的tail(t)指向新node
                    return t;                           // 注意: 返回的是上一个尾结点
                }
            }
        }
    }
    ```
* acquire/release
    ```java
    public final void acquire(int arg) {
        // 1. 快速尝试
        if (!tryAcquire(arg) &&
            // 3. 然后自旋等待, 并检查中断                             
            acquireQueued(                      
                // 2. 非阻塞方式(快速尝试+循环CAS)加入队尾, 
                addWaiter(Node.EXCLUSIVE), arg) 
                )  
            selfInterrupt();
    }

    final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            // 循环阻塞, 直到排到head之后
            for (;;) {
                final Node p = node.predecessor();              
                if (p == head && tryAcquire(arg)) {             // 当前结点前面无结点(有效的结点)才能tryAcquire
                    setHead(node);                              // 将head设为自己
                    p.next = null; // help GC
                    failed = false;                             // 只有在成功获取时才复位failed
                    return interrupted;                         
                }
                // 判断能否阻塞(pre结点是否为SIGNAL), 顺便清理node之前连续的一段CANCELLED结点
                if (shouldParkAfterFailedAcquire(p, node) &&    
                    parkAndCheckInterrupt())                    // 进入阻塞并检查中断
                    interrupted = true;                         // 
            }
        } finally {
            // 在acquire()中, 线程发生异常时, 只置位中断符号
            // 所以正常情况下, acquire()不会引发cancel
            if (failed)                            
                cancelAcquire(node);
        }
    }


    /**
        清理前续CANCELLED结点
        CANCELL掉当前结点
        同时交待前续结点唤醒后续结点, 或者由自己唤醒(如果前续结点都无法唤醒)
        或者不唤醒
    */
    private void cancelAcquire(Node node) {
        if (node == null)
            return;

        node.thread = null;

        // 删除node之前的cancelled结点
        Node pred = node.prev;
        while (pred.waitStatus > 0)
            node.prev = pred = pred.prev;

        Node predNext = pred.next;

        // 设置当前结点为CANCELLED
        node.waitStatus = Node.CANCELLED;                                           

        if (node == tail && compareAndSetTail(node, pred)) {                        // 当前结点为tail 并且CAS尝试设置新结点成功
            // [1]
            compareAndSetNext(pred, predNext, null);                                // 将新tail的next设为null
        } else {                                                                    // 由前面的结点唤醒后续结点或自己唤醒    
            int ws;                                                                 
            if (pred != head                                                        // 不能是头结点 [2]
                &&                                                    
                    ((ws = pred.waitStatus) == Node.SIGNAL ||                               // 上个结点已设为signal
                    (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL)))            // 或成功将上个结点设为signal
                && pred.thread != null                                                  // 上个结点的线程一定是没有结束的
                ) {                                                                 
                Node next = node.next;
                // next == null 时, 可能有新tail, 而node.next还没指向新tail
                if (next != null && next.waitStatus <= 0)                           
                    compareAndSetNext(pred, predNext, next);                            // 丢弃当前结点
            } else {                                                                
                unparkSuccessor(node);                                              // 无法由前续结点唤醒后续结点时, 由自己唤醒[4]
            }
            node.next = node; // help GC
        }
    }
    // [1]: 此处好像有线程安全问题, 不过后果好像不严重 考虑以下情况:
    //      (1)     Thread a:               compareAndSetTail(node, pred);                  // tail被设为pred
    //      (2)     Thread b (enq方法):     Node t = tail;                                  // t也是pred
    //      (3)     Thread b (enq方法):         ...
    //      (7)     Thread b (enq方法):         node.prev = t;                              // 先让node指向自认为的tail
    //      (8)     Thread b (enq方法):         if (compareAndSetTail(t, node)) {           // tail被设为node
    //      (9)     Thread b (enq方法):             pred.next = node;                       // pred指向新tail
    //      (10)    Thread a:                   compareAndSetNext(pred, predNext, null);    // pred指向null

    // [2]: 如果head结点的线程刚release完, 而head唤醒的线程还没设置新的head结点, 那该head结点无法唤醒其它结点, 所以要避开head结点, 选择阻塞的结点
    // [3]: 唤醒后续结点后, 后续结点发现自己并未取得锁, 然后在shouldParkAfterFailedAcquire()方法会将前续的cancelled清理掉
    //      触发条件一: pred为头结点
    //      触发条件二: pred也出异常将自己cancelled掉
    //      触发条件三: pred无线程(pred出异常但还没来得及将自己cancelled掉) 

    /**
        检查能否实现阻塞(上一个node是否为signal)
    */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;

        // 上一个结点为signal状态时, 可以安全地阻塞
        if (ws == Node.SIGNAL)                  
            return true;    
        // 前面存在CANCELLED结点时, 删除前面的连续的cancelled结点
        if (ws > 0) {                                          
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        // [1] 告诉上个结点要唤醒自己 (这种情况下不能阻塞, 以防上个结点刚好解锁)
        } else {                           
            // 上个结点可能是PROPAGATE或者0, (不可能是CONDITION)
            // PROPAGATE表示SHARED结点
            // 0表示结点未初始化或者已经signal过了
            // 在这种情况下才能设为SIGNAL
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL); 
        }
        return false;
    }
    // [1] 在互斥锁的情况下, else中可能的ws只有0 (CONDITION结点不可能被加入到同步队列)

    /**
        释放
    */
    public final boolean release(int arg) {
        // 单次尝试
        if (tryRelease(arg)) {                      
            Node h = head;
            // 判断结点是否有效(null: 尚未初始化, 比如新建AQS后直接release())
            if (h != null && h.waitStatus != 0)     
                unparkSuccessor(h);                 // 成功释放时, 唤醒下一个结点
            return true;
        }
        return false;
    }

    private void unparkSuccessor(Node node) {
        
        int ws = node.waitStatus;
        // 设置为0 (表示运行结束)
        if (ws < 0)
            compareAndSetWaitStatus(node, ws, 0);                       

        // s==null表示s为tail, 或者node.next还没来得及指向新的tail结点, 所以要从tail开始寻找要唤醒的结点
        Node s = node.next;
        if (s == null || s.waitStatus > 0) {
            s = null;

            // 从tail向前, 找最接近当前结点的最近的可唤醒的结点
            for (Node t = tail; t != null && t != node; t = t.prev)
                if (t.waitStatus <= 0)                                  
                    s = t;
        }
        // 找到时才唤醒
        // 被唤醒的结点会负责当前结点的清理
        if (s != null)
            LockSupport.unpark(s.thread);
    }

    ```
* acquireShared/releaseShared
    ```Java
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0)
            doAcquireShared(arg);
    }

    private void doAcquireShared(int arg) {
        final Node node = addWaiter(Node.SHARED);               // shared模式
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (;;) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        if (interrupted)
                            selfInterrupt();
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    private void setHeadAndPropagate(Node node, int propagate) {
        // 清除头结点, 把node设为头结点
        Node h = head; // Record old head for check below
        setHead(node);

        if (propagate > 0 
            || h == null || h.waitStatus < 0 
            || (h = head) == null || h.waitStatus < 0
        ) {
            Node s = node.next;             
            // node.next为shared结点时, 唤醒该结点
            // node.next表现为null时, 也要唤醒. 
            // 如果不小心唤醒一个非shared结点, 该结点发现自己不在队头, 会再阻塞
            if (s == null || s.isShared())  
                doReleaseShared();
        }
    }

    private void doReleaseShared() {

        // 循环CAS
        for (;;) {
            Node h = head;
            if (h != null && h != tail) {

                // 如果该结点被设为SIGNAL, 那就要把自己(head)设为0, 并负责唤醒后续最接近的一个结点
                // 设为0表示结点已经完成了signal, 被唤醒的结点负责清理之前的head结点
                int ws = h.waitStatus;
                if (ws == Node.SIGNAL) {
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                        continue;            // loop to recheck cases
                    unparkSuccessor(h);
                }
                else if (ws == 0 &&
                         !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    continue;                // loop on failed CAS
            }

            // 如果head改变, 要重新循环CAS
            if (h == head)                   
                break;
        }
    }

    public final boolean releaseShared(int arg) {
        // 单次尝试成功, 
        if (tryReleaseShared(arg)) {                
            doReleaseShared();
            return true;
        }
        return false;
    }    
    ```
* acquireInterruptedly
    ```java
    public final void acquireInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (!tryAcquire(arg))
            doAcquireInterruptibly(arg);
    }

    private void doAcquireInterruptibly(int arg)
        throws InterruptedException {
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return;
                }
                if (shouldParkAfterFailedAcquire(p, node) &&        
                    parkAndCheckInterrupt())
                    // 只有此处跟acquire不同, 异常发生时直接抛异常而不是等阻塞结束
                    throw new InterruptedException();           
            }
        } finally {
            // 中断异常会引发cancel
            if (failed)
                cancelAcquire(node);
        }
    }
    ```
* tryAquireNanos
    ```java
    private boolean doAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return true;
                }

                // 超时等待同步机制 
                nanosTimeout = deadline - System.nanoTime(); 
                if (nanosTimeout <= 0L)
                    return false;
                if (shouldParkAfterFailedAcquire(p, node) &&
                    nanosTimeout > spinForTimeoutThreshold)     // 时间小于自旋阈值时不阻塞, 只做自旋
                    LockSupport.parkNanos(this, nanosTimeout);  // 超时等待
                if (Thread.interrupted())
                    throw new InterruptedException();           // 直接抛异常
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }
    ```

