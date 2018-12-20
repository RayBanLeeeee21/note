java.lang.Thread

* 构造器
    * Thread()
    * Thread(Runnable)
    * Thread(ThreadGroup, Runnable)
    * Thread(String)
    * Thread(ThreadGroup, String)
    * Thread(Runnable, String)
    * Thread(ThreadGroup, Runnable, String)
    * Thread(ThreadGroup, Runnable, String, long)
* 实例
    * 属性:
        * 线程id
            * getId()
        * 线程名
            * setName(String)
            * getName()
        * 线程组
            * getThreadGroup()
        * 状态
            * getState()
            * isAlive()
        * daemon
            * setDaemon(boolean)
            * isDaemon()
        * 优先级
            * setPriority(int)
            * getPriority()
        * 中断
            * isInterrupted();
        * ContextClassLoader
            * getContextClassLoader()
            * setContextClassLoader(ClassLoader)
        * UncaughtExceptionHandler
            * getUncaughtExceptionHandler()
            * setUncaughtExceptionHandler(UncaughtExceptionHandler)
    * 行为
        * 启动
            * start()
        * 同步运行
            * run() 
        * 中断
            * interrupt()
        * join
            * join(long)
            * join(long, int)
            * join()
        * 线程控制 (deprecated)
            * suspend()
            * resume()
            * destroy()
            * stop()
            * stop(Throwable)
* 静态
    * 常量:
        * 优先级
            * MIN_PRIORITY : int
            * NORM_PRIORITY : int
            * MAX_PRIORITY : int
    * 行为
        * 当前线程相关
            * currentThread()
            * yield()
            * sleep(long)
            * sleep(long, int)
        * 未分类
            * activeCount()
            * enumerate(Thread[])
            * countStackFrames()
            * dumpStack()
            * checkAccess()
            * getStackTrace()
            * getAllStackTraces()
            * holdsLock(Object)
            * setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler)
            * getDefaultUncaughtExceptionHandler()

## 行为/特性
线程id
* 相关常量/方法
    * getId()
* 机制: 从1开始自增
* 实现: 同步方法实现线程安全的自增

优先级
* 相关常量/方法
    * MIN_PRIORITY : int
    * NORM_PRIORITY : int
    * MAX_PRIORITY : int
    * setPriority(int)
    * getPriority()
* 机制:
    * **默认继承**
* 注意
    * 不能让程序的正确性依赖于优先级
    * 实际效果与平台相关

线程名
* 相关常量/方法
    * setName(String)
    * getName()

daemon
* 相关常量/方法
    * setDaemon(boolean)
    * isDaemon()
* 特点:
    * **默认继承**
* 注意:
    * daemon线程不应该持有文件等资源
    * 只能在!isAlive()时被设置, start()后不可更改

中断
* 相关常量/方法
    * t1.interrupt()
    * Thread.interrupted()    // **静态方法**, 查询当前线程的中断情况并reset (只有本线程能复位本线程)
    * t1.isInterrupted()      // **实例方法**, 供其它线程查询某个线程, **不可复位**

线程组与UncaughtExceptionHandler
* 相关常量/方法
    * getThreadGroup()
    * setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler)
    * getDefaultUncaughtExceptionHandler()
    * getUncaughtExceptionHandler()
    * setUncaughtExceptionHandler(UncaughtExceptionHandler)
* 机制:
    * 线程组**默认继承** (树状继承)
    * 线程组的uncaughtException方法实现 (Thread的异常处理默认步骤)
        1. 祖先线程组的Handler, 否则2
        2. Thread类中定义的默认Handler, 否则3
        3. 打印线程名与捕获的异常信息

状态查询
* 相关常量/方法
    * isAlive()
    * getState()
* 特点:
    * isAlive()在状态为NEW或TERMINATED时返回false

状态切换/线程控制
* 相关常量/方法
    * start()
    * run()
    * yield()
    * join()
    * join(long)
    * join(long, int)
    * sleep(long)
    * sleep(long, int)
    * suspend()         // deprecated
    * resume()          // deprecated
    * stop()            // deprecated
    * stop(Throwable)   // deprecated
    * destroy()         // deprecated
* 机制:
    * **join**: join 实际上通过同步实例方法和wait来实现
        * 竞争对象为被**调用的Thread实例**
        * 等待/超时范式实现 (判断isAlive())
        * Thread实例在run()结束后自动notifyAll()
    * **Object.wait()/Object.notify()**:
        * 必须对对象加锁, 否则IllegalMonitorStateException
        * 调用wait()后释放锁, 进入WAITING或TIME_WAITING状态
        * 被notify()或超时后, WAITING线程从等待队列转到同步(阻塞)队列, 与其它BLOCKED线程参与竞争
        * wait()可以被中断, 但可能被阻塞, 直到阻塞结束后才能处理异常
    * **sleep**
        * sleep(long) 进入TIME_WAITING状态, **不释放锁**
    * InterruptedException会导致中断标记复位


ContextClassLoader
* getContextClassLoader()
* setContextClassLoader(ClassLoader)

其它
* 实例:
    * checkAccess()         // 通过System.getSecurityManager()来检查当前线程有无权限更改this
    * getStackTrace()       // 返回某线程的方法栈

* 静态:
    * currentThread()
    * activeCount()         // 调用Thread.currentThread().getThreadGroup().activeCount()
    * enumerate(Thread[])   // 调用Thread.currentThread().getThreadGroup().enumerate(tarray);
    * countStackFrames()    // deprecated. 只能在suspend时用
    * dumpStack()           
    * holdsLock(Object)     //
    * getAllStackTraces()   // 所有线程的方法栈












