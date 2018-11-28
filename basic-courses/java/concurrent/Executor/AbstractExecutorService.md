
## AbstractExecutorService
ExecutorService接口
* 接口方法
    ```java
    void shutdown(): 
        //shutdown后无法再增加新task, 但旧task会被执行完
        //该方法不会被阻塞, 可以调用awaitTermination来等到结束
    List<Runnable> shutdownNow(): 阻止后续task加入, 停止所有task, 返回未启动过的task
    boolean isShutdown();
    boolean isTerminated();
    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;
        //等待到TERMINATED状态
    <T> Future<T> submit(Callable<T> task):
        //把一个Callable包装在一个Future中, 然后执行Callable, 并返回该Future
        //submit()方法调用者可以通过Future来查看Callable的执行状态
    <T> Future<T> submit(Runnable task, T result);
    Future<?> submit(Runnable task);
        //把一个Runnable包装在一个Future中, 然后执行Runnable, 并返回该Future
        //除非发生异常, 否则Future的get()方法返回的是给定的result


        
    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException;
    <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException;
    <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException;
    <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;
    ```
AbstractExecutorService
* invokeAll
    ```java
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException {
        if (tasks == null)
            throw new NullPointerException();
        ArrayList<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean done = false;
        try {
            // 逐个把task加入队列, 并启动(execute())
            for (Callable<T> t : tasks) {
                RunnableFuture<T> f = newTaskFor(t);
                futures.add(f);
                execute(f);
            }

            // get()方法的作用类似于join(), 等待所有任务执行结束
            for (int i = 0, size = futures.size(); i < size; i++) {
                Future<T> f = futures.get(i);
                if (!f.isDone()) {
                    try {
                        f.get();      
                    // [1] CancellationException和ExecutionException异常都被忽略
                    //     但如果当前线程在get()中被中断, InterruptedException的抛出会使当前线程跳转到[2]
                    } catch (CancellationException ignore) {
                    } catch (ExecutionException ignore) {
                    }
                }
            }
            done = true;
            return futures;
        } finally {
            
            // [2] 任一task出现异常导致未完成, 都要把所有任务calcel()掉
            if (!done)
                for (int i = 0, size = futures.size(); i < size; i++)
                    futures.get(i).cancel(true);
        }
    }
    ```
* 超时invokeAll
    ```java
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                         long timeout, TimeUnit unit)
        throws InterruptedException {
        if (tasks == null)
            throw new NullPointerException();
        long nanos = unit.toNanos(timeout);
        ArrayList<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean done = false;
        try {
            // 把任务加入到队列
            for (Callable<T> t : tasks)
                futures.add(newTaskFor(t));

            final long deadline = System.nanoTime() + nanos;
            final int size = futures.size();

            // 一边execute()任务, 一边检查有没超时
            for (int i = 0; i < size; i++) {
                execute((Runnable)futures.get(i));
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L)
                    return futures;
            }

            
            // 这里超时get方法的作用类型于超时join
            for (int i = 0; i < size; i++) {
                Future<T> f = futures.get(i);
                if (!f.isDone()) {
                    if (nanos <= 0L)
                        return futures;                     // 超时立即返回
                    try {
                        f.get(nanos, TimeUnit.NANOSECONDS); // [1] 这里如果发生中断异常会跳转到finally块
                    } catch (CancellationException ignore) {
                    } catch (ExecutionException ignore) {
                    } catch (TimeoutException toe) {
                        return futures;                     // 超时立即返回
                    }
                    nanos = deadline - System.nanoTime();   // 更新时间
                }
            }
            done = true;
            return futures;
        } finally {
            // 如果中断或者超时导致未完成, 要把所有任务取消掉
            if (!done)
                for (int i = 0, size = futures.size(); i < size; i++)
                    futures.get(i).cancel(true);
        }
    }
    ```
* invokeAny
    ```java
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
        try {
            return doInvokeAny(tasks, false, 0);
        } catch (TimeoutException cannotHappen) {
            assert false;
            return null;
        }
    }
    ```
