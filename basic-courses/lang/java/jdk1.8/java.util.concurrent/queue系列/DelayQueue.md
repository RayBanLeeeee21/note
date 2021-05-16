
### offer()

```java
public boolean offer(E e) {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        q.offer(e);
        if (q.peek() == e) {
            leader = null;
            available.signal(); // 通知等待者
        }
        return true;
    } finally {
        lock.unlock();
    }
}
```

### poll()

```java
    public E take() throws InterruptedException {
        final ReentrantLock lock = this.lock;

        // 上锁
        lock.lockInterruptibly();
        try {
            for (;;) {

                // 没有结点, 阻塞
                E first = q.peek();
                if (first == null)
                    available.await();
                else {

                    // 获取结点的剩余delay
                    long delay = first.getDelay(NANOSECONDS);

                    // 到时间返回
                    if (delay <= 0)
                        return q.poll();
                    first = null; // don't retain ref while waiting

                    // 尝试成为leader
                        // leader要决定等待多长, 并且通知等待者
                    if (leader != null)
                        available.await();  // 竞争leader失败就等其它线程通知
                                            // 被唤醒后回到循环原点重新peek()
                    else {
                        Thread thisThread = Thread.currentThread();
                        leader = thisThread;
                        try {

                            // leader决定等待时间. 被唤醒后回到循环原点重新peek()
                            available.awaitNanos(delay);
                        } finally {
                            if (leader == thisThread)
                                leader = null;
                        }
                    }
                }
            }
        } finally {

            // 通知下个等待者
            if (leader == null && q.peek() != null)
                available.signal();
            lock.unlock();
        }
    }
```