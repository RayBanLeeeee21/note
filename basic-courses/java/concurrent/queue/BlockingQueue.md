BlockingQueue接口
* remove/add/element: 无法操作时**抛异常**
* poll/offer/peek: 无法操作时**返回false/null**
* put/take: 无法操作时**阻塞**, 直到条件满足, **可中断**
    * lockInterruptibly()方法与Condition.await()方法实现
* 超时poll/超时offer: 无法操作时**超时阻塞**, 直到条件满足或者超时, **可中断**
    * lockInterruptibly()方法与Condition.awaitNano()方法实现
* 特点:
    * 生产/消费模式

ArrayBlockingQueue:
* 特点:
    * 锁: 只有一个锁, 可选公平/非公平, 默认非公平
        * 带两个condition: notEmpty, notNull
    * 数据结构: 循环数组
    * 容量: 定长, **强制用户指定长度**
    * 所有操作都上锁

LinkedBlockingQueue
* 特点:
    * 锁: **putLock**和**takeLock**, **非公平**
        * condition: notNull对应takeLock, notEmpty对应putLock
    * 数据结构: 单链表
    * 容量: 定长, 不指定时最大为Integer.MAX_VALUE
    * poll和offer中使用了双检查机制
        1. **检查容量**, 满/空时不能offer/poll
        2. **上锁**
        3. **重检查**容量
        4. enqueue/dequeue
        5. 检查容量, 如果不满/不空, 则可以唤醒等待notEmpty/notNull的线程
            * poll和offer用了两个不同的锁, 很可能刚enqueue/dequeue, 就被取走了/加入了新结点
    * add/offer/put和poll/take可并发(**remove不可以**)

LinkedBlockingDeque
* 特点:
    * 锁: 只有一个锁, **非公平**
        * 带两个Condition: notNull, NotEmpty
    * 数据结构: 双链表
    * 容量: 定长, 不指定时最大为Integer.MAX_VALUE

SynchronizeQueue
* 特点:
    * 


ArrayBlockingQueue实现
* add
    ```java
    public boolean add(E e) {
        return super.add(e);
    }

    public boolean add(E e) {
        if (offer(e))
            return true;
        else
            throw new IllegalStateException("Queue full");
    }
    ```
* remove
    ```java
    public void remove() {
        // assert lock.getHoldCount() == 0;
        final ReentrantLock lock = ArrayBlockingQueue.this.lock;
        lock.lock();
        try {
            if (!isDetached())
                incorporateDequeues(); // might update lastRet or detach
            final int lastRet = this.lastRet;
            this.lastRet = NONE;
            if (lastRet >= 0) {
                if (!isDetached())
                    removeAt(lastRet);
                else {
                    final E lastItem = this.lastItem;
                    // assert lastItem != null;
                    this.lastItem = null;
                    if (itemAt(lastRet) == lastItem)
                        removeAt(lastRet);
                }
            } else if (lastRet == NONE)
                throw new IllegalStateException();
            // else lastRet == REMOVED and the last returned element was
            // previously asynchronously removed via an operation other
            // than this.remove(), so nothing to do.

            if (  < 0 && nextIndex < 0)
                detach();
        } finally {
            lock.unlock();
            // assert lastRet == NONE;
            // assert lastItem == null;
        }
    }
    ```
* element
    ```java
    public E element() {
        E x = peek();
        if (x != null)
            return x;
        else
            throw new NoSuchElementException();
    }
    ```
* offer
    ```java
    public boolean offer(E e) {
        checkNotNull(e);
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (count == items.length)
                return false;
            else {
                enqueue(e);
                return true;
            }
        } finally {
            lock.unlock();
        }
    }
    ```
* poll
    ```java
    public E poll() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return (count == 0) ? null : dequeue();
        } finally {
            lock.unlock();
        }
    }
    ```
* peek
    ```java
    public E peek() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return itemAt(takeIndex); // null when queue is empty
        } finally {
            lock.unlock();
        }
    }
    ```
* take
    ```java
    public E take() throws InterruptedException {
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();               // 可中断锁
        try {
            while (count == 0)          
                notEmpty.await();   // 阻塞等待
            return dequeue();
        } finally {
            lock.unlock();
        }
    }
    ```
* put
    ```java
    public void put(E e) throws InterruptedException {
        checkNotNull(e);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();               // 可中断锁
        try {
            while (count == items.length)
                notFull.await();
            enqueue(e);
        } finally {
            lock.unlock();
        }
    }
    ```
* 超时offer
    ```java
    public boolean offer(E e, long timeout, TimeUnit unit)
        throws InterruptedException {

        checkNotNull(e);
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();
        try {
            while (count == items.length) {
                if (nanos <= 0)
                    return false;
                nanos = notFull.awaitNanos(nanos);
            }
            enqueue(e);
            return true;
        } finally {
            lock.unlock();
        }
    }
    ```
* 超时poll
    ```java
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();               // 可中断锁
        try {
            while (count == 0) {
                if (nanos <= 0)
                    return null;
                nanos = notEmpty.awaitNanos(nanos); //超时等待
            }
            return dequeue();                   
        } finally {
            lock.unlock();
        }
    }
    ```


