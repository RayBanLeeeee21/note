### Channel

类头注释:
* 一个通道表示一种可以进行IO操作的实体, 如硬件/文件/网络socket等.
* 有开与关两种状态
    * 创建时打开, 一旦关掉就保持关闭. 
    * 调用已关闭channel的IO操作会造成``ClosedChannelException``
    * ``isOpen()``方法用于测试是否打开
* ``Channel``的实现类应该是线程安全的


行为:
* ``close()``: 
    * 关闭后, 任何IO操作会导致``ClosedChannelException``
    * 该方法是幂等的

扩展接口:
```properties
Channel
|-> AsynchronousChannel
    |-> AsynchronousByteChannel 
    |-> AsynchronousFile
|-> InterruptibleChannel
|-> NetWorkChannel
    |-> MulticastChannel
|-> ReadableByteChannel
    |-> ByteChannel
    |-> ScatteringByteChannel
|-> WritableByteChannel
    |-> ByteChannel
    |-> GatheringByteChannel
```



### 重要的Channel接口

#### 异步通道

``AsynchorousChannel``: 

``AsynchorousByteChannel``:
*   ```java
    <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer,? super A> handler);
    <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer,? super A> handler);
    Future<Integer> read(ByteBuffer dst);
    Future<Integer> write(ByteBuffer src);
    ```

<br/>

``InterruptibleChannel``: 可以被异步中断和close的通道
* 线程在该通道上被IO操作阻塞时, 另一个线程调用``close()``, 会使阻塞线程收到``AsynchronousCloseException``
* 线程在该通道上被IO操作阻塞时, 另一个线程调用阻塞线程的``interrupt()``, 会使阻塞线程收到``CloseByInterruptException``, 同时关闭通道, 并将被中断线程的中断标志置位.
* 线程在该通道上进入IO操作之前, 另一个线程调用阻塞线程的``interrupt()``, 则使用通道的线程进入通道后会立即抛``CloseByInterruptedException``并关闭阻塞, 中断标志也会被置位.

<br/>


#### 网络通道

``NetworkChannel``: 网络Socket通道
* 行为:
    * ``bind()``
    * ``getLocalAddress()``
    * ``setOption()``
    * ``getOption()``
    * ``supportedOptions()``

<br/>

``MulticastChannel``: 待研究

<br/>

#### 字节通道


``ByteChannel``: 可读写字节, 该接口简单地继承了``ReadableByteChannel``和``WritableByteChannel``

<br/>

``ReadableByteChannel``: 可读通道
* 一个线程通过``read()``方法开始读取后, 其它线程调用该方法会阻塞直到上一线程执行完毕.
    * 其它IO操作会不会被阻塞则取决于``Channel``的实现
* ``read()``方法不一定会填完buffer. 在非阻塞的通道中读取时, 可能读取到任意字节, 如
    * ``SocketChannel``的输入缓冲区的已到达的字节, ``FileChannel``中剩余的字节.
* **``read()``返回-1表示已经到了流的结尾(EOF)**

<br/>

``WritableByteChannel``: 可写通道
* 一个线程通过``write()``方法开始写入后, 其它线程调用该方法会阻塞直到上一线程执行完毕.
    * 其它IO操作会不会被阻塞则取决于``Channel``的实现
* ``write()``方法不一定会写完buffer中剩余的字节, 在非阻塞的通道中可能写入任意字节, 如
    * ``SocketChannel``中不超过输出缓冲区剩余空间大小的字节数

<br/>

``ScatterringByteChannel``: 可以将字节分散读入到多个buffer中. 在实现特定协议报文的读取时很实用, 特别是具有定长协议字段的.
* 从通道中读取字节时, 读满一个buffer再读下一个

<br/>

``GatheringByteChannel``: 可以将多个buffer的字节聚集写入到buffer.  在实现特定协议报文的写入时很实用, 特别是具有定长协议字段的.
* 写字节到通道中, 写完一个buffer的剩余字节再写下一个.



### 重要的Channel实现类

#### AbstractInterruptibleChannel

