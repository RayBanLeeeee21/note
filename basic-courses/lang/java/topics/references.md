
# 四种引用

四种引用
- 强引用: 不会被gc清理
- 软引用(`SoftReference`): 内存不够时, 会被gc清理, 并被加入用户指定的队列
- 弱引用(`WeekReference`): 只要gc发生时, 就会被gc清理, 并被加入用户指定的队列
- 虚引用(`WeekReference`): 无法通过该虚引用访问到对象(即`ref.get()==null`), 在gc被清理时会被回收并加入用户指定的队列


## Reference演示

### 强引用演示

代码
```java
public static void testStrongRef() {
    System.out.println("test strong ref:");

    Object obj = new Object();
    ThreadUtils.sleepSilently(1000);
    System.gc();
    ThreadUtils.sleepSilently(1000);
    System.out.println("    ref is null: " + (obj == null));
    System.out.println();
}
```

结果:
```log
test strong ref:
    ref is null: false
```

### 软引用演示

代码
```java
public static void testWeakRef() {

    System.out.println("test week ref:");

    int[]arr1 = new int[65536];
    SoftReference<int[]> ref = new SoftReference<>(arr1);
    System.out.println("    ref is null: " + (ref.get() == null));

    int[] arr2 = new int[65536];
    System.out.println("    after allocate arr2 int[65536], ref is null: " + (ref.get() == null));

    System.gc();
    System.out.println("    after sleep ref is null: " + (ref.get() == null));

    arr1 = null;
    System.gc();
    System.out.println("    after deleting pointer, ref is null: " + (ref.get() == null));

    System.gc();
    System.out.println("    after deleting pointer, ref is null: " + (ref.get() == null));

    int[] arr3 = new int[65536];
    System.out.println("    after allocating arr3 int[65536] ref is null: " + (ref.get() == null));
    System.out.println();
}
```

结果:
```
test soft ref:
    ref is null: false
    ref is null: false
    after deleting ptr and gc, ref is null: true
```

### 弱引用演示

代码
```java
public static void testWeakRef() {

    System.out.println("test week ref:");

    int[]arr1 = new int[65536];
    SoftReference<int[]> ref = new SoftReference<>(arr1);
    System.out.println("    ref is null: " + (ref.get() == null));

    int[] arr2 = new int[65536];
    System.out.println("    after allocate arr2 int[65536], ref is null: " + (ref.get() == null));

    System.gc();
    System.out.println("    after sleep ref is null: " + (ref.get() == null));

    arr1 = null;
    System.gc();
    System.out.println("    after deleting pointer, ref is null: " + (ref.get() == null));

    System.gc();
    System.out.println("    after deleting pointer, ref is null: " + (ref.get() == null));

    int[] arr3 = new int[65536];
    System.out.println("    after allocating arr3 int[65536] ref is null: " + (ref.get() == null));
    System.out.println();
}

```

结果:
```
test week ref:
    ref is null: false
    after allocate arr2 int[65536], ref is null: false
    after sleep ref is null: false
    after deleting pointer, ref is null: false
    after deleting pointer, ref is null: false
    after allocating arr3 int[65536] ref is null: true
```

### 虚引用演示

代码
```java
public static void testPhantomRef() {

    System.out.println("test phantom ref:");

    Object obj = new Object();
    ReferenceQueue<Object> queue = new ReferenceQueue<>();
    Reference<Object> ref = new PhantomReference<>(obj, queue);
    System.out.println("    before gc");
    System.out.println("        ref.get() is null: " + (ref.get() == null));
    System.out.println("        ref is in queue: " + ref.isEnqueued());
    System.out.println("        queue.poll(): " + queue.poll());

    System.gc();
    System.out.println("    after gc");
    System.out.println("        ref.get() is null: " + (ref.get() == null));
    System.out.println("        ref is in queue: " + ref.isEnqueued());
    System.out.println("        queue.poll(): " + queue.poll());
    System.out.println();

    obj = null;
    System.gc();
    System.out.println("    after deleting ptr and gc");
    System.out.println("        ref.get() is null: " + (ref.get() == null));
    System.out.println("        ref is in queue: " + ref.isEnqueued());
    System.out.println("        queue.poll(): " + queue.poll());
    System.out.println();
}

```

结果:
```
test phantom ref:
    before gc
        ref.get() is null: true
        ref is in queue: false
        queue.poll(): null
    after gc
        ref.get() is null: true
        ref is in queue: false
        queue.poll(): null

    after deleting ptr and gc
        ref.get() is null: true
        ref is in queue: true
        queue.poll(): java.lang.ref.PhantomReference@3a4afd8d
```



## Reference 源码解析

`java.util.ref.Reference`是引用的抽象, 是`SoftReference`, `WeakReference`, `PhantomReference`的父类(抽象类).

### Reference 类的作用

`Reference`类用于配合GC使用
- 在创建`Reference`时, 可以为其指定一个队列`ReferenceQueue`. 当`Reference`中保存的对象被gc回收时, `Reference`实例会被加入到用户指定的队列中, 而用户通过队列感知到对象被回收, 并且可以通过`ReferenceQueue#poll()`获取`Reference`

### Reference 类实现

要理解`Reference`, 就要理解与`Reference`相关的两个队列:

`Reference.queue`: 
- 用户指定的队列(私有), 通过字段`ref.next`连接.
    ```java
    volatile ReferenceQueue<? super T> queue;   // 实例属性
    volatile Reference next;
    ```

<br/>

`Reference.pending`队列: 
- GC操作的队列(全局), 通过字段`ref.discovered`连接. `Reference`通过这一队列与GC交互

    ```java
    private static Reference<Object> pending = null;   // 静态属性, 表示这个队列是所有reference共用的
    transient private Reference<T> discovered;
    ```
- 当GC发生后, GC会将回收了的`Reference`组织成队列(通过`ref.discovered`连接), 并传递给`Reference.pending`
- `Reference`的类信息被加载时, 会通过静态块启动一个Daemon线程, 定期轮询`pending`队列, 将已回收的`Reference`加入到`queue`:
    ```java

    static {
        // ...
        Thread handler = new ReferenceHandler(tg, "Reference Handler");
        handler.setPriority(Thread.MAX_PRIORITY);
        handler.setDaemon(true);
        handler.start();
    }

    private static class ReferenceHandler extends Thread {
        // ...
        public void run() {
            while (true) {
                tryHandlePending(true);
            }
        }
    }

    static boolean tryHandlePending(boolean waitForNotify) {
        Reference<Object> r;
        Cleaner c;
        try {
            synchronized (lock) {
                if (pending != null) {
                    r = pending;
                    c = r instanceof Cleaner ? (Cleaner) r : null;
                    pending = r.discovered;   // 取下第一个结点 
                    r.discovered = null;   
                } else {
                    if (waitForNotify) {      // 没有pending时, 陷入等待
                        lock.wait();
                    }
                    return waitForNotify;
                }
            }
        } catch (OutOfMemoryError x) {
            Thread.yield();
            return true;
        } catch (InterruptedException x) {
            return true;
        }


        if (c != null) {
            c.clean();
            return true;
        }

        ReferenceQueue<? super Object> q = r.queue;
        if (q != ReferenceQueue.NULL) q.enqueue(r);  // 入队
        return true;
    }
    ```
