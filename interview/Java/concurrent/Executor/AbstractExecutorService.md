
## AbstractExecutorService
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
            // 把每个Callable任务加入到队列, 并执行
            for (Callable<T> t : tasks) {
                RunnableFuture<T> f = newTaskFor(t);
                futures.add(f);
                execute(f);
            }

            // 这里get()方法的作用类似于join(), 等待所有任务执行结束
            for (int i = 0, size = futures.size(); i < size; i++) {
                Future<T> f = futures.get(i);
                if (!f.isDone()) {
                    try {
                        f.get();
                    } catch (CancellationException ignore) {
                    } catch (ExecutionException ignore) {
                    }
                }
            }
            done = true;
            return futures;
        } finally {
            
            // 如果因为中断而未完成, 则把所有任务calcel()掉
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

            // 一边execute()任务, 一边
            for (int i = 0; i < size; i++) {
                execute((Runnable)futures.get(i));
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L)
                    return futures;
            }

            // 所有都开始execute()以后, 用超时get(long, TimeUnit)方法来等待
            // 这里get()方法的作用类型于join()

            for (int i = 0; i < size; i++) {
                Future<T> f = futures.get(i);
                if (!f.isDone()) {
                    if (nanos <= 0L)
                        return futures;                     // 超时立即返回
                    try {
                        f.get(nanos, TimeUnit.NANOSECONDS);
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
* doInvokeAny
    ```java
    /**
        在一堆任务里面尝试execute()一个其中一个任务, 直到成功完成一个任务, 或者全部失败
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
        ExecutorCompletionService<T> ecs =
            new ExecutorCompletionService<T>(this);

        try {
            
            ExecutionException ee = null;
            final long deadline = timed ? System.nanoTime() + nanos : 0L;
            Iterator<? extends Callable<T>> it = tasks.iterator();

            // 启动第一个任务并加到队列, 任务在run()完后, 结果会加到ecs的结果队列
            futures.add(ecs.submit(it.next()));
            --ntasks;
            int active = 1;

            for (;;) {
                // 尝试从esc获取已提交的future
                Future<T> f = ecs.poll();
                // 如果取不到future
                if (f == null) {
                    // 还有剩余任务, 则再启动一个任务
                    if (ntasks > 0) {
                        --ntasks;
                        futures.add(ecs.submit(it.next()));
                        ++active;
                    }
                    // 没有剩余的任务了, 而之前的任务都异常失败, 跳出循环
                    else if (active == 0)
                        break;
                    // 如果是计时avoke, 那就超时阻塞等待future, 直到拿到future或者抛超时异常
                    else if (timed) {
                        f = ecs.poll(nanos, TimeUnit.NANOSECONDS);
                        if (f == null)
                            throw new TimeoutException();
                        nanos = deadline - System.nanoTime();
                    }
                    // 不是计时invoke则阻塞等待future
                    else
                        f = ecs.take();
                }
                // 拿到future
                if (f != null) {
                    --active;
                    try {
                        // 阻塞获取结果, 结果正常则返回
                        return f.get();
                    } catch (ExecutionException eex) {
                        ee = eex;
                    } catch (RuntimeException rex) {
                        ee = new ExecutionException(rex);
                    }
                }
            }

            if (ee == null)
                ee = new ExecutionException();
            // 返回最后一个exception
            throw ee;

        } finally {
            // cancel()掉所有任务
            for (int i = 0, size = futures.size(); i < size; i++)
                futures.get(i).cancel(true);
        }
    }
    ```