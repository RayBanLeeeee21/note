## 4.1 什么是线程

线程属性:
* 优先级
* 状态
* 线程名
* 线程ID
* 线程组
* isDaemon

### <c>4.1.3 线程优先级</c>

Java线程优先级:
* 相关常量/方法
    * MIN_PRIORITY : int
    * NORM_PRIORITY : int
    * MAX_PRIORITY : int
    * setPriority(int)
    * getPriority()
* 机制:
    * 功能受限于具体平台
* 注意:
    * 对频繁阻塞的线程要设置较高优先级, 对偏重计算的设置较低优先级
    * 程序正确性不能依赖于线程优先级


### <c>4.1.4 线程状态</c>
线程状态:
* 相关常量/方法: 
    * Thread.getState()
    * Thread.isAlive() // 在state为NEW或TERMINATED时返回true
* 状态分类:
    * NEW
    * RUNNABLE(running, ready)
    * WAITING
    * TIME_WAITING
    * BLOCKED
    * TERMINATED

### <c>4.1.4 Daemon线程</c>
Daemon线程:
* 机制:
    * **默认继承**自创建线程
    * 只有在!isAlive()时可以setDaemon, 否则IllegalThreadStateException
* 注意:
    * 不能靠finally域来确保关闭资源

## 4.2 启动和终止线程
### <c>4.2.1 构造线程<\c>
* 默认属性:
    * parent自动设为currentThread
    * ThreadGroup默认继承
    * daemon默认继承
    * priority默认继承
    * ContextClassLoader默认继承
    * ThreadLocal默认继承
    * tid自增
    
### <c>4.3.1 volatile 和 synchronized

synchronized:
* 同步块实现: monitorenter 与 monitorexit指令
* 同步方法实现: 方法修饰符的ACC_SYNCRONIZED标记实现
    * 静态同步方法锁Class对象
    * 实例同步方法锁实例本身

### <c>4.3.2 等待通知机制
* wait/notify:
    * 必须将对象加锁才能使用wait/notify, 否则IlegalMonitorStateException
    * notify调用后, 要等到退出同步块/同步方法, 才能使调用wait()的对象有机会从wait()返回
    * notify调用并退出同步块/同步方法后, 被notify()的方法从**等待队列**加入**同步队列**去竞争.
* e.g.:
    ```Java
    // Consuner线程
    synchronized (lock){
        while(!flag){
            lock.wait();
        }
    }

    // Producer线程
    synchronized (lock){
        doWork();
        flag = true;
        lock.notifyAll();
    }
    ```
### <c>4.3.5</c> Thread.join()的使用
join:
* 机制:
    * 通过**等待/超时机制**和**wait(long)**实现
* happens-before规则:
    * thread线程运行结束happens-before 于thread.join()的成功返回

## 线程应用实例
* e.g.:
    ```java
    synchronize(lock){
        long future = System.currentTimeMillis() + remaining;
        while(!ready && (remaining = future - System.currentTimeMillis()) > 0){
            lock.wait(remaining);
        }
        doWork();
    }
    ```