``AbstractInterruptibleChannel``: 可中断的通道
* 原理: 
    * 中断时关闭通道, 是通过在当前线程中设置中断回调方法(``Interruptible``)实现的. 并且保证如果有多个线程操作同一通道时都发生了中断, 只会有一个线程执行``implCloseChannel``.
    * 线程执行``begin()``后必须执行``end()``, 保证当前线程的中断回调被设置以后一定会被清理掉, 如
        ```java
        boolean completed = false;
        try {
            begin();
            completed = ...;    // Perform blocking I/O operation
            return ...;         // Return result
        } finally {
            end(completed);
        }
        ```
* 先验知识: 线程的中断回调方法``Thread.blocker``
    ```java
    //**************** java.lang.Thread 中的方法 ***************

    /**
     *  互斥地设置 blocker (被中断时的回调)
     */
    static void blockedOn(Interruptible b) {
        Thread me = Thread.currentThread();
        // 此处 blocker 的设置与后续 interrupt()方法中 blocker 的使用互斥
        synchronized (me.blockerLock) {
            me.blocker = b;
        }
    }

    public void interrupt() {

        // 此处 blocker 的使用与上文 blockedOn()方法中 blocker 的设置互斥
        if (this != Thread.currentThread()) {
            checkAccess();

            // thread may be blocked in an I/O operation
            synchronized (blockerLock) {
                Interruptible b = blocker;
                if (b != null) {
                    interrupt0();  // 设置中断状态
                    b.interrupt(this);  // 使用 blocker
                    return;
                }
            }
        }

        // 设置中断标志 (this.interrupt0())
        interrupt0();
    }
    ```
* 实现
    ```java
    /**
     * 通过竞争closeLock, 设置Interruptor, 用于记录被阻塞的线程, 并关闭通道(this.implCloseChannel())
     */
    protected final void begin() {
        if (interruptor == null) {
            interruptor = new Interruptible() {
                public void interrupt(Thread target) {

                    // 多个线程执行同一通道(this)的begin时, 分别把自己的interruptor放到自己的线程中
                    //     如果多个线程都有中断, 则会在此处发生竞争
                    //     只有第一个进入临界区的线程会避开 if(closed) 分支, 执行 implCloseChannel()
                    //     其余线程在后续都从 if(closed) 分支中返回了
                    synchronized (closeLock) {
                        if (closed)
                            return;
                        closed = true;
                        interrupted = target;   // 记录被中断的线程
                        try {
                            // 执行实际的close方法
                            AbstractInterruptibleChannel.this.implCloseChannel();
                        } catch (IOException x) { }
                    }
                }};
        }

        // 设置当前线程的中断回调方法 (Thread.blocker)
        //      有可能另一个线程先执行当前线程的 Thread.interrupt()方法, 把这一行阻塞住
        blockedOn(interruptor);

        // 如果在另一个线程中断当前线程后, 上一行才进入blockedOn()的临界区, 
        //      则interruptor没来得及被回调, 需要在此处直接调用, 关闭通道
        Thread me = Thread.currentThread();
        if (me.isInterrupted())
            interruptor.interrupt(me);
    }

    /**
     * 解除 interruptor, 检查是否发生中断, 是否当前线程执行了 this.implCloseChannel() 方法
     */
    protected final void end(boolean completed)
        throws AsynchronousCloseException {

        // 解除 interruptor
        blockedOn(null);   

        // 检查是否发生中断, 以及是否当前线程执行了 this.implCloseChannel() 方法
        //     如果是则抛 ClosedByInterruptException
        Thread interrupted = this.interrupted;
        if (interrupted != null && interrupted == Thread.currentThread()) {
            this.interrupted = null;
            throw new ClosedByInterruptException();
        }

        //     否则抛 AsynchronousCloseException, 表示被其它线程关闭
        if (!completed && closed)
            throw new AsynchronousCloseException();
    }

    /**
     * 关闭通道
     */
    public final void close() throws IOException {
        // 该方法也会与中断回调 interruptor, 以及 begin() 方法发生竞争
        synchronized (closeLock) {
            if (closed)
                return;
            closed = true;
            implCloseChannel();
        }
    }

    static void blockedOn(Interruptible intr) {
        // 该方法实际调用了 Thread.blockedOn(intr), 表示把中断回调设置给当前线程
    }
    
    ```

