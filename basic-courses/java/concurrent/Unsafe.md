[](http://ifeve.com/juc-atomic-class-lazyset-que/)

Unsafe.putOrderedObject:
* 只加入StoreStore屏障, 而不用StoreLoad屏障



Unsafe类功能
* 内存管理:
    * 非volatile读写: get{Type}(Object, long/int) 与 put{Type}(Object, long/int, {type})
        * put的三个参数分别为**域所在对象实例**, **域的内存偏移**和**更新值**
        * Type/type包括8大基本类型和Object(即对象引用)
    * **volatile读写**: get{Type}Volatile(Object, long/int) 与 put{Type}Volatile(Object, long/int, {type})
        * Type/type包括8大基本类型和Object(即对象引用)
* 内存屏障:
    * loadFence(): fence前的load操作必须在fence之前完成
    * storeFence(): fence前的store操作必须在fence之前完成
    * fullFence(): fence前的load/store操作必须在fence之前完成