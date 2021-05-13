
## awaitNanos() (带超时, 可中断)
```java
    public final long awaitNanos(long nanosTimeout)
            throws InterruptedException {

        if (Thread.interrupted()) throw new InterruptedException();     // 检查中断

        // 1. 加入等待队列队尾
        Node node = addConditionWaiter();

        // 2. 释放已有资源
        int savedState = fullyRelease(node);
        final long deadline = System.nanoTime() + nanosTimeout;

        // 3. 循环检查
        int interruptMode = 0;
        while (!isOnSyncQueue(node)) { // 被唤醒后要检查是不是在等待队列中

            // 3.1 超时取消后转移结点
            if (nanosTimeout <= 0L) { transferAfterCancelledWait(node);  break; }

            // 3.2 park
            if (nanosTimeout >= spinForTimeoutThreshold) // 时间过短时不阻塞, 只自旋
                LockSupport.parkNanos(this, nanosTimeout);

            // 被中断
            if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) break;
            nanosTimeout = deadline - System.nanoTime();
        }

        // 4. 退出循环后, 重新取回资源(可能阻塞)
        if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
            interruptMode = REINTERRUPT;

        // 5. 清理取消的结点
        if (node.nextWaiter != null) unlinkCancelledWaiters();

        // 6. 处理中断 - 因取消而中断时需要抛异常
        if (interruptMode != 0) reportInterruptAfterWait(interruptMode);
        return deadline - System.nanoTime();
    }
```

### 加入等待队列

加入ConditionObject的等待队列:
1. 清理掉所有CANCELLED的结点
2. 创建新结点加到最后

调用`await()`系列方法的前提是独占了资源, 因此下面这个方法只会有一个线程在执行, 不必考虑线程安全

```java
    private Node addConditionWaiter() {
        Node t = lastWaiter;
        // 清理掉所有CANCELLED的结点
        if (t != null && t.waitStatus != Node.CONDITION) {
            unlinkCancelledWaiters();
            t = lastWaiter;
        }

        // 然后加到最后
        Node node = new Node(Thread.currentThread(), Node.CONDITION);
        if (t == null)
            firstWaiter = node;
        else
            t.nextWaiter = node;
        lastWaiter = node;
        return node;
    }

    /**
     *  从等待队列的第一个结点开始, 将所有CANCELLED结点删掉
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
```

### 释放已有资源

主要是要判断资源当前是不是自己持有. 如果状态不对, 需要立即取消, 并出队(等待队列)
```java
    final int fullyRelease(Node node) {
        boolean failed = true;
        try {

            // 放弃state
            int savedState = getState();
            if (release(savedState)) {
                failed = false;
                return savedState;
            } else {
                throw new IllegalMonitorStateException(); // 没有上锁不能释放
            }
        } finally {

            // 状态错了要取消掉
            if (failed) node.waitStatus = Node.CANCELLED;
        }
    }
```

### 判断是否在等待队列
```java
    final boolean isOnSyncQueue(Node node) {

        // 状态为CONDITION说明还在等待队列
        if (node.waitStatus == Node.CONDITION || node.prev == null)
            return false;

        // 有后续结点表示被移到了同步队列
        if (node.next != null) return true;
        
        // 从队尾开始找当前结点
        return findNodeFromTail(node);
    }

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

### 取消后转移结点

```java
    final boolean transferAfterCancelledWait(Node node) {
        // 跟signal()线程竞争
        if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
            enq(node); // 成功后进入同步队列
                        // 由于状态不再是CONDITION, 会有线程将自己从等待队列中清理掉
            return true;
        }
        // 竞争失败, 等待回到同步队列
        while (!isOnSyncQueue(node))
            Thread.yield();
        return false;
    }
```

### 中断处理

中断检查: 如果发生了中断, 则要中止等待, 然后CAS尝试将结点放回同步队列
- 如果在被signal之前发生中断, 则要抛中断异常, 否则不能抛异常, 要把中断标志取消掉
```java
    private int checkInterruptWhileWaiting(Node node) {
        return Thread.interrupted() ?
            (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
            0;
    }

    private void reportInterruptAfterWait(int interruptMode)
            throws InterruptedException {
        if (interruptMode == THROW_IE)
            throw new InterruptedException();
        else if (interruptMode == REINTERRUPT)
            selfInterrupt();
    }
```

## signal

```java
    public final void signal() {
        
        // 未持有资源时报错
        if (!isHeldExclusively()) throw new IllegalMonitorStateException();
        Node first = firstWaiter;
        if (first != null) doSignal(first);
    }

    // 从第一个结点开始遍历等待结点, 直到唤醒第一个结点, 然后就返回
    private void doSignal(Node first) {
        do {
            if ( (firstWaiter = first.nextWaiter) == null)
                lastWaiter = null;
            first.nextWaiter = null;
        } while (!transferForSignal(first) &&
                    (first = firstWaiter) != null);
    }

```

### signal后转移结点
```java
    final boolean transferForSignal(Node node) {
        
        // 与cancel()线程竞争 (cancel由中断或超时造成)
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0)) return false;

        // 入队
        Node p = enq(node);
        int ws = p.waitStatus;

        // 如果结点状态变成CANCELLED了, 则唤醒结点去清理自己
        if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
            LockSupport.unpark(node.thread);
        return true;
    }
```

## signalAll()

```java
    public final void signalAll() {
        if (!isHeldExclusively()) throw new IllegalMonitorStateException();
        Node first = firstWaiter;
        if (first != null)
            doSignalAll(first);
    }

    private void doSignalAll(Node first) {
        lastWaiter = firstWaiter = null;
        do {
            Node next = first.nextWaiter;
            first.nextWaiter = null;
            transferForSignal(first);   // 逐一唤醒
            first = next;
        } while (first != null);
    }
```

## awaitUninterruptedly

```java
    public final void awaitUninterruptibly() {

        // 1. 入队
        Node node = addConditionWaiter();

        // 2. 释放已有资源
        int savedState = fullyRelease(node);
        boolean interrupted = false;

        // 3. 循环检查
        while (!isOnSyncQueue(node)) {  // 确定是否还在同步队列

            // 3.1 park
            LockSupport.park(this);     

            // 3.2 检查中断 
            if (Thread.interrupted()) interrupted = true;
        }

        // 4. 重新获取资源, 并清理中断标志
        if (acquireQueued(node, savedState) || interrupted)
            selfInterrupt();
    }
```

## await

```java
    public final void await() throws InterruptedException {
        // 检查中断
        if (Thread.interrupted()) throw new InterruptedException();

        // 1. 入队
        Node node = addConditionWaiter();

        // 2. 释放资源
        int savedState = fullyRelease(node);
        int interruptMode = 0;

        // 3. 循环检查
        while (!isOnSyncQueue(node)) {  // 确定自己不是在同步队列

            // 3.1 park
            LockSupport.park(this);

            // 3.2 检查中断
            if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                break;
        }

        // 4. 重新获取资源
        if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
            interruptMode = REINTERRUPT;

        // 5. 清理取消的结点
        if (node.nextWaiter != null) unlinkCancelledWaiters();

        // 6. 处理中断
        if (interruptMode != 0) reportInterruptAfterWait(interruptMode);
    }
```