能否让HashMap实现线程安全:
1. Collections.synchronizeMap(hashMap)
    * 用代理模式对原map进行包装, 用同步块将其方法包装成同步的
2. 改用Hashtable
3. 改用ConcurrentHashMap