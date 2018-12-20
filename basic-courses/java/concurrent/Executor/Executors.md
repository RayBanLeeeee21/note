线程池
* newCachedThreadPool()
    * 实现方法: 利用ThreadPoolExecutor
        * 核心线程数为0
        * 最大线程数为Integer.MAX_VALUE
        * keepAliveTime = 60 second
    * 特点:
        * 所有线程在空闲60s后被销毁
    ```java
    public static ExecutorService newCachedThreadPool() {
        return new ThreadPoolExecutor(
            0, Integer.MAX_VALUE,               
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<Runnable>());
    }
    ```
* newFixedThreadPool(int)
    * 实现方法: 利用ThreadPoolExecutor
        * 核心线程数与最大线程数都为nThreads
        * keepAliveTime = 0
    ```java
    public static ExecutorService newFixedThreadPool(int nThreads) {
        return new ThreadPoolExecutor(
            nThreads, nThreads,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());
    }
    ```
* newSingleThreadExecutor(int)
    * 实现方法: 利用ThreadPoolExecutor
        * 核心线程数与最大线程数都为1
        * keepAliveTime = 0
    ```java
    public static ExecutorService newSingleThreadExecutor() {
        return new FinalizableDelegatedExecutorService
            (new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>()));
    }
    ```