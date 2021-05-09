# AbstractQueuedSynchronizer

## 1. AQS特点

AQS提供的功能:
- 排他:
    - 获取资源值(acquire系列):
        - 非阻塞
        - 阻塞
        - 可中断
        - 带超时
- 共享:


AQS的域:
- 结构: 
    - 队列: 用来保存等待的线程
- 状态:
    - state: 获取的总的资源值
    - exclusiveOwnerThread: 排他持有者线程 


队列: 
- 结点状态(waitStatus):
    ```
    0                普通结点初始化(如果不是0, 则初始化为CONDITION了)
    CANCELLED ( 1)   线程已取消
    SIGNAL    (-1)   后续结点要被唤醒
    CONDITION (-2)   线程在等待condition
    PROPAGATE (-3)   ???
    ```
- 头结点: 头结点是**空结点**, 不保存数据
- 实现:
    - 入队 & 出队: 循环CAS尝试将tail指向新结点, 然后再改原结点的next指针. 出队尾类似
    - 

## 2. 实现

### 2.1 创建结点入队

```java
    private Node addWaiter(Node mode) {

        // 以当前线程创建结点
        Node node = new Node(Thread.currentThread(), mode);

        // 第一次尝试入队
        Node pred = tail;
        if (pred != null) { // 未初始化时, 走另一个分支初始化头结点
            node.prev = pred;
            if (compareAndSetTail(pred, node)) { // 一次成功, 直接返回
                pred.next = node;
                return node;
            }
        }
        // enq有两个作用:
        // 1. 初始化头结点
        // 2. 循环CAS尝试入队
        enq(node);
        return node;
    }

    // 循环CAS尝试将新结点加到队尾
    private Node enq(final Node node) {
        for (;;) {
            Node t = tail;

            // 初始化头结点
            if (t == null) { 
                if (compareAndSetHead(new Node()))
                    tail = head;
            } 
            
            // 尝试CAS加入队尾
            else {
                node.prev = t;
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }
```

### 2.2 tryAcquireNanos() - 带超时 & 可中断获取资源

```java
    public final boolean tryAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        // 先检查中断
        if (Thread.interrupted())               
            throw new InterruptedException();
        return tryAcquire(arg) ||               // 尝试获取成功直接返回
            doAcquireNanos(arg, nanosTimeout);  // 入队等待
    }
```

在队列中阻塞等待资源释放 (带超时, 可中断)
```java
    /**
     * @return true: 获取成功;   false: 超时
     * @throws InterruptedException 发生中断
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        
        if (nanosTimeout <= 0L)
            return false;

        // 获取时间
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            // 循环尝试
            for (;;) {
                // 1. 检查自己是不是第一个结点, 是的话尝试获取
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) { 
                    setHead(node);  // 成功后出队(头结点不保存线程)
                    p.next = null;  
                    failed = false;
                    return true;
                }

                // 2. 检查超时
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L)
                    return false;

                // 3. 设置前续结点为SIGNAL, 保证能被通知到才进入park
                if (shouldParkAfterFailedAcquire(p, node) &&
                    nanosTimeout > spinForTimeoutThreshold)     // 时间过短时不用park, 直接自旋
                    LockSupport.parkNanos(this, nanosTimeout);
                
                // 4. 检查中断
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {

            // 因中断或超时而取消, 则要从队列中清理自己和前续的CANCELLED结点, 并唤醒后续结点
            if (failed)
                cancelAcquire(node);
        }
    }

```

检查能不能进入park
```java
    /** 尝试将前续结点设置为SIGNAL, 以唤醒自己 */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;

        // 1. SIGNAL: 前续结点保证会通知自己
        if (ws == Node.SIGNAL) return true;

        // 2. 前续结点为CANCELLED, 则把前面一片连续的CANCELLED结点都清理掉
        if (ws > 0) {
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } 
        
        // 3. 需要让前续结点承诺会唤醒自己
        else {
            //设置完以后还得退出方法去检查, 不能直接park
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }
```

#### 2.2.1 cancelAcquire() - 取消获取资源

`cancelAcquire()`: 在发生**超时**或**中断**时, 放弃获取资源时调用, 主要包括
- 清理当前结点前面的CANCELLED结点
- 通知或者让前续结点通知后续结点
- 清理自己的结点

**清理CANCELLED结点时为什么不会竞争**: 
- 注意到: 只有执行完清理后, 线程才会设置自己结点的状态为`CANCELLED`
- 假设:
    - 结点B正在执行`cancelAcquire()`的清理过程
    - A为B的某个前续结点
    - C为B的某个后续结点
- 推演: 
    1. A与B并发执行`cancelAcquire()`: 
        1. `A.state == CANCELLED`: 说明A已经做过清理了, 不会再与B竞争清理
        2. `A.state != CANCELLED`: A清理时只会清理自己前面的结点, 而B的清理范围会被A挡住, 不会与B有重复的清理范围
    2. B与C并发执行`cancelAcquire()`:
        - B的清理未完成, 其状态不可能是`CANCELLED`, 而C的清理范围会被A挡住
