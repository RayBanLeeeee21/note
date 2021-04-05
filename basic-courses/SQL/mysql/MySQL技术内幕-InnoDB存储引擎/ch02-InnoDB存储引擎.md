# Chapter 02 InnoDB存储引擎

## 2.3 InnoDB体系架构

InnoDB体系架构
- 内存池:
    - 存储进程/后台线程用到的内部数据结构
    - 缓存磁盘中的数据
    - 缓存重做日志(redo log)
- 后台线程: 刷新缓存


### 2.3.1 后台线程

后台线程:
- Master Thread
- IO Thread 
- Purge Thread
- Page Cleaner Thread
<br/>


Master Thread:
- 将数据异步刷新到磁盘, 保持一致性
- 合并插入缓存(Insert Buffer)
- 脏页刷新(老版本)
- 回收UNDO页(老版本)
<br/>


IO Threads: 完成异步IO操作, 然后执行回调
- 四种:
    - read thread 
    - write thread
    - insert buffer thread
    - log IO thread
<br/>


Purge Thread: 回收UNDO页
- 原本由Master Thread完成
<br/>


Page Cleaner Thread: 刷新脏页
- 原本由Master Thread完成


### 2.3.2 内存

先验知识: [缓冲与缓存的区别](https://www.zhihu.com/question/26190832)

缓冲池(Buffer Pool)
- 以页为单位, 按需加载到内存中
- 一个数据库可以有多个缓冲池实例, 减少竞争
- 使用**LRU**算法管理页
- 数据页类型:
    - 索引页
    - 数据页
    - undo页
    - 插入缓冲
    - 自适合哈希索引
    - InnoDB存储的锁信息
    - 数据字典信息
<br/>


LRU算法(Latsest Recent Used)
- 中点插入策略(midpoint insertion strategy): 新加入的结点入到中点而不是队首, 防止把热点页刷掉
    - innodb_old_blocks_pct(37): 默认后3/8 
    - 为什么不放队尾? 有些操作需要遍历所有的页, 如果放队首, 可能把热点页刷掉
    - innodb_old_blocks_time(1000ms): 新加载的页需要等一段时间才能进入new列表

## 2.4 // todo

## 2.6 插入缓冲

