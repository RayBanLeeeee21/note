# Semaphore

先验知识
- [AQS](./locks/AbstractQueuedSynchronizer-v2.0.md)

Semaphore: 实质上是个不可重入的限制state次数的锁
- 特点:
    - 方法命名跟AQS中的习惯不同: 
        - AQS: `acquire()`不可中断, `acquireInterruptibly()`可中断
        - Semaphore: `acquire()`可中断, `acquireUninterruptibly()`可中断
        - *两个类的作者都是Doug Lea, 作者这可是在搞事情 -_-!!*
    - 一个线程acquire次数超过Semaphore的state数时, 会死锁
    - 分了公平类型与非公平类型的
- 源代码
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
- 问题:
    - Q: 为什么不用独占的`acquire()`&`release()`实现?
        - A: 
            - 假设线程A和B在队列中等待
            - 此时有两个线程都释放了自己的资源, 并且都只通知到第一个等待的线程
            - 然后A`tryAcquire()`成功, 但是它只抢了1个单位的资源, 而在`Semaphore.acquire()`方法返回时并没有通过传播来告诉B还有1个单位的资源
            - 那B只能等到A释放时才能拿到资源, 白等了很长时间
            - 另外, 在竞争`Semaphore`的线程中, 并没有一个独占者的角色