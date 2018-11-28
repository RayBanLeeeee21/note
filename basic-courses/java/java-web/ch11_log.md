
日志记录内容:
* 难以重现的bug
* 错误
* 配置修改
* 不可预料的行为
* 组件的启动与停止
* 用户登录
* 数据修改

日志分级：定义不同的日志级别
日志筛选：定义不同的日志目标

Log4j-2
* maven:
    * log4j-api
    * log4j-core: log4j默认实现
    * log4j-jcl: log4j实现的Commons Logging API
    * log4j-slf4j: log4j实现的slf4j API
    * log4j-taglib
* 配置检查顺序：
    1. log4j.configurationFile
    2. log4j2-test.{json|jsn}  (类路径)
    3. log4j2-test.xml  (类路径)
    4. log4j2.{json|jsn}  (类路径)
    5. log4j2.xml  (类路径)
    6. default: 级别: ERROR以上  记录到控制台
* 级别: 
    1. OFF 
    2. FATAL 
    3. ERROR 
    4. WARN 
    5. INFO 
    6. DEBUG 
    7. TRACE 
    8. ALL
* Logger:
    * getLogger
        * 每个类只有一个对应的Logger实例, 无则创建, 有则返回
        * LoggerName对与对应类相同
    * Level属性:
        * 继承
            * 继承自最近祖先
            * 自己设置
        * 每个Logger只有一个Level
    * Additivity属性: 控制Appender的继承
    * Appender属性:
        * PatternLayout属性
        * 继承
            * 继承自祖先 
            * 继承并添加(Additivity=true) 
            * 自己设置(Additivity=false)
        * 每个Logger可以有多个Appender(设置多个或者继承多个)
    * 过滤器
        * 结果
            * ACCEPT: 应该输出
            * DENY: 拒绝输出
            * NEUTRAL: 中立
        * 顺序
            1. context config
            2. Level 
            3. logger config
            4. appender reference
            5. appender config
* Log4j2 config文件
    * 文件名：log4j2(-test).{xml|json|jsn}
    * Status Logger: 特殊Logger，记录**日志系统**本身的问题