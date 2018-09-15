[](http://www.importnew.com/25286.html)

其它接口
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
* boolean cancel(boolean mayInterruptIfRunning);
    * 成功cancel或者状态为NEW时, 返回true, 然后不能再被run()
    * 状态已经完成或者已经cancell的时候返回false
    * 参数为true时会中断线程
* isDone(): 执行完成(非NEW)
* V get() throws InterruptedException, ExecutionException: 
    * 阻塞等待结果, 直到等到结果, 中断或者其它异常
* V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;
    * 阻塞等待结果, 直到等到结果, 中断, 超时异常或者其它异常

状态
* 状态
    * NEW: 创建未运行
    * COMPLETING: 运行结束或者异常, 结果(异常)未保存到outcome
    * NORMAL: 结果保存到了outcome
    * EXCEPTIONAL: 异常保存到了outcome
    * CANCELLED: 用户调用cancel(false)
    * INTERRUPTING: 用户调用cancel(true), 但任务还未被中断
    * INTERRUPTED: 用户调用cancel(true), 并且任务已经被中断
* 可能的转移状态
    * NEW -> COMPLETING -> NORMAL
    * NEW -> COMPLETING -> EXCEPTIONAL
    * NEW -> CANCELLED
    * NEW -> INTERRUPTING -> INTERRUPTED


FutureTask
* 特点:
    * 实现了RunnableFuture接口
* run()
    ```java
    public void run() {
        // state不是NEW(COMPLETING, NORMAL, EXCEPTIONAL, INTERRUPTED, INTERRUPTING CANCELLED)
        // 或者没有抢到run的机会
        if (state != NEW ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                         null, Thread.currentThread()))
            return;
        try {
            Callable<V> c = callable;
            if (c != null && state == NEW) {
                V result;
                boolean ran;
                try {
                    result = c.call();
                    ran = true;
                } catch (Throwable ex) {
                    result = null;
                    ran = false;
                    // 运行时出异常:
                    //   先把状态CAS写成COMPLETING
                    //   把异常保存在outcome
                    //   把结果CAS写成EXCEPTIONAL
                    //   最后把waiters的线程都唤醒
                    setException(ex);
                }
                if (ran)
                    // 正常运行结束:
                    //   先把状态CAS写成COMPLETING
                    //   把结果保存在outcome
                    //   把结果CAS写成NORMAL
                    //   最后把waiters的线程都唤醒
                    set(result);
            }
        } finally {
            // 运行过程完runner设置为null, 上面对runner进行cas来争夺run的机会
            runner = null;
            
            // 如果是被cancel()方法中断, 很可能是cancel()方法所在的线程负责唤醒和清理
            // 如果快完成中断, 线程一直yield到完成中断(INTERRUPTED)
            int s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
    }


    /**
        将waiters队列中所有非空线程全部唤醒
    */
    private void finishCompletion() {
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
        done();
        callable = null;        // to reduce footprint
    }

    // 唤醒所有结点
    private void finishCompletion() {
        
        // 循环CAS尝试得到唤醒等待线程队列的机会
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

        done(); // hook方法

        callable = null;        // to reduce footprint
    }
    ```
* runAndReset
    ```java
    /**
        run完以后把状态改成NEW, 以便一些可重复run的工作, 但不能保存结果
        出现异常或者中断时不恢复状态
    */
    protected boolean runAndReset() {
        // state不是NEW(COMPLETING, NORMAL, EXCEPTIONAL, INTERRUPTED, INTERRUPTING CANCELLED)
        // 或者没有抢到run的机会, 失败
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
                    c.call(); // don't set result
                    ran = true;
                } catch (Throwable ex) {
                    // 出异常时, 把异常保存在outcome
                    setException(ex);
                }
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;
            // 被中断时, yield当前线程直到达到INTERRUPTED
            s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
        // 只有不出异常, 不被中断才能算成功
        return ran && s == NEW; 
    }
    ```
* get
    ```java
    public V get() throws InterruptedException, ExecutionException {
        // 先等到状态达到NORMAL, EXCEPTIONAL或者INTERRUPTING, INTERRUPTED
        int s = state;
        // 还未完成, 则循环阻塞等待, 直到正常(NORMAL)或者异常结束(EXCEPTIONAL)
        if (s <= COMPLETING)
            s = awaitDone(false, 0L);
        // 返回结果之前检查是否被cancel或者出异常
        // 被cancel要抛CancellationException
        // 出异常要抛new ExecutionException(outcome);
        return report(s);
    }

    private int awaitDone(boolean timed, long nanos)
        throws InterruptedException {
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        WaitNode q = null;
        boolean queued = false;

        // 循环
        for (;;) {
            // 首先检查中断, 如果被中断则要把自己从队列去掉
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
            // 快完成了, yield线程直到NORMAL或者EXCEPTIONAL
            else if (s == COMPLETING) 
                Thread.yield();
            // 1. 把当前线程保存到WaitNode
            else if (q == null)
                q = new WaitNode();
            // 2. 如果没有入队, 循环尝试入队, 入队后才能等待
            else if (!queued)
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                     q.next = waiters, q);
            // 3.1 阻塞等待直到超时, 超时要把自己从等待队列去掉
            else if (timed) {
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L) {
                    removeWaiter(q);
                    return state;
                }
                LockSupport.parkNanos(this, nanos);
            }
            // 3.2 阻塞等待
            else
                LockSupport.park(this);
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
        if (s <= COMPLETING &&
            (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING) // 超时则抛超时异常
            throw new TimeoutException();
        return report(s);
    }
    ```
* boolean cancel(boolean mayInterruptIfRunning)
    ```java
    public boolean cancel(boolean mayInterruptIfRunning) {
        // 非NEW状态
        // 或者没有抢到cancel的机会, 返回false
        if (!(state == NEW &&
              UNSAFE.compareAndSwapInt(this, stateOffset, NEW,
                  mayInterruptIfRunning ? INTERRUPTING : CANCELLED)))  // 不用中断则只把状态设成CANCELLED
            return false;
        // 
        try {    
            if (mayInterruptIfRunning) {
                try {
                    Thread t = runner;
                    if (t != null)
                        t.interrupt();
                } finally { 
                    //如果出现并发问题, 该方法一定会在最后把结果设为INTERRUPTED
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
* 分析:
    * 只有中断, 或者超时时会把某个结点的线程设为null
    * 并发场景:
        * cancel()时, cancel()的线程会和run()的线程争夺更改最终state的机会, 然后争夺唤醒线程的机会