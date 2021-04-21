
# 相关类
Executors.DefaultThreadFactory
* ThreadFactory的意义
    * 统一设置thread的属性
        * thread name
        * priority
        * isDaemon
        * thread group
```java
static class DefaultThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        DefaultThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                                  Thread.currentThread().getThreadGroup();
            namePrefix = "pool-" +
                          poolNumber.getAndIncrement() +
                         "-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                                  namePrefix + threadNumber.getAndIncrement(),
                                  0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
```

## Worker

Worker的特点
- 携带一个task
- 其本身是个锁(不可重入锁)

ThreadPoolExecutor.Worker
```java
private final class Worker
        extends AbstractQueuedSynchronizer
        implements Runnable
{
    /**
        * This class will never be serialized, but we provide a
        * serialVersionUID to suppress a javac warning.
        */
    private static final long serialVersionUID = 6138294804551838833L;

    /** Worker正在跑的线程, null表示线程工厂创建线程失败 */
    final Thread thread;
    /** 初始任务 */
    Runnable firstTask;
    /** Per-thread task counter */
    volatile long completedTasks;

    
    Worker(Runnable firstTask) {
        setState(-1);                   // [1]
        this.firstTask = firstTask;

        //  ThreadFactory会把给定的task放在Thread里面
        this.thread = getThreadFactory().newThread(this);
    }
    // [1] state被初始化为-1, 此时不可被中断, 经过一次unlock()后再改成0, 可被中断

    /** Delegates main run loop to outer runWorker  */
    public void run() {
        runWorker(this);
    }

    // 锁方法 (不可重入排他锁)
    // 1表示上锁, 0表示未上锁
    protected boolean isHeldExclusively() {
        return getState() != 0;
    }

    protected boolean tryAcquire(int unused) {
        if (compareAndSetState(0, 1)) {
            setExclusiveOwnerThread(Thread.currentThread());
            return true;
        }
        return false;
    }

    protected boolean tryRelease(int unused) {
        setExclusiveOwnerThread(null);
        setState(0);
        return true;
    }

    public void lock()        { acquire(1); }
    public boolean tryLock()  { return tryAcquire(1); }
    public void unlock()      { release(1); }
    public boolean isLocked() { return isHeldExclusively(); }

    // 任务开始才能中断
    void interruptIfStarted() {
        Thread t;
        if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
            try {
                t.interrupt();
            } catch (SecurityException ignore) {
            }
        }
    }
}
```

## ThreadPoolExecutor
* 机制:
    * 核心线程与最大线程
        * workerCount < corePoolSize
            * 创建核心线程来执行任务, **即使其它线程空闲**
        * corePoolSize <= workerCount < maximumPoolSize
            * 只有队列满时才创建线程
        * maximumPoolSize <= workerCount
            * 不创建线程
    * 预启动:
        * 可以不等到task到达就启动核心线程
            * public boolean prestartCoreThread()
            * public int prestartAllCoreThreads()
    * 线程工厂
    * keep-alive-time
        * 非核心线程空闲时间达到keepAliveTime会清理掉
        * 核心线程也可以设置成带keepAliveTime的
* 域:
    * private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0)); 
        * 高3位用来表示线程池状态
        * 后29位用来保存Worker数
    * private final BlockingQueue<Runnable> workQueue
        * 任务队列
    * private final HashSet<Worker> workers = new HashSet<Worker>()
        * 保存所有worker
    * private volatile ThreadFactory threadFactory
        * 默认为Executors.defaultThreadFactory()
    * private volatile RejectedExecutionHandler handler
        * 处理被拒绝执行的task
        * 接口方法: void rejectedExecution(Runnable r, ThreadPoolExecutor executor)
        * 默认为AbortPolicy()
    * private volatile long keepAliveTime
    * private volatile int corePoolSize
    * private volatile int maximumPoolSize
* 状态: 
    * 0xe (1110).. RUNNING: 接受新任务
    * 0x0 (0000).. SHUTDOWN: 关闭, 不接受新任务, 但继续执行队列的任务
    * 0x2 (0010).. STOP: 停止, 不接受新任务, 中断所有任务
    * 0x4 (0100).. TIDYING: 队列为空或者所有task被终止, 线程池为空, 执行terminated()方法
    * 0x6 (0110).. TERMINATED: terminated()方法执行结束
