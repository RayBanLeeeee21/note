SelectionKey
* OPs:
    * OP_READ
    * OP_WRITE
    * OP_CONNECT
    * OP_ACCEPT
* 主要属性
    ```java
    final SelChImpl channel;                // 关联的Channel
	public final SelectorImpl selector;     // 关联的selector
	private int index;
	private volatile int interestOps;       // 记录感兴趣的操作
	private int readyOps;                   // 记录准备好的操作
    ```


# ServerSocketChannel
* 机制:
    * Selector, SelectionKey与SocketChannel:
        * 一个Selector可以与多个SelectionKey关联, 一个SelectionKey只能与一个Selector关联
        * 一个SocketChannel可以与多个SelectionKey关联, 一个SelectionKey只能与一个SocketChannel关联
        * Selector通过不同的SelectionKey间接与不同的Channel关联, 从而可以通知不同的Channel
        * SocketChannel通过不同的SelectionKey间接与不同的Selector关联, 从而可以接收不同的通知
        * key用于表示SocketChannel与Selector的关联关系
* 属性:
    * private SelectionKey[] keys: 保存已注册的Selector对应的SelectionKey

## 方法实现 
register
*   ```java
    // 方法继承自AbstractSelectableChannel
    public final SelectionKey register(Selector sel, int ops,
                                       Object att)
        throws ClosedChannelException{
        synchronized (regLock) {
            if (!isOpen())
                throw new ClosedChannelException();
            if ((ops & ~validOps()) != 0)
                throw new IllegalArgumentException();
            if (blocking)
                throw new IllegalBlockingModeException();
            // 如果当前channel已经关联到sel(存在selector为sel的key)
            // 则只要更新key中保存的interestOps
            SelectionKey k = findKey(sel);
            if (k != null) {
                k.interestOps(ops);
                k.attach(att);
            }
            // 未与sel建立关联关系, 调用sel把自己注册为通知对象, 得到一个新key
            // 把新key加进自己的通知者selector数组
            if (k == null) {
                synchronized (keyLock) {
                    if (!isOpen())
                        throw new ClosedChannelException();
                    k = ((AbstractSelector)sel).register(this, ops, att);
                    addKey(k);
                }
            }
            return k;
        }
    }

    /**
     * 在keys数组中判断与特定Selector关联的key是否存在, 不存在则返回null
     */
    private SelectionKey findKey(Selector sel) {
        synchronized (keyLock) {
            if (keys == null)
                return null;
            for (int i = 0; i < keys.length; i++)
                if ((keys[i] != null) && (keys[i].selector() == sel))
                    return keys[i];
            return null;
        }
    }


    /**
     * 添加与当前通道关联的SelectionKey
    */
    private void addKey(SelectionKey k) {
        assert Thread.holdsLock(keyLock);
        int i = 0;
        if ((keys != null) && (keyCount < keys.length)) {
            // Find empty element of key array
            for (i = 0; i < keys.length; i++)
                if (keys[i] == null)
                    break;
        } else if (keys == null) {
            keys =  new SelectionKey[3];
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

    /**
     * Selector负责创建一个SelectionKey, 保存interestOps, 并加到自己的keys集合中, 最后返回SelectionKey给Channel
    */
    protected final SelectionKey register(AbstractSelectableChannel paramAbstractSelectableChannel, int paramInt, Object paramObject){
        if (!(paramAbstractSelectableChannel instanceof SelChImpl)) {
            throw new IllegalSelectorException();    
        }
        SelectionKeyImpl localSelectionKeyImpl = new SelectionKeyImpl((SelChImpl)paramAbstractSelectableChannel, this);
        localSelectionKeyImpl.attach(paramObject);
        synchronized (publicKeys)    {
            implRegister(localSelectionKeyImpl);
        }
        localSelectionKeyImpl.interestOps(paramInt);
        return localSelectionKeyImpl;
    }
    ```
* Selector.selector
