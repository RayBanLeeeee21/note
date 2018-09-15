问题:
*   ```java
    Sync() {
            readHolds = new ThreadLocalHoldCounter();
            setState(getState()); // ensures visibility of readHolds [1]
        }
    ```


ReentrantReadWriteLock
* 特点:
    * 写锁与所有读锁互斥, 写锁与其它写锁互斥, 读锁之间不互斥
    * 如果一个线程先占有读锁, 再占有写锁, 会发生死锁
* 源代码
    * 说明:
        * tryAquire / tryAcquireShared的实现是提供给AQS的acquire等模板方法调用的, 而tryWriteLock/tryReadLock提供给WriteLock/ReadLock的tryLock方法调用
        * 前者考虑了公平锁和非公平锁要考虑的条件
    * tryAcquire/tryWriteLock 
        ```java
        protected final boolean tryAcquire(int acquires) {
            Thread current = Thread.currentThread();
            int c = getState();                                 // |share state|exclusive state|
            int w = exclusiveCount(c);                          // exclusive state数

            // 锁被占有
            if (c != 0) {
                // 读锁占有(w==0) || 写锁占有且不是当前线程占有, 获取失败
                if (w == 0 || current != getExclusiveOwnerThread())
                    return false;
                if (w + exclusiveCount(acquires) > MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                
                // 成功获取, 增加state数
                setState(c + acquires);
                return true;
            }
            // 锁没有被占有
            if (writerShouldBlock() ||                // [1]
                !compareAndSetState(c, c + acquires)) // CAS竞争锁失败
                return false;
            
            // CAS竞争成功, 把占有锁的线程设为自己
            setExclusiveOwnerThread(current);          
            return true;
        }
        // [1] tryWriteLock中没有这一行
        ```
    * tryRelease(int release)
        ```java
        protected final boolean tryRelease(int releases) {
            // 检查是不是自己持有
            if (!isHeldExclusively())                       
                throw new IllegalMonitorStateException();
            int nextc = getState() - releases;

            // exclusive state数降为0, 释放锁
            boolean free = exclusiveCount(nextc) == 0;      // EXCLUSIVE_MASK(0xFFFF) & state
            if (free)
                setExclusiveOwnerThread(null);
            setState(nextc);
            return free;
        }
        ```
    * tryAcquireShared
        ```java
        protected final int tryAcquireShared(int unused) {
            Thread current = Thread.currentThread();
            int c = getState();

            // 如果被写锁占用, 且不是自己, 失败
            if (exclusiveCount(c) != 0 &&
                getExclusiveOwnerThread() != current)
                return -1;
            int r = sharedCount(c);

            // 满足read的条件, 并且CAS尝试增加share state 成功后, 才修改自己的count
            if (!readerShouldBlock() &&                                 // [1]
                r < MAX_COUNT &&                                        // 检查 share count有没溢出
                compareAndSetState(c, c + SHARED_UNIT)) {

                // 增加share count数
                if (r == 0) {
                    firstReader = current;
                    firstReaderHoldCount = 1;
                } else if (firstReader == current) {
                    firstReaderHoldCount++;
                } else {
                    HoldCounter rh = cachedHoldCounter;                
                    if (rh == null || rh.tid != getThreadId(current))   // 更新缓存(解锁的时候不更新)
                        cachedHoldCounter = rh = readHolds.get();
                    else if (rh.count == 0)
                        readHolds.set(rh);
                    rh.count++;
                }
                return 1;
            }
            // CAS尝试增加
            return fullTryAcquireShared(current);
        }
        // [1] tryReadLock没有这个条件
        // [2] 只要是更改cont都要以下步骤
        //       1. 检查firstReader是不是自己
        //       2. 检查缓存中有没自己的count, 有就直接改, 没有从readHolds(ThreadLocal)中取. 如果是锁, 那还要更新缓存

        final int fullTryAcquireShared(Thread current) {

            // 循环CAS
            HoldCounter rh = null;
            for (;;) {

                // 写锁占用, 且不是自己, 则失败
                int c = getState();
                if (exclusiveCount(c) != 0) {
                    if (getExclusiveOwnerThread() != current)
                        return -1;
                
                // 同步队列的head有独占锁时, 非重新进入的锁[3]被阻塞
                } else if (readerShouldBlock()) {
                    if (firstReader == current) {     // 如果是firstReader, 那一定是重新进入的

                    } else {
                        if (rh == null) {
                            rh = cachedHoldCounter;
                            if (rh == null || rh.tid != getThreadId(current)) {
                                rh = readHolds.get();
                                if (rh.count == 0)
                                    readHolds.remove();
                            }
                        }
                        if (rh.count == 0)          // [3]
                            return -1;
                    }
                }
                if (sharedCount(c) == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");
                    
                // CAS增加share state成功, 则增加自己的count
                if (compareAndSetState(c, c + SHARED_UNIT)) {
                    if (sharedCount(c) == 0) {
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) {
                        firstReaderHoldCount++;
                    } else {
                        if (rh == null)
                            rh = cachedHoldCounter;
                        if (rh == null || rh.tid != getThreadId(current))
                            rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        rh.count++;
                        cachedHoldCounter = rh; // cache for release
                    }
                    return 1;
                }
            }
        }
        ```
    * tryReadLock
        ```java
        final boolean tryReadLock() {
            Thread current = Thread.currentThread();

            // 循环CAS
            for (;;) {

                // 写锁占用, 且不是自己, 则失败
                int c = getState();
                if (exclusiveCount(c) != 0 &&
                    getExclusiveOwnerThread() != current)
                    return false;
                
                // 检查share count 有没溢出
                int r = sharedCount(c);
                if (r == MAX_COUNT)
                    throw new Error("Maximum lock count exceeded");

                // CAS尝试
                if (compareAndSetState(c, c + SHARED_UNIT)) {

                    // 尝试成功再更改自己的count
                    if (r == 0) {
                        firstReader = current;
                        firstReaderHoldCount = 1;
                    } else if (firstReader == current) {
                        firstReaderHoldCount++;
                    } else {
                        HoldCounter rh = cachedHoldCounter;
                        if (rh == null || rh.tid != getThreadId(current))
                            cachedHoldCounter = rh = readHolds.get();
                        else if (rh.count == 0)
                            readHolds.set(rh);
                        rh.count++;
                    }
                    return true;
                }
            }
        }
        ```
    * tryReleaseShared
        ```java
        protected final boolean  (int unused) {
            Thread current = Thread.currentThread();

            // firstReader是当前线程, 表示已经firstReaderHoldCount已经保存了自己的count
            if (firstReader == current) {
                if (firstReaderHoldCount == 1)
                    firstReader = null;
                else
                    firstReaderHoldCount--;
            } else {
                // 先从缓存得到holdCounter
                HoldCounter rh = cachedHoldCounter;

                //缓存的holdCounter不属于自己, 再从readHolds中查找holdCounter
                if (rh == null || rh.tid != getThreadId(current))
                    rh = readHolds.get();
                int count = rh.count;
                if (count <= 1) {
                    // 删除自己的holdCounter
                    readHolds.remove();
                    // 检查非法release
                    if (count <= 0)
                        throw unmatchedUnlockException();
                }
                --rh.count;
            }
            // 循环CAS减少share state
            for (;;) {
                int c = getState();
                int nextc = c - SHARED_UNIT;
                if (compareAndSetState(c, nextc))
                    return nextc == 0;
            }
        }

        
        ```
    * FairSync
        ```java
        static final class FairSync extends Sync {
            final boolean writerShouldBlock() {
                return hasQueuedPredecessors();     // 如果队列有结点且不为自己时, 放弃获取
            }
            final boolean readerShouldBlock() {
                return hasQueuedPredecessors();     // 如果队列有结点且不为自己时, 放弃获取
            }
        }
        ```
    * NonfairSync
        ```java
        static final class NonfairSync extends Sync {
            final boolean writerShouldBlock() {
                return false;                               // 写锁不会放弃获取
            }
            final boolean readerShouldBlock() {
                return apparentlyFirstQueuedIsExclusive();  // 队列的第一个结点为非共享结点时, 放弃获取
            }
        }

        /**
            判断同步队列的第二个结点为非SHARED结点, 且有保存线程
        */
        final boolean apparentlyFirstQueuedIsExclusive() {
            Node h, s;
            return (h = head) != null &&
                (s = h.next)  != null &&
                !s.isShared()         &&
                s.thread != null;
        }
        ```