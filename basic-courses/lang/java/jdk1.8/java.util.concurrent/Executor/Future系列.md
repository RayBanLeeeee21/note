[](http://www.importnew.com/25286.html)



## 相关接口
``` java
public interface Executor {
    void execute(Runnable command);
}

/**
    与Runnable相比, Callable有返回值, 且可以捕捉异常
*/
public interface Callable<V> {
    V call() throws Exception;
}


public interface RunnableFuture<V> extends Runnable, Future<V> {
    void run();
}

```

Future接口
- `boolean cancel(boolean mayInterruptIfRunning);`
    - 成功cancel或者状态为NEW时, 返回true, 然后不能再被run()
    - 状态已经完成或者已经cancel的时候返回false
    - 参数为true时会中断线程
- `isDone()`: 执行完成(非NEW)
- V get() throws InterruptedException, ExecutionException: 
    - 阻塞等待结果, 直到等到结果, 中断或者其它异常
    - 除了两个声明的受查异常以外, 还有CancellationException
    - ExecutionException中包装异常原因
- V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;
    - 阻塞等待结果, 直到等到结果, 中断, 超时异常或者其它异常

状态
- 状态(带ING的都是**过渡状态**, 时间比较短)
    - `0-NEW`: 创建未运行, 或者运行中 (两个子状态由runner是否为null来区分)
    - `1-COMPLETING`: 运行结束或者异常, 结果(异常)未保存到outcome
    - `2-NORMAL`: 结果保存到了outcome
    - `3-EXCEPTIONAL`: 异常保存到了outcome
    - `4-CANCELLED`: 用户调用cancel(false)
    - `5-INTERRUPTING`: 用户调用cancel(true), 但任务还未被中断
    - `6-INTERRUPTED`: 用户调用cancel(true), 并且任务已经被中断
- 可能的转移状态
    ```
    NEW--->COMPLETING--> NORMAL
     |          |------> EXCEPTIONAL
     |---->CANCELLED
     |---->INTERRUPTING---->INTERRUPTED
    ``` 


## FutureTask

FutureTask
- 特点:
    - 实现了RunnableFuture接口
    - 如果cancel()时对callable进行中断, 只有run()的线程知道发生了中断, get()线程只能捕捉到CancellationException
- 域:
    - **volatile** int state: 状态
        - 作为互斥量用来控制对callable, outcome的读写
    - Callable<V> callable: 用来保存任务
        - 通过unsafe类的访问来保持可见性
    - Object outcome: 保存callable.call()的结果或者抛出的异常
        - 线程通过抢夺state来取得读写outcome的机会
    - **volatile** Thread runner: 运行callable的线程
        - runner被作互斥量来判断任务是否开始, 开始run()的时候被设为当前线程, 运行结束被设为null
    - **volatile** WaitNode waitNode: 保存等待结果的线程的队列

        
- `run()`
    ```java

    public void run() {
        // 对runner做CAS, 保证只有一个线程能运行run()
            // 成功则进入临界区
        if (state != NEW ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset,                
                                         null, Thread.currentThread()))
            return;
        try {
            Callable<V> c = callable;
            if (c != null && state == NEW) {        // 进入临界区先检查一下状态能不能用
                V result;
                boolean ran;
                try {
                    result = c.call();
                    ran = true;
                } catch (Throwable ex) {
                    result = null;
                    ran = false;
                    // 保存异常
                    setException(ex);               // 设置异常
                }
                if (ran) set(result);               // 设置结果
            }
        } finally {
            
            // 释放runner锁
            runner = null;
            
            // 有线程做了带中断的cancel, 则要yield()到中断处理结束
                // 如果run()没有发生过sleep()/wait(), 可能没法发现中断, 没有InterruptedException
            int s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
    }
    
    
    protected void set(V v) {
        // 尝试把状态变成COMPLETING, 同时判断是否已Cancel, 是的话就退出
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = v;
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); 

            // 如果成功的话, 就要通知所有的等待者
            finishCompletion();
        }
    }

    protected void setException(Throwable t) {
        // CAS尝试把状态变成COMPLETING
            // 尝试失败, 则说明有另一个线程抢到了cancel机会
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = t;
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state

            // 异常也要通知所有的等待者
            finishCompletion();
        }
    }

    /**
        将waiters队列中所有等待结果的非null线程全部唤醒
    */
    private void finishCompletion() {
        
        // 循环竞争CAS(waiters, currentWaiter(即q), null) (与其它finishCompletion竞争)
        for (WaitNode q; (q = waiters) != null;) {
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                for (;;) {
                    Thread t = q.thread;
                    if (t != null) {
                        q.thread = null;
                        LockSupport.unpark(t);
                    }
                    WaitNode next = q.next;
                    if (next == null)
                        break;
                    q.next = null; // unlink to help gc
                    q = next;
                }
                break;
            }
        }

        done();                 // hook方法

        callable = null;        // to reduce footprint
    }
    ```
* runAndReset
    ```java
    /**
        与run()相比, runAndReset()运行完不保存结果, 不改变状态(还是保持为NEW)
        对异常和cancel的处理与run()相同, 不过会返回false表示失败
    */
    protected boolean runAndReset() {
        // state不是NEW(COMPLETING, NORMAL, EXCEPTIONAL, INTERRUPTED, INTERRUPTING CANCELLED)
        // 或者run()的机会被其它线程抢走
        if (state != NEW ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                         null, Thread.currentThread()))
            return false;
        boolean ran = false;
        int s = state;
        try {
            Callable<V> c = callable;
            if (c != null && s == NEW) {
                try {
                    c.call();               // 不保存结果, 因为无法知道结果是不是正确的那个            
                    ran = true; 
                } catch (Throwable ex) {
                    // 出异常时, 把异常保存在outcome
                    setException(ex);
                }
            }
        } finally {
            // runner设为null后, 其它线程才能运行
            runner = null;
            // 被中断时, 循环yield()直到达到INTERRUPTED
            s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
        // 只有不出异常, 不被中断才能算成功
        return ran && s == NEW; 
    }
    ```
* boolean cancel(boolean mayInterruptIfRunning)
    ```java
    public boolean cancel(boolean mayInterruptIfRunning) {
        // 非NEW状态
        // 或者没有抢到cancel的机会, 返回false
        if (!(state == NEW &&
              UNSAFE.compareAndSwapInt(this, stateOffset, NEW,
                  mayInterruptIfRunning ? INTERRUPTING : CANCELLED)))  // 不用中断run()则只把状态设成CANCELLED
            return false;
        // 
        try {    
            // 需要中断run()则给run()的线程发出中断信号
            // 此时当前线程已经抢到state, 不用竞争, run()只需要等待cancel()结束
            if (mayInterruptIfRunning) {
                try {
                    Thread t = runner;
                    if (t != null)
                        t.interrupt();      
                } finally { 
                    // 把状态设置为interrupted (此时其它线程调用set()或者setException()都抢不到机会)
                    UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED); 
                }
            }
        } finally {
            finishCompletion();         // 唤醒所有等待的线程
        }
        // 中断成功
        return true;
    }
    ```
* get
    ```java
    public V get() throws InterruptedException, ExecutionException {
        // 先等到状态达到NORMAL, EXCEPTIONAL或者INTERRUPTING, INTERRUPTED
        int s = state;
        // 还未完成, 则循环阻塞等待, 直到正常(NORMAL)或者异常结束(EXCEPTIONAL)
        if (s <= COMPLETING)
            s = awaitDone(false, 0L);                           // [1]
        // 返回结果之前检查是否被cancel或者出异常   
        // 被cancel要抛CancellationException
        // 出异常要抛new ExecutionException(outcome);
        return report(s);                                       // [2]
    }
    // [1] get()的线程被中断时, 会从[1]处抛出中断异常
    // [2] callable.run()如果内部中断而抛出异常时, get()的线程会从[2]处抛出一个cause为InterruptedException的ExecutionException
    //     也就是说该中断异常本质上与其它类型的callable.run()内部发生的异常无区别;
    //     但如果另一个线程cancel(true), 导致callable.run()抛出中断异常, [2]处只会抛出CancellationException, 中断异常被忽略

    private int awaitDone(boolean timed, long nanos)
        throws InterruptedException {
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        WaitNode q = null;
        boolean queued = false;

        // 循环(检查中断, 状态, 入队, 超时, 都未发生则阻塞)
        for (;;) {
            // 首先检查中断, 刚运行就检查到中断或者LockSupport.park中发生中断, 跳转到此
            // 先把中断标志清除, 然后把自己线程从等待队列中清理掉, 最后抛中断异常
            if (Thread.interrupted()) {
                removeWaiter(q);
                throw new InterruptedException();
            }

            int s = state;
            // 已结束, 直接返回状态
            if (s > COMPLETING) {
                if (q != null)
                    q.thread = null;
                return s;
            }
            // 如果接近完成, 则让步等待
            else if (s == COMPLETING) 
                Thread.yield();
            // 1. 把当前线程保存到WaitNode
            else if (q == null)
                q = new WaitNode();
            // 2. 如果没有入队, 循环CAS尝试加入队头, 入队后才能LockSupport.park()
            else if (!queued)
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                     q.next = waiters, q);
            // 3.1 阻塞等待直到超时, 超时要把自己从等待队列去掉
            else if (timed) {
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L) {
                    removeWaiter(q);                    // 把q的线程设为null, 然后把队列中线程为null的结点都清理掉
                    return state;
                }
                LockSupport.parkNanos(this, nanos);
            }
            // 3.2 阻塞等待
            else
                LockSupport.park(this);                 // 此时如果发生中中断, 只会被置位, 不会抛异常
        }
    }

    private V report(int s) throws ExecutionException {
        Object x = outcome;
        if (s == NORMAL)
            return (V)x;
        if (s >= CANCELLED)
            throw new CancellationException();
        throw new ExecutionException((Throwable)x);
    }

    /**
        把指定结点清理掉, 顺便清理已被中断或者超时的结点
        实现方法: 先把指定结点的线程设为null, 然后把线程为null的结点清理掉
    */
    private void removeWaiter(WaitNode node);
    ```
* 超时get
    ```java
    public V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        if (unit == null)
            throw new NullPointerException();
        int s = state;
        // 状态<=COMPLETING说明超时, 抛超时异常
        if (s <= COMPLETING &&
            (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING) 
            throw new TimeoutException();
        return report(s);
    }
    ```
* 分析:
    * 并发run()时, runner作为互斥量
    * 并发唤醒时, 以state和waiters的状态为互斥量
    * run()和cancel()并发时, 以state为互斥量