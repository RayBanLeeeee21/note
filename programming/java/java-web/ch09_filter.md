
* Filter方法
    * init
        * 只在应用程序启动时(servlet初始化前)调用 
    * doFilter
    * destroy
* Filter配置
    * 配置内容：
        * url模式
        * servlet
        * 请求派发类型
    * 注解不能对过滤器进行排序，编程式与部署描述符可以