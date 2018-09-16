## ExecutorCompletionService
CompletionService接口

* Future<V> submit(Callable<V> task)
* Future<V> submit(Runnable task, V result): 将任务交给executor, 执行完可以得到结果
* Future<V> take() throws InterruptedException: 阻塞等待结果(可中断)
* Future<V> poll(): 尝试取得结果, 尝试失败返回null
* Future<V> poll(long timeout, TimeUnit unit) throws InterruptedException
    * 超时等待取得结果, 可被中断

ExecutorCompletionService
* 域:
    ```java
        private final Executor executor;
        private final AbstractExecutorService aes;
        private final BlockingQueue<Future<V>> completionQueue;
    ```
* QueueingFuture
    ```java
        /**
        *  QueueingFuture会在运行结束后, 把运行完的task(Callable)加入外部类的队列
        */
        private class QueueingFuture extends FutureTask<Void> {
            QueueingFuture(RunnableFuture<V> task) {
                super(task, null);
                this.task = task;
            }
            protected void done() { completionQueue.add(task); }
            private final Future<V> task;
        }
    
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
* 其它方法
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