* 状态转移
    * RUNNING -> SHUTDOWN: 执行shutdown();
    * (RUNNING or SHUTDOWN) -> STOP: shutdownNow()
    * SHUTDOWN -> TIDYING: 队列和线程池都为空(处理完毕)
    * STOP -> TIDYING: 线程池为空
    * TIDYING -> TERMINATED: terminated()方法处理完成
* 方法实现
    - `addWorker`
        ```java
        private boolean addWorker(Runnable firstTask, boolean core) {

            // 两层循环: 尝试自增workerCount
            //     第一层: 检查线程池状态
            //     第二层: 检查workCount是否超出
            retry:
            for (;;) {
                
                int c = ctl.get();
                int rs = runStateOf(c);

                // 线程池要关闭了, 退出, 失败
                if (rs >= SHUTDOWN &&       // STOP, TYDING, TERMINATED 时不能继续加入
                    ! (rs == SHUTDOWN &&    // SHUTDOWN时, 如果workQueue不为空, 这种情况下可以加入不带task的Worker把剩余任务完成
                    firstTask == null &&    
                    !workQueue.isEmpty()))  // 为什么非空时不加入? 其它线程可以把剩下的任务处理完
                    return false;

                // 小循环
                for (;;) {
                    
                    // 检查workerCount是否超出, 超出则失败
                    int wc = workerCountOf(c);
                    if (wc >= CAPACITY ||
                        wc >= (core ? corePoolSize : maximumPoolSize))
                        return false;
                    
                    // CAS尝试增加worker数, 增加成功才能退出大循环
                    if (compareAndIncrementWorkerCount(c))
                        break retry;

                    // 再次检查线程池状态, 如果改变, 回到大循环检查状态
                    c = ctl.get();
                    if (runStateOf(c) != rs)
                        continue retry;
                }
            }

            boolean workerStarted = false;
            boolean workerAdded = false;
            Worker w = null;

            try {

                // 创建线程
                w = new Worker(firstTask);
                final Thread t = w.thread;

                // worker带的线程不能为空
                if (t != null) {
                    final ReentrantLock mainLock = this.mainLock;
                    // 尝试把新worker加入workers集, 在这之前要取得 mainLock
                    mainLock.lock();
                    try {
                        // 取得 mainLock以后再检查一下状态
                        int rs = runStateOf(ctl.get());
                        if (rs < SHUTDOWN ||
                            (rs == SHUTDOWN && firstTask == null)) {
                            if (t.isAlive())                                // 线程不能被启动过
                                throw new IllegalThreadStateException();
                            
                            // 加入到workers集, 并更新largestPoolSize
                            workers.add(w);
                            int s = workers.size();
                            if (s > largestPoolSize)        // [2]
                                largestPoolSize = s;
                            workerAdded = true;
                        }
                    } finally {
                        mainLock.unlock();
                    }

                    // 加入了以后尝试启动worker
                    if (workerAdded) {      
                        t.start();
                        workerStarted = true;
                    }
                }
            } finally {
                // 如果没有成功启动worker, 还要再加锁把worker再去掉, 可能原因
                //      1. worker的线程启动过
                //      2. 线程池被关闭
                if (! workerStarted)
                    addWorkerFailed(w);
            }
            return workerStarted;
        }

        private void addWorkerFailed(Worker w) {
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                if (w != null)
                    workers.remove(w);
                decrementWorkerCount();     // 循环cas将work数减1
                tryTerminate();             // 尝试终止(RUNNING状态时不会成功)
            } finally {
                mainLock.unlock();
            }
        }   
    - execute
        ```java
        /**
            如果task不能被提交, 可能因为shutdown也可能因为容量达到上限,
            然后task由RejectedExecutionHandler负责处理
        */
        public void execute(Runnable command) {
            if (command == null)
                throw new NullPointerException();


            // 1. 如果worker数少于核心worker数(即使空闲), 就增加核心worker, 尝试将任务交给新worker, 然后返回
            int c = ctl.get();
            if (workerCountOf(c) < corePoolSize) {
                if (addWorker(command, true))       // true 表示核心
                    return;
                c = ctl.get();
            }
            
            // 运行到此, 说明workerCount >= corePoolSize, 不用加核心线程, 先加入队列
            //      RUNNING中, 尝试将command加入workQueue
            if (isRunning(c) && workQueue.offer(command)) {

                // 重检查运行状态, 防止刚好被shutdown或者stop
                int recheck = ctl.get();
                if (! isRunning(recheck) && remove(command))
                    reject(command);
                
                // 如果worker数为0, 增加worker
                else if (workerCountOf(recheck) == 0)
                    addWorker(null, false);
                
                // 运行到此, command已加入workQueue
            }

            // 核心worker已满, workQueue已满, 加入普通worker, 失败则交给RejectedExecutionHandler处理
            // addWorker中会再检查是否RUNNING
            else if (!addWorker(command, false))
                reject(command);
        }
        ```
    - runWorker
        ```java
        /**
            runWorker是DefaultThreadFactory为worker生成的线程中的Runnable 的run方法的内容
        */
        final void runWorker(Worker w) {
            Thread wt = Thread.currentThread();

            // 取下task
            Runnable task = w.firstTask;
            w.firstTask = null;

            w.unlock();                                 // 将state从初始(-1)改成0, 表示可以被中断
            boolean completedAbruptly = true;
            try {
                
                // 如果没有firstTask, 要去workQueue取task
                // getTask()返回null时, 说明该worker已经被抛弃了, run()结束
                while (task != null || (task = getTask()) != null) {            
                    w.lock();

                    // [1]
                    // 1. 如果是被STOP而中断, 重新给线程置位中断标志, 保证task被中断
                    // 2. 如果不是被STOP, 清理掉中断位
                    // 3. 重检查, 发现如果是STOP (shutdownNow刚好发生) 那还要再给线程置中断位, 否则该线程收不到接下来的中断消息
                    if ((runStateAtLeast(ctl.get(), STOP) ||    // 1.  
                        (Thread.interrupted() &&                // 2.
                        runStateAtLeast(ctl.get(), STOP))) &&   // 3.
                        !wt.isInterrupted())                    // 4.
                        wt.interrupt();
                    try {
                        beforeExecute(wt, task);                // hook方法
                        Throwable thrown = null;
                        try {
                            task.run();
                        } catch (RuntimeException x) {
                            thrown = x; throw x;
                        } catch (Error x) {
                            thrown = x; throw x;
                        } catch (Throwable x) {
                            thrown = x; throw new Error(x);     // 将Throwable包在Error中
                        } finally {
                            afterExecute(task, thrown);         // hook方法
                        }
                    } finally {
                        // 不管有没run()成功, 都要丢弃上一个task
                        task = null;
                        w.completedTasks++;
                        w.unlock();
                    }
                }
                completedAbruptly = false;
            } finally {
                // 如果是task.run()有异常导致exit, 则completedAbruptly为true
                processWorkerExit(w, completedAbruptly);
            }
        }
        // [1] 假定task设计良好, 在进入run()时会检查中断位, 在发现被中断时会立即抛出异常
        //     在这一前提下, STOP发生时, 在getTask()方法中被waitQueue.take()方法阻塞的线程会被立即中断
        //     对于运行到[1]处的worker, [1]处的条件保证worker在进行task.run()方法时立即抛出中断异常, 触发worker的回收


        private Runnable getTask() {
            boolean timedOut = false; // Did the last poll() time out?

            for (;;) {
                int c = ctl.get();
                int rs = runStateOf(c);

                // shutdown并且任务已空, 则 workerCount-1, 返回
                if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {                    // [1]
                    decrementWorkerCount();
                    return null;
                }

                int wc = workerCountOf(c);

                // 开启对核心worker超时的检查或者worker数超出核心worker数时, 下面的步骤会检查超时
                boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;

                // worker数大于最大worker数 (worker数被setMaximumPoolSize()方法更改)
                // 或者超时
                // 则取消掉当前worker (结束worker的run()方法)
                if ((wc > maximumPoolSize || (timed && timedOut))
                    && (wc > 1 || workQueue.isEmpty())) {
                    if (compareAndDecrementWorkerCount(c))
                        return null;
                    continue;
                }

                try {
                    Runnable r = timed ?
                        workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                        workQueue.take();                                                       // [2]
                    // 超时的时候如果取到任务, 可重新计时
                    if (r != null)          
                        return r;           
                    
                    // 否则就真的是超时了
                    timedOut = true;
                } catch (InterruptedException retry) {

                    // 中断时重新计时
                    timedOut = false;
                }
            }
        }
        // [1][2] 如果在[1]和[2]之间, workQueue中的work被取完, 那worker会被阻塞在[2]
        //        tryTerminate()中的中断传播可以防止[2]处可能发生的死锁

        private void processWorkerExit(Worker w, boolean completedAbruptly) {
            if (completedAbruptly) // If abrupt, then workerCount wasn't adjusted
                decrementWorkerCount();

            // 操作workers集时需要mainLock
            // 清算worker的task数以后再把worker移出workers集
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                completedTaskCount += w.completedTasks;
                workers.remove(w);
            } finally {
                mainLock.unlock();
            }

            // 尝试terminate(传播信号)
            //      如果当前worker是被tryTerminate()中的interruptIdleWorkers(ONLY_ONE)唤醒的
            //      那它会继续在tryTerminated()用中断唤醒另一个worker
            tryTerminate();

            int c = ctl.get();
            // 线程池还在正常运行, 说明当前worker是因为task.run()的异常或者被抛弃而退出的
            if (runStateLessThan(c, STOP)) {
                // worker被抛弃
                if (!completedAbruptly) {
                    // 允许的最少的线程数
                    int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                    if (min == 0 && ! workQueue.isEmpty())     // workQueue不为空的时候, 至少要有一个worker来把work执行完
                        min = 1;
                    if (workerCountOf(c) >= min)
                        return;
                }
                // 如果worker数已经小于最小的worker数时, 要增加worker
                // 如果是worker是因为task.run()的异常而退出的, 那再加上一个空worker
                addWorker(null, false);
            }
        }
        ```
    * shutdown
        ```java
        public void shutdown() {
            // shutdown要有全局锁
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                // 检查是否对每个worker都有权限进行shutdown,
                // 如果不是会抛出AccessControlException
                checkShutdownAccess();
                // 循环检查+CAS将状态改成SHUTDOWN
                advanceRunState(SHUTDOWN);
                // 向所有空闲(不在task.run()中的)worker发出中断
                interruptIdleWorkers();
                onShutdown();           // hook for ScheduledThreadPoolExecutor
            } finally {
                mainLock.unlock();
            }
            tryTerminate();
        }

        final void tryTerminate() {
            for (;;) {
                int c = ctl.get();
                // 以下情况不能终止:
                if (isRunning(c) ||                                         // RUNNING时不能终止, 未经用户允许
                    runStateAtLeast(c, TIDYING) ||                          // TIDYING以后的阶段无须终止, 因为有其它线程处理过了
                    (runStateOf(c) == SHUTDOWN && ! workQueue.isEmpty()))   // SHUTDOWN且workQueue非空, 不能终止
                    return;
                
                // 运行到此时, 可能的状态:
                //     SHUTDOWN 且 workQueue已空
                //     STOP
                // 需要传播中断, 防止有work在getTask()的workQueue.take()中被死锁
                if (workerCountOf(c) != 0) { 
                    interruptIdleWorkers(ONLY_ONE);     // 中断worker之前要取得worker的锁
                    return;
                }

                final ReentrantLock mainLock = this.mainLock;
                mainLock.lock();
                try {
                    if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {  // 争夺状态, CAS改成TIDYING
                        try {
                            terminated();                   // 执行 hook 方法
                        } finally {
                            ctl.set(ctlOf(TERMINATED, 0));  // 状态改成TERMINATED
                            termination.signalAll();
                        }
                        return;
                    }
                } finally {
                    mainLock.unlock();
                }
                // else retry on failed CAS
            }
        } 
        ```

        private void interruptIdleWorkers(boolean onlyOne) {
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                for (Worker w : workers) {
                    Thread t = w.thread;
                    if (!t.isInterrupted() && w.tryLock()) {    // tryLock()失败说明worker正在task.run(), 不管
                        try {
                            t.interrupt();
                        } catch (SecurityException ignore) {
                        } finally {
                            w.unlock();
                        }
                    }
                    if (onlyOne)
                        break;
                }
            } finally {
                mainLock.unlock();
            }
        }

        ```
    * shutdownNow
        ```java
        public List<Runnable> shutdownNow() {
            List<Runnable> tasks;
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                // 检查是否对每个worker都有权限进行shutdown,
                // 如果不是会抛出AccessControlException
                checkShutdownAccess();
                // 循环检查+CAS将状态改成STOP
                advanceRunState(STOP);
                // 中断所有active状态的worker, 不用管SecurityException
                interruptWorkers();
                tasks = drainQueue();
            } finally {
                mainLock.unlock();
            }
            tryTerminate();
            return tasks;
        }

        /** 
            中断所有以开始的线程
            与interruptIdleWorkers()不同之处在于不用通过tryLock()来试探worker有没在run, 而是直接中断
        */
        private void interruptWorkers() {
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                for (Worker w : workers)
                    w.interruptIfStarted();
            } finally {
                mainLock.unlock();
            }
        }

        /** 
            Worker类的方法
        */
        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
        ```
    * awaitTermination
        ```java
        /**
            超时await一直到TERMINATED(返回true)或者await超时(返回false)或者被中断而抛出中断异常
            await以后, 由调用tryTerminate并抢到TIDYING机会的线程负责唤醒
            该方法的作用类似于join()
        */
        public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
            long nanos = unit.toNanos(timeout);
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                for (;;) {
                    if (runStateAtLeast(ctl.get(), TERMINATED)) // 被唤醒发现已经到TERMINATED了, 返回true
                        return true;
                    if (nanos <= 0)                             // 醒来发现已经超时, 返回false
                        return false;
                    nanos = termination.awaitNanos(nanos);      // 被中断时抛异常
                }
            } finally {
                mainLock.unlock();
            }
        }
        ```
    * setKeepAliveTime
        ```java
        public void setKeepAliveTime(long time, TimeUnit unit) {
            if (time < 0)
                throw new IllegalArgumentException();
            // 核心线程的keepAliveTime不能为0
            if (time == 0 && allowsCoreThreadTimeOut())
                throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
            long keepAliveTime = unit.toNanos(time);
            long delta = keepAliveTime - this.keepAliveTime;
            this.keepAliveTime = keepAliveTime;
            if (delta < 0)                      // 新的keepAliveTime如果比旧的小, 那要把空闲线程叫起来检查一下有没超时
                interruptIdleWorkers();
        }
        ```
    * purge
        ```java
        /**
            清理状态为CANCELLED的task, 如果task为Future类型的
            如果不主动调用该方法来清理, worker在run的时候也会清理掉, 但这些task会累积在workQueue中
        */
        public void purge(); 
        ```

