




Java:
* 集合: (底层实现)
    * HashMap
        * [prob]Hashmap为什么大小是2的幂次
    * **ConcurrentHashMap**
        * [prob]锁分段
        * [prob]读是否加锁
        * [prob]迭代器是强一致还是弱一致
    * HashTable
    * HashSet
        * 原理
        * 线程安全
            * 解决方案
    * List
        * ArrayList
        * LinkedList
        * [prob] ArrayList和LinkedList使用场景
    * CopyOnWrite
* IO
    * File IO 都是阻塞IO
    * Socket IO 
        * 阻塞与非阻塞区别
        * 同步异步区别
        * 模型:
            * 阻塞IO
            * 非阻塞IO
            * 多路复用IO
            * 异步IO
            * NIO
* 并发:
    * 进程线程区别
    * **volatile**
    * **synchronized**
        * [prob] 为什么同步块太大会影响性能
        * synchronized加在静态方法上面那么锁指的是什么
    * ReentrantLock CAS
    * AtomicInteger实现原理(CAS机制)
    * 线程池
    * [prob] synchronized与ReentrantLock区别
    * [prob] 列锁排查方法
    * 阻塞队列
    * 线程状态转移图
    * 锁
* Objects方法
* 关键词:
    * static 
    * transient
    * foreach原理
    * [prob] finalize，finally，final区别
* 反射的底层原理
Jvm:
* 内存分区
* GC 
    * 算法
    * GC root
    * [prob] 怎样进入老年代
    * 1.8新特性
* 类加载机制(双亲委派模型)
* Java内存模型
* happens-before规则
* volatile

SQL:
* join
* 优化
* 事务
* 数据结构
* [prob] 数据库索引实现
* **数据库引擎**
* 隔离级别

web框架:
* Spring
    * AOP
    * IoC
    * [prob] autowired与resource区别
* 对比Hibernate Mybatis
* Session
    * 分布式Session
    * Session与Cookie的区别与联系
    * Session实现
* Tomcat
    * 启动
    * web.xml
* HTTP
    * 安全
        * 加密解密
    * Forward Redirect
* 消息队列
* 分布式
* Redis
* [prob] Strus2与SpringMvc区别

数据结构:
* 链表, 栈
    * 找链表的交叉
* 红黑树
* AVL树
* 排序
    * 堆排序
    * 排序稳定
* 动规
* [prob] 数据库索引实现
* [prob] java统计一个文本文件中出现的频率最高的20个单词 (答案: TreeMap)

计网:
* OSI分层
* **TCP**
    * 拥塞控制
    * 长连接
    * 三次握手
* UDP 
    * [prob] UDP如何实现可靠

设计模式:
* 单例模式
* 工厂模式
    * 在spring中应用

项目经历：
   时间：2018.5-至今
   项目名称：在线图像隐写分析系统
   项目简介：
      1. 核心功能：在线检测图像是否隐藏信息。
      2. 其它功能：后台管理（记录查询，图像检测算法管理等）。
   开发框架：
      spring boot, vue, ajax等
   负责内容：
      1. 框架搭建与配置。
      2. 后台功能组件开发（尚未完成）。
      3. 图像检测算法服务组件接口设计（尚未完成）。
      4. 部分图像检测算法的设计与实现。

项目经历：
   时间：2017.4-2017.5
   项目名称：AVS2视频格式编解码器
   项目简介：
      AVS2视频格式的编解码。
   开发框架：
      CUDA
   负责内容：
      1. 去块效应算法的并行化实现。

实习经验：
   时间：2016.5-2016.7
   公司：广州朗识计算机测评有限公司
   职位：java开发实习生	
   开发框架：
      spring, mybatis, ajax等
   内容：
      1. 开发网站后台功能相关组件。
      2. 邮件发送服务组件实现。
      3. 网站前端开发等。

社团经历：
   1.2013年，在中山大学信息科技发展中心人力资源部负责策划、主持全员大会，以及其它社团日常活动，并获得内部策划大赛一等奖；
   2.2014年，在中山大学吉他协会担任副部长，负责组织内培与教授乐理与吉他基础，组织大型校内演出活动（荒岛音乐节）。