#### SelectableChannel系列 - 可通过选择器进行多路复用的通道

类头注释: 该通道可通过选择器进行多路复用
* **该通道是线程安全的**
* **注册**: 通过``register()``方法将自己注册到选择器, 并返回一个关联自己与选择器的``SelectionKey``
    * 将通道注册到某个特定的选择器上只能注册一次
* **注销**: 通过调用``deregister()``注销, 注销的时候, 选择器会回收所有它分配给通道的资源
    * 通道不能直接注销, 在注销之前, 代表注册关系的key要先cancel; key被cancel后, 选择器进行下一次选择操作时, 会注解通道
    * key可以被显式调用cancel
    * 关闭通道也可以将所有关联的key都cancel掉, 不管是显式调用close()还是被中断
    * 关闭选择器会使所有关联的通道都注销, 并且使所有关联的key失效
* 阻塞模式: 该通道支持非阻塞模式
    * 通道初始化阻塞模式
    * 如果用于选择器, 则一定要设置成非阻塞

``SelectableChannel``行为:
```java
    /** 返回创建该通道的provider */
    public abstract SelectorProvider provider();

    /** 支持的操作. */
    public abstract int validOps();

    /** 是否注册
        - 由于key的cancel与注销过程之间可能有时延, 在对key进行cancel后
          可能在key.cancel()/channel.close()以后, 还能观察到未注销       */
    public abstract boolean isRegistered(); 
    
    /** 返回最后一次注册到sel时(如果有注册多次的话), 产生的key 
        - 也可能是null, 如果没注册过 */
    public abstract SelectionKey keyFor(Selector sel);

    /** 注册
        - 返回值SelectionKey是注册成功的标志
        - 如果一次selection正在进行中, 该方法被调用, 则key对应的channel不会对该selection作出反应. 新的注册或者对key.ops的改变会在下次selection时才能观察到.
        - 如果configureBlocking()方法正在执行中, 该方法被调用, 则该方法也会被阻塞, 直到阻塞模式被调整完成
        - 如果该方法执行的时候, 通道被关闭, 则返回的key是失效的      */
    public abstract SelectionKey register(Selector sel, int ops, Object att)
        throws ClosedChannelException;

    /** 相当于执行 register(sel, ops, null) */
    public final SelectionKey register(Selector sel, int ops)
        throws ClosedChannelException;

    /** 是否阻塞
        - 通道关闭后, 返回值是未定义的    */
    public abstract boolean isBlocking();

    /** 
        - 如果通道是被注册过的, 将通道改成阻塞模式会抛IllegalBlockingModeException
        - 对阻塞模式的调整, 只影响模式被改变以后的IO操作
        - 该方法可能会被正在进行的IO操作阻塞, 该现象与具体实现有关
        - 该方法与其本身互斥, 也与register()方法互斥 
    */
    public abstract SelectableChannel configureBlocking(boolean block)
        throws IOException;

    /** register()方法与configureBlocking()方法同步的对象    */
    public abstract Object blockingLock();
```

抽象模板类``AbstractSelectableChannel``: 这一层主要负责实现**key的管理**, **注册**和**阻塞模式配置**
* 属性
    ```java
        // 产生通道的provider
        private final SelectorProvider provider;

        // key相关
        private final Object keyLock = new Object();    // 设置key的锁
        private SelectionKey[] keys;                    // 关联的key
        private int keyCount = 0;

        // 注册相关
        private final Object regLock = new Object();    // 用于注册的锁
        private volatile boolean nonBlocking;           // 是否非阻塞
    ```