* 超时invokeAny
    ```java
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                           long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        return doInvokeAny(tasks, true, unit.toNanos(timeout));
    }
    ```
* doInvokeAny
    ```java
    /**
        机制:
            给定一堆task, 逐个task尝试启动, 直到其中一个任务成功并得到正常的结果, 则返回结果
            否则抛出最近一个异常
            如果是超时, 则抛出超时异常

        task的状态转换:
            (1)未提交->(2)提交但未结束->(3)结束且成功
            (1)未提交->(2)提交但未结束->(4)结束但失败
        变量语义:
            futures: 保存已提交(2)(3)(4)的task
            ntasks: 未提交(1)的task个数  

            ecs: 保存已结束(3)(4)的task
            active: 提交但未结束(2)(3)的task个数
    */
    private <T> T doInvokeAny(Collection<? extends Callable<T>> tasks,
                              boolean timed, long nanos)
        throws InterruptedException, ExecutionException, TimeoutException {
        if (tasks == null)
            throw new NullPointerException();
        int ntasks = tasks.size();
        if (ntasks == 0)
            throw new IllegalArgumentException();
        ArrayList<Future<T>> futures = new ArrayList<Future<T>>(ntasks);
        ExecutorCompletionService<T> ecs =              // 用来保存运行完毕的task
            new ExecutorCompletionService<T>(this);     // ecs使用的task包装器为this

        try {
            
            ExecutionException ee = null;
            final long deadline = timed ? System.nanoTime() + nanos : 0L;
            Iterator<? extends Callable<T>> it = tasks.iterator();

            // 尝试启动第一个task并加到futures, 
            // futures负责保存已提交的task
            // 而ecs负责保存已结束的task(完成后自动加入)
            futures.add(ecs.submit(it.next()));
            --ntasks;                           // 尚未被启动的task数
            int active = 1;                     // 已提交但未结束的task数

            for (;;) {
                // 尝试从esc获取已提交的future
                Future<T> f = ecs.poll();
                // 取不到结束的task
                if (f == null) {
                    // 还有有未提交的任务, 继续启动
                    if (ntasks > 0) {
                        --ntasks;
                        futures.add(ecs.submit(it.next()));
                        ++active;
                    }
                    // 所有task提交完, 但所有都失败 
                    // (active == 0表示全部失败, 如果有成功的, 那会在active降到0之前返回)
                    else if (active == 0)
                        break;
                    // 所有task提交完, 但并非所有task都结束, 那就等待这个task的结果
                    //    如果是计时invoke, 那就超时阻塞等待, 
                    //    直到拿到task或者抛超时异常
                    else if (timed) {
                        f = ecs.poll(nanos, TimeUnit.NANOSECONDS);
                        if (f == null)
                            throw new TimeoutException();           // 超时直接抛超时异常
                        nanos = deadline - System.nanoTime();
                    }
                    // 不是计时invoke则阻塞等待
                    else
                        f = ecs.take();             // [2]
                }
                // 成功取到一个已结束的task
                if (f != null) {
                    --active;
                    try {
                        // [1] 阻塞获取task的结果, 结果正常则返回, 否则进行下一轮迭代
                        return f.get();
                    } catch (ExecutionException eex) {
                        ee = eex;
                    } catch (RuntimeException rex) {
                        ee = new ExecutionException(rex);
                    }
                }
            }

            // ee用来保存最近一次异常
            if (ee == null)
                ee = new ExecutionException();
            throw ee;

        } finally {
            // cancel()掉所有任务
            for (int i = 0, size = futures.size(); i < size; i++)
                futures.get(i).cancel(true);
        }
    }
    // [1] 对于FutureTask类型的Future, 
    //     在ecs把它加到ecs的队列之前, 状态一定被设为了NORMAL/EXCEPTIONAL/CANCELLED/INTERRUPTED中的一个
    //     那此处的get()方法可能会返回正常结果, 抛CancellationException或者ExecutionException
    // [2] 这里可能会发生抛中断异常
    ```