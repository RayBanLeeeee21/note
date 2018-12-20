public class Src {


    /**
     *  AbstractSelectableChannel.register(Selector sel, int ops, Object att): SelectionKey 
     */
    public final SelectionKey register(Selector sel, int ops, Object att) throws ClosedChannelException {
        synchronized (regLock) {
            if (!isOpen())                                  // 检查channel是否关闭
                throw new ClosedChannelException();     
            if ((ops & ~validOps()) != 0)                   // 检查ops合法性
                throw new IllegalArgumentException();
            if (blocking)                                   // 只有非阻塞才能注册
                throw new IllegalBlockingModeException();
            SelectionKey k = findKey(sel);                  // 在keys数组查找找sel对应的key是否存在
            if (k != null) {                                
                k.interestOps(ops);                         // 存在则追加interest
                k.attach(att);
            }
            if (k == null) {
                // New registration
                synchronized (keyLock) {                    
                    if (!isOpen())
                        throw new ClosedChannelException();     
                    k = ((AbstractSelector) sel).register(this, ops, att);  // 在sel中注册
                    addKey(k);                              // 把新key加入keys数组
                }
            }
            return k;
        }
    }
}