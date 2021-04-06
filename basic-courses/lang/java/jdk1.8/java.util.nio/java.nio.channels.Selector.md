## 先验知识
[Channel](./java.nio.channels.Channel.md)



### SelectionKey

``SelectionKey``: 用来关联``Selector``, ``Channel``以及感兴趣的操作(interestOps)
* 行为:
    ```java
    SelectableChannel channel();    // 关联的通道
    Selector selector();            // 关联的选择器
    boolean isValid();              // key 是否可用
    void cancel();                  // 使key失效, 然后selector会将其移到CancelledKey集

    int interestOps();              // interestOps的getter
    interestOps(int ops);           // interestOps的setter
    int interestOpsOr(int ops);     // 将当前的ops与给定的ops相或, 并返回新的ops
    int interestOpsAnd(int ops);    // 将当前的ops与给定的ops相与, 并返回新的ops
    int readyOps();                 // 准备好的OP(readyOp)的getter

    boolean isReadable();           // 是否准备好进行OP_READ操作
    boolean isWritable();           // 是否准备好进行OP_WRITE操作
    boolean isConnectable();        // 是否准备好进行OP_CONNECT操作
    boolean isAcceptable();         // 是否准备好进行OP_ACCEPT操作

    Object attach(Object ob);       // 原子地对attachment执行 getAndset(ob)
    Object attachment();            // 返回attachment
    ```