- 结论: 清理时不会有并发问题

```JAVA
    private void cancelAcquire(Node node) {
        // Ignore if node doesn't exist
        if (node == null) return;

        node.thread = null;

        // 清理: 顺手清理掉自己前面一片连续的CANCELLED结点
        Node pred = node.prev;
        while (pred.waitStatus > 0)
            node.prev = pred = pred.prev;


        Node predNext = pred.next;

        // 将当前结点设为取消, 其它线程会跳过该节点 (一定要清理完才可以设置CANCELLED)
        node.waitStatus = Node.CANCELLED;

        // 如果自己是尾结点(没有后续结点), 则要自己清理自己
        if (node == tail && compareAndSetTail(node, pred)) { // 如果刚好有新结点进来导致CAS失败, 则让后续结点清理自己

            // 只要变成了CANCELLED, 就有可能被另一个线程并发清理掉, 因此必须CAS
            compareAndSetNext(pred, predNext, null);
        }else {

            // 前续结点: 尝试把前续结点设为SIGNAL, 以保证唤醒下个结点
            int ws;
            if (pred != head &&
                ((ws = pred.waitStatus) == Node.SIGNAL ||
                 (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) && // 可能被另一个线程改成 CANCELLED
                pred.thread != null) {

                // CAS尝试清理自己
                Node next = node.next;
                if (next != null && next.waitStatus <= 0)
                    compareAndSetNext(pred, predNext, next);
            } 
            
            // 否则直接唤醒下个结点. 下个结点会清理掉自己
            else {
                unparkSuccessor(node);
            }

            node.next = node; // help GC
        }
    }
```

#### 2.2.2 unparkSuccessor() - 唤醒后续结点

```java
    private void unparkSuccessor(Node node) {
        /*
         * If status is negative (i.e., possibly needing signal) try
         * to clear in anticipation of signalling.  It is OK if this
         * fails or if status is changed by waiting thread.
         */
        // 不懂为什么没有必要改还去改
        int ws = node.waitStatus;
        if (ws < 0) compareAndSetWaitStatus(node, ws, 0);

        // 从后往前遍历找最靠近node的, 可以唤醒的结点
            // 这样可以避开被CANCELLED的, 被唤醒的结点也会把CANCELLED结点清理掉
        Node s = node.next;
        if (s == null || s.waitStatus > 0) {
            s = null;
            for (Node t = tail; t != null && t != node; t = t.prev)
                if (t.waitStatus <= 0)
                    s = t;
        }
        if (s != null) LockSupport.unpark(s.thread);
    }
```


### 2.3 tryRelease() - 释放资源

```java
    public final boolean release(int arg) {
        // 尝试释放成功后, 唤醒下个结点即可
        if (tryRelease(arg)) {  
            Node h = head;
            if (h != null && h.waitStatus != 0)
                unparkSuccessor(h);
            return true;
        }
        return false;
    }
```

### 2.4 tryAcquireSharedNanos()

```java
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquireShared(arg) >= 0 ||
            doAcquireSharedNanos(arg, nanosTimeout);
    }
```

```java
    private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;
        
        // 获取时间
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;

        try {

            // 循环尝试
            for (;;) {

                // 1. 检查自己是否第一结点, 是的话出队, 传播通知其它结点, 然后返回
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);   // 传播唤醒其它结点
                        p.next = null; // help GC
                        failed = false;
                        return true;
                    }
                }

                // 2. 检查超时
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L)
                    return false;

                // 3. 设置前续结点为SIGNAL, 保证能被通知到才进入park
                if (shouldParkAfterFailedAcquire(p, node) &&
                    nanosTimeout > spinForTimeoutThreshold)     // 时间太短时不阻塞, 只自旋
                    LockSupport.parkNanos(this, nanosTimeout);

                // 4. 检查中断
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }
```

#### 2.4.1 setHeadAndPropagate() - 传播

```java
    private void setHeadAndPropagate(Node node, int propagate) {
        Node h = head; // Record old head for check below
        setHead(node);

        // 以下情况传播唤醒后续结点
            // 1. propagate > 0 表示前面 tryAcquireShared()成功
            // 2. 前续结点为空
        if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared()) doReleaseShared();
        }
    }

    private void doReleaseShared() {
        // 循环尝试
        for (;;) {
            Node h = head;
            if (h != null && h != tail) {
                int ws = h.waitStatus;

                // SIGNAL 表示后续结点需要被唤醒, 唤醒下个结点
                    // 后续结点会被连锁地唤醒
                if (ws == Node.SIGNAL) {
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                        continue;            // loop to recheck cases
                    unparkSuccessor(h);
                }

                // 循环CAS将propagation记录下来, 表示是共享模式的结点
                else if (ws == 0 && !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    continue;                // loop on failed CAS
            }

            // 头结点变了需要重新读取, 然后再重试
            if (h == head) break;
        }
    }
```

### 2.5 releaseShared() - 共享锁释放

```java
    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            doReleaseShared();
            return true;
        }
        return false;
    }
```