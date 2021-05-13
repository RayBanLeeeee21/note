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
    


## 2. 实现

### 2.1 创建结点入队

入队关键:
-   ```java
    node.pre = tail;            // 1. 这一步可能有其它线程也做这个尝试
    casTail(oldTail, node);     // 2. cas
    oldTail.next = node;        // 3. 只有成功的线程可以把oldTail.next指向新的tail
    ```
- 问题
    - Q: 为什么不能先cas替换tail? 
        - A: 有线程会从tail向前遍历. 如果先把tail改成了node, 那遍历的线程无法从tail往前找

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
            pred.next = node;                   // 这里如果pred可能会变成CANCELLED, 下次还是可以检查出来
        } 
        
        // 3. 需要让前续结点承诺会唤醒自己
        else {
            //设置完以后还得退出方法去检查, 不能直接park
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }
```

#### 2.2.1 cancelAcquire() & unparkSuccessor()

这两个方法可以是整个AQS中最难理解的方法, 其难点在于高并发的情况下还要从链表的中间清理结点, 同时保证不会有沉睡结点被遗漏

`cancelAcquire()`: 在发生**超时**或**中断**时, 放弃获取资源时调用, 主要包括
- 清理当前结点及前面一片连续的CANCELLED结点
    - 先让pre指针通路绕过CANCELLED结点
    - 再更改next指针通路
    - 改pre通路时会改若干次`node.pre`, 但只需要改一次`pred.next`
- 自己通知或者让前续结点通知最靠前的沉睡结点


竞争条件:
- `prev`指针: 
    - step-2中, 每个结点只有一个线程会修改`node.prev`
    - 虽然`pred.prev`结点的prev被赋给`node.prev`可能马上就变了, 如`A->B`
    - 但线程下次迭代的时候仍然可以通过prev指针通路遍历到`B`
    - 所以对`prev`指针的修改能保证**结果一致**
- `next`指针:
    - 考虑连续的结点`A->B->C`
        - 当`B`在`cancelAcquire()`把自己标记为CANCELLED时, 其查找到的pred为`A`
        - 此时`C`也失效, 在`cancelAcquire()`把自己标记为CANCELLED, 其查找到的pred也是`A`
        - 因此对结点`next`指针的修改是**存在并发**, 可能会被错误地指向一个CANCELLED结点
- **如何保证不遗漏沉睡结点**
    - 虽然结点的`pred.next`指针可能指向一个CANCELLED结点
    - 但是pred线程执行`unparkSuccessor()`方法, 检查到`next`指向失效节点时, 会从tail向前遍历, 找到最前面的沉睡结点

```JAVA
    private void cancelAcquire(Node node) {
        // Ignore if node doesn't exist
        if (node == null) return;

        // step-1. 去掉thread代表cancelAcquire过程开始了
        node.thread = null;     

        // step-2. 清理: 使pre指针通路(从node开始)绕过node及其前面的一片失效结点
            // next指针通路仍然经过失效结点
        Node pred = node.prev;
        while (pred.waitStatus > 0) 
            node.prev = pred = pred.prev;   // 注意到, 自始至终只有一个线程在修改node.prev
                                            // pred.prev被赋给node.prev后, 可能会被其它线程更改, 如A变成B
                                            // 但当前线程下次迭代时还是通过prev通路遍历到B


        // pred是从当前结点往前找到的第1个有效结点 (当前线程认为pred有效, 但pred在后面仍可能失效)
        Node predNext = pred.next;  // predNext后面cas用到

        // step-3. 将当前结点设为取消, 保证其它线程会让pre通路绕过该结点
            // 一定要在step-2清理后才设置, 这样才能保证线程更新pre指针时的线程安全
        node.waitStatus = Node.CANCELLED;

        // step-4. 清理: 使next指针通路(从pred开始)绕过node及其前面的一片失效结点
            // step-2中可能会多次更改node.pre, 但step-4只改一次pred.next
            // step-2中只有一个线程会写node.pre, 但step-4可能会有多个线程找到同一个pred, 然后竞争修改pred.next

        // 如果自己是尾结点, 则要负责更新tail
        if (node == tail && compareAndSetTail(node, pred)) { // 与addWaiter()线程竞争

            // 既和addWaiter()线程竞争, 又和cancelAcquire()线程竞争
            compareAndSetNext(pred, predNext, null);
        }else {

            // 尝试让pred通知后面的结点
                // 以下3个检查是确定pred的线程有能力唤醒后续结点
            int ws;
            if (pred != head &&                             // 1. head结点没有线程, 不能唤醒下个结点
                ((ws = pred.waitStatus) == Node.SIGNAL ||   // 2. 确认ws是SIGNAL, 或者可以改成SIGNAL
                 (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) && // 可能被另一个线程改成 CANCELLED
                pred.thread != null) {                      // 3. 最后一次检查pred的线程是否有效 
                                                                // 线程进入cancelAcquire()/setHead()方法时才会清理thread
                                                                // 该条件说明pred的线程要退出acquire()了, 可能没法通知后续结点

                // 即使通过前面3个检查, pred的线程依然可能进入cancelAcquire()/setHead()而错过通知node的后续结点
                    // 不过pred线程在unparkSuccessor()方法有保底方案 - pred线程发现其后结点(node)已失效, 然后从tail往前找失效结点
                    // 不过大多数情况下还是可以在这里成功让pred.next连到下个结点上
                Node next = node.next;
                if (next != null && next.waitStatus <= 0) compareAndSetNext(pred, predNext, next);
            } 
            else {
                // 自己来唤醒后续结点. 而后续结点会负责把自己和前面的CANCELLED结点清理掉
                unparkSuccessor(node);
            }

            node.next = node; // help GC
        }
    }
```

unparkSuccessor() - 唤醒后续结点: 该方法在以下三种情况下被调用到
- 取消获取资源 - `cancelAcquire()`方法
- `release()`
- `doReleaseShared()`
    - 该方法会在`doAcquiredShared()`系列中获取到资源时或者`releaseShared()`方法中被调用到

```java
    private void unparkSuccessor(Node node) {
        /*
         * If status is negative (i.e., possibly needing signal) try
         * to clear in anticipation of signalling.  It is OK if this
         * fails or if status is changed by waiting thread.
         */
        // ??? 不懂为什么没有必要改还去改
        int ws = node.waitStatus;
        if (ws < 0) compareAndSetWaitStatus(node, ws, 0);

        // 不能确定node.next是沉睡结点时, 需要从tail往前找最前面的沉睡结点
        Node s = node.next;
        if (s == null               // 1. 不一定是没有next, 可能新tail刚加进来还没更改oldTail.next, 所以要检查
            || s.waitStatus > 0) {  // 2. 下个结点失效了, 需要从tail往前
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
                        setHeadAndPropagate(node, r);   // 传播唤醒其它结点. 注意传播范围可能是有限的, r==0时就不会再传播
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
            // 1. propagate == 0 时不再继续往前传播. 该特性在Semaphore中
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
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0)) continue;            // loop to recheck cases
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

## 3. 问题

问题:
- Q: 如果共享结点和独占结点共存于队列上时, 如何区分共享和独占结点, 以唤醒正确的结点?
    - A: 结点不需要知道下个结点是共享还是独占结点, 它只需要知道自己在`acquire()`成功时要不要唤醒下个结点
        - 共享结点在`doAcquireShared()`中获取资源成功后马上会在`setHeadAndPropagate()`执行`unparkSuccessor()`唤醒后续结点
        - 而独占结点在`queuedAcquire()`中获取资源成功后不会唤醒后续结点, 只有在`release()`时才会
        - 共享结点如果在获取资源成功后, `unpark()`了下个独占结点, 独占结点也会在`tryAcquire()`时失败, 从而再次进入`park()`