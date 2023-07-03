# Queue系列

## Queue接口

方法
- 添加
    - `add(E): booleean`: 元素加到队尾, 超出容量报`IllegalStateException`
    - `offer(E): boolean`: 元素加到队尾, 超出容量返回false
- 读队尾
    - `element(): E`: 只读队头元素, 队列为空报`NoSuchElementException`
    - `peek(): E`: 只读队头元素, 队列为空返回null
- 删除
    - `remove(): E`: 读队头并删除, 队列为空报`NoSuchElementException`
    - `poll(): E`: 读队头并删除, 队列为空返回null


## Deque接口
继承自`java.util.Queue`, 但是是双端队列

方法
- 添加
    - `addFirst(E): void`: 元素加到队头, 超出容量报`IllegalStateException`
    - `addLast(E): void`: 元素加到队尾, 超出容量报`IllegalStateException`
    - `offerFirst(E): boolean`: 元素加到队头, 超出容量返回null
    - `offerLast(E): boolean`: 元素加到队尾, 超出容量返回null
- 读取
    - `getFirst(): E`: 读队头, 队列为空报`NoSuchElementException`
    - `getLast(): E`: 读队尾, 队列为空报`NoSuchElementException`
    - `peekFirst(): E`: 读队头, 队列为空返回null
    - `peekLast(): E`: 读队尾, 队列为空返回null
- 删除
    - `removeFirst(): E`: 读并删除队头, 队列为空报`NoSuchElementException`
    - `removeLast(): E`: 读并删除队尾, 队列为空报`NoSuchElementException`
    - `pollFirst(): E`: 读并删除读队头, 队列为空返回null
    - `pollLast(): E`: 读并删除队尾, 队列为空返回null
- 继承自`Queue`的方法:
    - 添加操作都是添加到队头
    - 读取/删除操作都是操作队尾


## BlockingQueue接口
方法:
- 添加
    - `offer(E,long,TimeUnit) boolean`: 等待元素加到队头, 超时返回null, 被中断抛`InterruptedException`
    - `put(E)`: 永久等待元素加到队头, 被中断抛`InterruptedException`
- 读取
    - 同`Queue`
- 删除
    - `poll(long,TimeUnit): E`: 等待读队头并删除, 超时返回null, 被中断抛`InterruptedException`
    - `take()`: 永久等待读队头并删除, 被中断抛`InterruptedException`


怎么区分每个方法副作用?
- `peek`: 偷看一眼, 没有就没有
- `offer`: 无偿提供
- `poll`: 轮询, 有就拿走


实现类:
- 非线程安全:
    - `ArrayDeque`: 
        - 数据结构: 基于循环数组实现的双端队列, 通过head, tail双指针标记队头队尾
        - 无容量限制
        - 可扩容: 双倍扩容
    - `LinkedList`:
        - 数据结构: 基于双链表实现的双端队列, first, last双指针标记队头队尾
        - 无容量限制
- 线程安全:
    - `LinkedBlockingQueue`:
        - 数据结构: 基于双链表实现的队列
        - 可设置最大容量限制
        - 并发实现:
            - 基于`ReentranLock`, `Condition`, `AtomicInteger`实现, 基于多生产者-多消费者模式.
            - 生产者和消费者总有一个处于休眠状态, 因此不会有并发问题
                ```java
                private final ReentrantLock takeLock = new ReentrantLock();
                private final Condition notEmpty = takeLock.newCondition();
                private final ReentrantLock putLock = new ReentrantLock();
                private final Condition notFull = putLock.newCondition();
                private final AtomicInteger count = new AtomicInteger();
                ```
    - `LinkedBlockingDeque`:
        - 数据结构: 基于双链表实现的双端阻塞队列
        - 可设置最大容量限制
        - 并发实现:
            - 基于`ReentranLock`, `Condition`实现, 基于生产者-消费者模式. 但是读写共用同一个lock
                ```java
                final ReentrantLock lock = new ReentrantLock();
                private final Condition notEmpty = lock.newCondition();
                private final Condition notFull = lock.newCondition();
                ```
    - `ArrayBlockingQueue`
        - 数据结构: 基于循环数据实现的阻塞队列
        - 可设置最大容量限制
        - 并发实现
            - 基于`ReentranLock`, `Condition`实现, 基于生产者-消费者模式. 但是读写共用同一个lock
                ```java
                final ReentrantLock lock;
                private final Condition notEmpty;
                private final Condition notFull;
                ```