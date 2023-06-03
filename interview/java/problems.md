
## java 
- 集合
    - HashMap 1.7 & 1.8
    - List(ArrayList)
- 并发:
    - Thread
    - wait() / notify()
    - 锁机制: synchronize, CAS, AQS
    - 工具: ConcurrentHashMap / ReentranceLock / CountDownLatch / CyclicBarrier / Semaphore / BlockingQueue / LongAdder / ThreadLocal
    - Executor
- NIO:
    - Buffer
    - Channel
        - ServerSocketChannel
    - DirectByteBuffer
    - AIO
    - IO模型 & select & epoll
- 涉及的设计模式:
- String底层
    - 字符串常量池
    - StringBuider / StringBuffer
    - 为什么不可继承
- 基础:
    - final / finally / finallize()
    - hashCode() & equals()

## JVM
- 内存分区
- 类加载
    - 类加载器
    - 加载 & 解析 & 初始化过程
- 垃圾回收
    - 算法: 
        - CMS, 复制算法, 标记整理
        - 垃圾回收器
- 并发
    - 锁升级
- 字节码
    - lambda实现
- 编译器优化


## 系统

- 进程/线程/协程
    - 进程通信: 管道/命名管理
    - 进程调度算法
    - 死锁: 四个条件 & 避免方法 / 判断 / 解除死锁
- TCP & UDP
    - 三次握手/四次挥手
    - 拥塞控制
    - 计时器
    - 长连接/短连接
    - 粘包
- 内存
    - LRU
- 4种IO模型
- 虚拟内存


## 算法:
- 排序:
    - 归排/**快排**/桶排/希排/**堆排** 
    - 复杂度
    - 稳定性
- 搜索: 深搜 / 广搜
- 动规
- 回溯
- 并查集
- TOP K系列


## MySQL: 
- 索引
    - 聚集索引/非聚集索引
    - B+树
- 事务
    - 隔离级别
    - MVCC & 锁
- 引擎: InnoDB, MyISAM
- count(*) & count(1) & count(field)

## 分布式
- 分布式ID算法
- 缓存
    - 缓存一致性问题
    - 缓存穿透, 击穿, 雪崩
- 一致性问题
    - CAP
    - PAXOS
    - ZAB
- 限流
- 熔断

## netty
- NIO
- Executor

## redis
- 事件驱动IO

## zookeeper
- Paxos算法


## Spring:
- AOP实现: GCLib
- 循环依赖解决方法
- Scope
- 事务管理器
- 设计模式
    - Factory
    - SPI机制
