## ExecutorCompletionService
CompletionService接口
* 接口方法:
    * Future<V> submit(Callable<V> task)
    * Future<V> submit(Runnable task, V result): 将任务交给executor, 执行完可以得到结果
    * Future<V> take() throws InterruptedException: 阻塞等待结果(可中断)
    * Future<V> poll(): 尝试取得结果, 尝试失败返回null
    * Future<V> poll(long timeout, TimeUnit unit) throws InterruptedException
        * 超时等待取得结果, 可被中断
* 特点:
    * 会把执行结束(正常, 异常, cancelled)的Future加入到一个阻塞队列中
    * completionQueue默认为LinkedBlockingQueue<Future<V>>

ExecutorCompletionService
* 域:
    ```java
        private final Executor executor;                         // 负责执行任务 (通过调用RunnableFuture的run()方法)
        private final AbstractExecutorService aes;               // 负责提供把Callable包装成RunnableFuture的服务
        private final BlockingQueue<Future<V>> completionQueue;  // 负责保存运行完的结果
    ```
* QueueingFuture
    ```java
        /**
        *  QueueingFuture会在运行完(正常, 异常, cancelled)后, 把运行完的task(Callable)加入外部类的队列
        */
        private class QueueingFuture extends FutureTask<Void> {
            QueueingFuture(RunnableFuture<V> task) {
                super(task, null);
                this.task = task;
            }
            protected void done() { completionQueue.add(task); }
            private final Future<V> task;
        }
    // [1] 如果实例是AbstractExecutorService类型, 那
    ```
* submit
    ```java

        /**
            将任务才QueueingFuture的形式交给exector去执行, 执行完的任务会被加到队列中
            返回被包装的RunnableFuture
        */
        public Future<V> submit(Callable<V> task);
        public Future<V> submit(Runnable task, V result) {
            if (task == null) throw new NullPointerException();
            RunnableFuture<V> f = newTaskFor(task, result);
            executor.execute(new QueueingFuture(f));
            return f;
        }
    ```
* 其它接口方法
    ```java
        public Future<V> take() throws InterruptedException {
            return completionQueue.take();
        }

        public Future<V> poll() {
            return completionQueue.poll();
        }

        public Future<V> poll(long timeout, TimeUnit unit)
                throws InterruptedException {
            return completionQueue.poll(timeout, unit);
        }
    ```
* 非接口方法
    * 如果aes的实际类型是AbstractExecutorService, 那返回的RunnableFuture是FutureTask
    * FutureTask中的callable采用了适配器模式
    ```java
    private RunnableFuture<V> newTaskFor(Runnable task, V result) {
        if (aes == null)
            return new FutureTask<V>(task, result);
        else
            return aes.newTaskFor(task, result);
    }

    // AbstractExecutorService 的newTaskFor方法
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new FutureTask<T>(runnable, value);
    }

    // FutureTask的构造方法
    public FutureTask(Runnable runnable, V result) {
        this.callable = Executors.callable(runnable, result);
        this.state = NEW;       // ensure visibility of callable
    }

    public static <T> Callable<T> callable(Runnable task, T result) {
        if (task == null)
            throw new NullPointerException();
        return new RunnableAdapter<T>(task, result);
    }
    ```