# RejectedExecutionHandler

RejectedExecutionHandler接口
* void rejectedExecution(Runnable r, ThreadPoolExecutor executor)

RejectedExecutionHandler实现
* ThreadPoolExecutor.AbortPolicy: 抛RejectedExecutionException, 告诉调用者中止
    * ThreadPoolExecutor默认的RejectedExecutionHandler
    ```java
    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        throw new RejectedExecutionException("Task " + r.toString() +
                                                " rejected from " +
                                                e.toString());
    }
    ```
* ThreadPoolExecutor.CallerRunsPolicy: 在RUNNING状态时, 直接在调用者的线程中把task执行掉
    ```java
    /**
        只要executor不是处于RUNNING状态, 就直接把task执行掉
    */
    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        if (!e.isShutdown()) {
            r.run();
        }
    }
    ```
* ThreadPoolExecutor.DiscardPolicy: 果断把执行不了的task丢弃掉, 不通知调用者
    * rejectedExecution方法为空 
* ThreadPoolExecutor.DiscardOldestPolicy: 如果executor还在RUNNING状态, 那把最老的一个task丢掉, 然后尝试加入新的
    ```java
    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        if (!e.isShutdown()) {
            e.getQueue().poll();        // 把最老任务的丢掉
            e.execute(r);               // [1] 再尝试execute
        }
    }
    // [1] 如果再失败, execute()中还会再调用一次rejectedExecution, 也就是说一直尝试到成功为止
    ```