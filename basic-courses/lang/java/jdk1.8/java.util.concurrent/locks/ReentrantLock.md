# ReentrantLock

先验知识
- [AQS](./AbstractQueuedSynchronizer-v2.0.md)
## 非公平锁

尝试上锁
```java
    final boolean nonfairTryAcquire(int acquires) {

        // 先尝试对state做CAS, 改成非0值
            // 成功后把线程设为自己
        final Thread current = Thread.currentThread();
        int c = getState();
        if (c == 0) {
            if (compareAndSetState(0, acquires)) {
                setExclusiveOwnerThread(current);
                return true;
            }
        }

        // 本来就是持有者, 则增加重入值 
        else if (current == getExclusiveOwnerThread()) {
            int nextc = c + acquires;
            if (nextc < 0) // overflow
                throw new Error("Maximum lock count exceeded");
            setState(nextc);
            return true;
        }
        return false;
    }
```

尝试解锁
```java
    protected final boolean tryRelease(int releases) {
        int c = getState() - releases;

        // 当前线程持有才能操作
        if (Thread.currentThread() != getExclusiveOwnerThread())
            throw new IllegalMonitorStateException();
        boolean free = false;

        // 取消独占
        if (c == 0) {
            free = true;
            setExclusiveOwnerThread(null);
        }

        // 将state改回来
        setState(c);
        return free;
    }
```

### 公平锁

尝试上锁
```java
    protected final boolean tryAcquire(int acquires) {
        final Thread current = Thread.currentThread();
        int c = getState();
        if (c == 0) {
            // 只有当队列中没有等待者时, 才会尝试更改state, 尝试将自己设置成持有者
            if (!hasQueuedPredecessors() &&
                compareAndSetState(0, acquires)) {
                setExclusiveOwnerThread(current);
                return true;
            }
        }
        // 已是持有者, 直接增加重入数
        else if (current == getExclusiveOwnerThread()) {
            int nextc = c + acquires;
            if (nextc < 0)
                throw new Error("Maximum lock count exceeded");
            setState(nextc);
            return true;
        }
        return false;
    }
```