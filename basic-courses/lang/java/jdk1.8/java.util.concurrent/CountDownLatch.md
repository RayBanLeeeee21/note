# CountDownLatch

先验知识
- [AQS](./locks/AbstractQueuedSynchronizer-v2.0.md)

CountDownLatch
- 特点: 
    - 可中断, 可计时
    - 可以多个线程同时await()
- 原理:     
    - 初始时, 将AQS的state设成指定的count
    - 每次`countDown()`时, 通过`tryReleaseShared()`方法, 以cas的方式将state减1
    - `await`()方法中调用了`tryAcquire()`方法, 该方法只有在`state=0`时才返回成功
- 实现: 
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

        public void await() throws InterruptedException {
            sync.acquireSharedInterruptibly(1);                 // 可中断
        }

        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;                  // state被减到0时才能释放
        }

    ```
- 问题:
    - Q: 为什么不用独占的`acquire()`&`release()`实现?
        - A: 
            - `CountDownLatch.await()`方法可能会有多个线程调用, 当一个阻塞的线程抢到资源时, 也要通过传播通知其它所有线程
            - 另外在竞争`CountDownLatch`的线程中并没有一个独占者的角色