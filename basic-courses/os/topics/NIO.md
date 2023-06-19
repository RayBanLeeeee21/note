
## IO模型

参考:
- [100%弄明白5种IO模型](https://zhuanlan.zhihu.com/p/115912936)
- [IO多路复用的三种机制Select，Poll，Epoll](https://www.jianshu.com/p/397449cadc9a)
- [epoll原理详解及epoll反应堆模型](https://blog.csdn.net/daaikuaichuan/article/details/83862311)


同步&阻塞:
- 同步就是一个任务的完成需要依赖另外一个任务时，只有等待被依赖的任务完成后，依赖的任务才能算完成，这是一种可靠的任务序列

AIO与NIO的区别: read / write可以是非阻塞 & 回调式的
```java
// java.nio.channels.AsynchronousServerSocketChannel


    public abstract <A> void accept(A attachment,
                                CompletionHandler<AsynchronousSocketChannel,? super A> handler); // 

// java.nio.channels.AsynchronousSocketChannel

    public abstract <A> void read(ByteBuffer dst,
                                  long timeout,
                                  TimeUnit unit,
                                  A attachment,
                                  CompletionHandler<Integer,? super A> handler);

    public abstract <A> void write(ByteBuffer src,
                                   long timeout,
                                   TimeUnit unit,
                                   A attachment,
                                   CompletionHandler<Integer,? super A> handler);
```