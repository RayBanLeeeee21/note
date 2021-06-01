

参考
- [分布式系统一致性（ACID、CAP、BASE、二段提交、三段提交、TCC、幂等性）原理详解](https://blog.csdn.net/qq_31854907/article/details/92796788)

# SAGA
参考:
- [一篇搞定！10分钟说透Saga分布式事务](https://developer.51cto.com/art/202103/650597.htm?mobile)
- [分布式事务：Saga模式](https://www.cnblogs.com/tianyamoon/p/11969089.html)

为什么SAGA不满足A和I, 而TCC满足?
- SAGA的子事务对于单点来说是完整的数据库事务, 可以被其它事务看到. 但其实事务看到的这个提交结果可能只是个中间状态, 而后面又被回滚掉
- TCC进入TRY阶段预留资源后, 其它TCC事务在发生竞争时, 可以知道该资源处于TRY阶段(中间阶段), 然后选择放弃或者阻塞等待