


# ThreadPoolExecutor
* 状态:
    * 0xe0.. RUNNING: 接受新任务
    * 0x00.. SHUTDOWN: 关闭, 不接受新任务, 但继续执行队列的任务
    * 0x20.. STOP: 停止, 不接受新任务, 中断所有任务
    * 0x40.. TIDYING: 队列为空或者所有task被终止, 线程池为空, 执行terminated()方法
    * 0x40.. TERMINATED: terminated()方法执行结束
* 状态转移
    * RUNNING -> SHUTDOWN: 执行shutdown();
    * (RUNNING or SHUTDOWN) -> STOP: shutdownNow()
    * SHUTDOWN -> TIDYING: 队列和线程池都为空(处理完毕)
    * STOP -> TIDYING: 线程池为空
    * TIDYING -> TERMINATED: terminated()方法处理完成
* execute
    ```java
    /**
     * Executes the given task sometime in the future.  The task
     * may execute in a new thread or in an existing pooled thread.
     
     * If the task cannot be submitted for execution, either because this
     * executor has been shutdown or because its capacity has been reached,
     * the task is handled by the current {@code RejectedExecutionHandler}.
     *
     * @param command the task to execute
     * @throws RejectedExecutionException at discretion of
     *         {@code RejectedExecutionHandler}, if the task
     *         cannot be accepted for execution
     * @throws NullPointerException if {@code command} is null
     */
    /**
        如果task不能被提交, 可能因为shutdown也可能因为容量达到上限,
        然后task由RejectedExecutionHandler负责处理
    *
    public void execute(Runnable command) {
        if (command == null)
            throw new NullPointerException();
        /*
         * Proceed in 3 steps:
         *
         * 1. If fewer than corePoolSize threads are running, try to
         * start a new thread with the given command as its first
         * task.  The call to addWorker atomically checks runState and
         * workerCount, and so prevents false alarms that would add
         * threads when it shouldn't, by returning false.
         *
         * 2. If a task can be successfully queued, then we still need
         * to double-check whether we should have added a thread
         * (because existing ones died since last checking) or that
         * the pool shut down since entry into this method. So we
         * recheck state and if necessary roll back the enqueuing if
         * stopped, or start a new thread if there are none.
         *
         * 3. If we cannot queue task, then we try to add a new
         * thread.  If it fails, we know we are shut down or saturated
         * and so reject the task.
         */
        int c = ctl.get();
        // 如果worker少于核心线程数, 就增加worker, 尝试将任务交给新worker
        if (workerCountOf(c) < corePoolSize) {
            if (addWorker(command, true))
                return;
            c = ctl.get();
        }
        if (isRunning(c) && workQueue.offer(command)) {
            int recheck = ctl.get();
            if (! isRunning(recheck) && remove(command))
                reject(command);
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);
        }
        else if (!addWorker(command, false))
            reject(command);
    }

    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (;;) {
            
            // 获取ctl
            int c = ctl.get();
            int rs = runStateOf(c);

            // STOP, TYDING, TERMINATED不能增加
            // SHUTDOWN时,?
            if (rs >= SHUTDOWN &&
                ! (rs == SHUTDOWN && 
                   firstTask == null &&
                   !workQueue.isEmpty()))
                return false;

            for (;;) {
                // 如果worker数量过多, 则添加失败
                int wc = workerCountOf(c);
                if (wc >= CAPACITY ||
                    wc >= (core ? corePoolSize : maximumPoolSize))
                    return false;
                
                // CAS尝试增加worker
                if (compareAndIncrementWorkerCount(c))
                    break retry;
                // 尝试失败先检查状态
                c = ctl.get();

                // 如果状态更新, 那要重新开始大循环
                if (runStateOf(c) != rs)
                    continue retry;
                // 状态不变, 继续尝试小循环
            }
        }

        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            w = new Worker(firstTask);
            final Thread t = w.thread;
            if (t != null) {
                final ReentrantLock mainLock = this.mainLock;
                // 取得 mainLock
                mainLock.lock();
                try {
                    // 取得 mainLock以后再检查一下状态
                    int rs = runStateOf(ctl.get());
                    if (rs < SHUTDOWN ||
                        (rs == SHUTDOWN && firstTask == null)) {
                        if (t.isAlive()) // precheck that t is startable
                            throw new IllegalThreadStateException();
                        
                        // 加入到worker队列
                        workers.add(w);
                        int s = workers.size();
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                        workerAdded = true;
                    }
                } finally {
                    mainLock.unlock();
                }
                if (workerAdded) {
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            // 任务开始失败, 把worker从队列中去掉
            if (! workerStarted)
                addWorkerFailed(w);
        }
        return workerStarted;
    }
    ```