* 对key进行增删查: key直接放在keys表(数组)中, 新增的时候加到后面, 检索和删除的时候则按顺序找
    ```java
    private void addKey(SelectionKey k) {
        // 检查是否持有 keyLock
        assert Thread.holdsLock(keyLock);

        int i = 0;

        // 容量足够, 则从keys表第0个开始往上找, 直到找到第一个可用的slot
        if ((keys != null) && (keyCount < keys.length)) {
            
            for (i = 0; i < keys.length; i++)
                if (keys[i] == null)
                    break;
        // 未创建keys表, 则创建keys表
        } else if (keys == null) {
            keys = new SelectionKey[2];
        // 扩容, 
        } else {
            // Grow key array
            int n = keys.length * 2;
            SelectionKey[] ks =  new SelectionKey[n];
            for (i = 0; i < keys.length; i++)
                ks[i] = keys[i];
            keys = ks;
            i = keyCount;
        }
        keys[i] = k;
        keyCount++;
    }

    private SelectionKey findKey(Selector sel) {
        // 检查是否持有锁
        assert Thread.holdsLock(keyLock);
        if (keys == null)
            return null;
        
        // 按顺序找
        for (int i = 0; i < keys.length; i++)
            if ((keys[i] != null) && (keys[i].selector() == sel))
                return keys[i];
        return null;
    }

    void removeKey(SelectionKey k) {                    // package-private
        synchronized (keyLock) {
            // 按顺序找, 找到后删除
            for (int i = 0; i < keys.length; i++)
                if (keys[i] == k) {
                    keys[i] = null;
                    keyCount--;
                }
            ((AbstractSelectionKey)k).invalidate();
        }
    }

    ```
* 注册key:
    ```java
    public final SelectionKey register(Selector sel, int ops, Object att) throws ClosedChannelException {

        // 检查操作是否合法
        if ((ops & ~validOps()) != 0)
            throw new IllegalArgumentException();

        // 检查通道是否关闭
        if (!isOpen())
            throw new ClosedChannelException();
        
        synchronized (regLock) {

            // 检查是否为非阻塞模式
                // 该检查必须放在 synchronized块中, 否则可能判断完条件就变了
                // 该方法本身不带锁
            if (isBlocking())
                throw new IllegalBlockingModeException();
            synchronized (keyLock) {
                
                // 重检查: 结果可能会跟上一次检查不一样
                if (!isOpen())
                    throw new ClosedChannelException();

                // 如果key已存在(已注册), 则更新key
                SelectionKey k = findKey(sel);
                if (k != null) {
                    k.attach(att);
                    k.interestOps(ops);

                } 
                // 否则将自己注册到选择器上, 并生成一个新的key, 加到keys表中
                else {
                    k = ((AbstractSelector)sel).register(this, ops, att);
                    addKey(k);
                }
                return k;
            }
        }
    }
    ```
* 关闭通道: 关闭完通道后, 要使所有key都失效
    ```java
    protected final void implCloseChannel() throws IOException {
        // 具体操作
        implCloseSelectableChannel();

        // clone keys to avoid calling cancel when holding keyLock
        SelectionKey[] copyOfKeys = null;
        synchronized (keyLock) {
            if (keys != null) {
                copyOfKeys = keys.clone();
            }
        }

        // 使所有key失效
            // key.cancel()是幂等操作
        if (copyOfKeys != null) {
            for (SelectionKey k : copyOfKeys) {
                if (k != null) {    // 有可能刚好被remove(), 因此要检查
                    k.cancel();   // invalidate and adds key to cancelledKey set
                }
            }
        }
    }
    ```
* 配置阻塞模式
    ```java
    public final SelectableChannel configureBlocking(boolean block)
        throws IOException {
        // 先上锁, 防止与register()方法发生冲突
        synchronized (regLock) {

            // 检查通道是否已经关闭
            if (!isOpen())
                throw new ClosedChannelException();

            // 只有阻塞模式发生变化才操作
            boolean blocking = !nonBlocking;
            if (block != blocking) {
                // 如果有可用的key (即存在关联的selector), 则抛异常
                if (block && haveValidKeys())
                    throw new IllegalBlockingModeException();
                
                // 具体操作
                implConfigureBlocking(block);

                // 更新
                nonBlocking = !block;
            }
        }
        return this;
    }
    ```

#### SocketChannel系列

#### ServerSocketChannel系列