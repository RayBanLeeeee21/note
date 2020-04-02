# Chapter 01 spring boot 启动 - SpringApplication.run


## 1. SpringApplication实例化

```java
public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
    this.resourceLoader = resourceLoader;
    Assert.notNull(primarySources, "PrimarySources must not be null");
    this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));

    // 1.1 推断web类型
    this.webApplicationType = WebApplicationType.deduceFromClasspath();

    // 1.2 加载Initializer和Listener - SpringFctoriesLoader提供类名, 再实例化
    setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));
    setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));

    // 推断main方法所在类(通过在线程调用栈中向上递归查找main方法实现)
    this.mainApplicationClass = deduceMainApplicationClass();   
}

```
### 1.1 推断web类型

推断Web容器类型时, 通过判断是否存在特定类(或者缺少特定的必要类), 来推断出Web容器类型(React/Servlet/无Web容器). 
类的存在与否, 则通过``ClassLoader#forName()``来判断, 默认情况下是查找类路径下的类.

```java

private static final String WEBMVC_INDICATOR_CLASS = "org.springframework.web.servlet.DispatcherServlet";
private static final String WEBFLUX_INDICATOR_CLASS = "org.springframework.web.reactive.DispatcherHandler";
private static final String JERSEY_INDICATOR_CLASS = "org.glassfish.jersey.servlet.ServletContainer";
private static final String SERVLET_APPLICATION_CONTEXT_CLASS = "org.springframework.web.context.WebApplicationContext";
private static final String REACTIVE_APPLICATION_CONTEXT_CLASS = "org.springframework.boot.web.reactive.context.ReactiveWebApplicationContext";

static WebApplicationType deduceFromClasspath() {
    if (ClassUtils.isPresent(WEBFLUX_INDICATOR_CLASS, null) && !ClassUtils.isPresent(WEBMVC_INDICATOR_CLASS, null)
            && !ClassUtils.isPresent(JERSEY_INDICATOR_CLASS, null)) {
        return WebApplicationType.REACTIVE;
    }
    for (String className : SERVLET_INDICATOR_CLASSES) {
        if (!ClassUtils.isPresent(className, null)) {
            return WebApplicationType.NONE;
        }
    }
    return WebApplicationType.SERVLET;
}
```

### 1.2 通过SpringFactoriesLoader配置Initializer和Listener实现类

``SpringApplication#getSpringFactoriesInstances()``方法通过调用``SpringFactoriesLoader#loadFactoryNames()``方法, 
获取``Initializer``和``Listener``接口的一系列实现类的类名, 然后再调用``BeanUtils#instantiateClass()``方法来实例化这些实现类. 
``Initializer``和``Listener``的作用和加载等方面暂时不在这一节做介绍. 
<br/>

从``SpringApplication#getSpringFactoriesInstances()``到``SpringFactoriesLoader#loadFactoryNames()``的调用栈(下调上)为:

```java
loadFactoryNames(Class, ClassLoader):121, SpringFactoriesLoader
getSpringFactoriesInstances(Class, Class[], Object[]):419, SpringApplication
getSpringFactoriesInstances(Class):413, SpringApplication
<init>(ResourceLoader, Class[]):269, SpringApplication
<init>(Class[]):250, SpringApplication
```


``SpringFactoriesLoader#loadFactoryNames()``方法从``classpath:META/spring.factories``资源文件中获取接口实现类的配置信息.
``spring.factories``实际上是一种properties文件, 保存着取值为接口和类的全限定名的key-val对, 表示某个类的哪些实现类需要加载.  例如: 
```properties
package1.MyInterface=package2.MyImplementClass1\
    ,package2.MyImplementClass2\
```

``SpringFactoriesLoader#loadFactoryNames()``方法的实现如下

```java
public static List<String> loadFactoryNames(Class<?> factoryClass, @Nullable ClassLoader classLoader) {
    String factoryClassName = factoryClass.getName();
    return loadSpringFactories(classLoader).getOrDefault(factoryClassName, Collections.emptyList());
}

private static Map<String, List<String>> loadSpringFactories(@Nullable ClassLoader classLoader) {

    // 先从cache检查是否已经加载过该ClassLoader的接口
    // 该多值map保存接口全限定名到实现类的全限定名的映射关系
    // 即: 接口->[实现1, 实现2,..., 实现n]
    MultiValueMap<String, String> result = cache.get(classLoader);
    if (result != null) {
        return result;
    }

    try {

        // 检索所有类路径(各jar包)下的 META-INF/spring.factories
        Enumeration<URL> urls = (classLoader != null ?
                classLoader.getResources(FACTORIES_RESOURCE_LOCATION) :
                ClassLoader.getSystemResources(FACTORIES_RESOURCE_LOCATION));
        result = new LinkedMultiValueMap<>();
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            UrlResource resource = new UrlResource(url);
            Properties properties = PropertiesLoaderUtils.loadProperties(resource);
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                String factoryClassName = ((String) entry.getKey()).trim();
                for (String factoryName : StringUtils.commaDelimitedListToStringArray((String) entry.getValue())) {
                    result.add(factoryClassName, factoryName.trim());
                }
            }
        }
        cache.put(classLoader, result);
        return result;
    }
    catch (IOException ex) {
        throw new IllegalArgumentException("Unable to load factories from location [" +
                FACTORIES_RESOURCE_LOCATION + "]", ex);
    }
}
```
``SpringFactoriesLoader`` + ``spring.factories``实现的功能与jdk的[SPI机制](https://www.jianshu.com/p/3a3edbcd8f24)相似, 都是通过配置文件来加载接口的特定实现类, 避免将一些组件类的实例化配置硬编码到java代码中. 
``spring.factories``的配置方法与SPI机制相比更简洁, 一个``spring.factories``可以指定多种接口的实现类的配置, 而jdk的SPI机制中, 每个接口需要写成单独的配置文件. 例如 JDBC Driver:
```properties
# ServiceLoader类固定地从 classpath:META-INF/services加载配置
# 每个接口的实现类的配置, 都分别定义在[以该接口全限定类名为文件名]的资源文件中

# Mysql的JDBC Driver类 [mysql:mysql-connector-java:5.1.47]
# - 资源路径: mysql-connector-java-5.1.47.jar!\META-INF\services\java.sql.Driver
com.mysql.jdbc.Driver
com.mysql.fabric.jdbc.FabricMySQLDriver

# Oracle的JDBC Driver类 [com.oracle:ojdbc6:11.2.0.3]
# - 资源路径: ojdbc6-11.2.0.3.jar!\META-INF\services\java.sql.Driver
oracle.jdbc.OracleDriver

```
然而, ``spring.factories``的缺点也很明显 -- SPI作为jdk自带的功能, 在使用时不需要引入任何依赖, 而通过``spring.factories``实例化类需要引入Spring的依赖, *在开发非Spring应用的SDK时引入额外的依赖会使SDK的配置更复杂*.

<br/>

``SpringFactoriesLoader`` + ``spring.factories``是比较常用的配置bean的方法, 也是实现Spring的AutoConfiguration机制的其中一个基础功能. 

## 2. 运行SpringApplication

调用``SpringApplication#run()``方法启动Spring应用时, 主要完成配置加载和bean加载等工作. 
整个启动过程非常复杂, 但并非毫无规律可言, 大多数的工作都在``SpringApplicationRunListener``, ``Environment``和``ApplicationContext``这几个接口的实现类中完成, 而设计模式 (工厂模式, 监听器模式等) 的作用和思想在其中体现得淋漓尽致. 这些接口可以说是整个Spring体系的基石.

<br/>

``SpringApplication#run()``的流程如下:
```java
public ConfigurableApplicationContext run(String... args) {

    // 
    StopWatch stopWatch = new StopWatch(); // 计时开始 - StopWatch是一个简单的计时器
    stopWatch.start(); 

    ConfigurableApplicationContext context = null;
    Collection<SpringBootExceptionReporter> exceptionReporters = new ArrayList<>();

    /**
    * 将java.awt.headless设置到system properties
    * - 无头模式(java.awt.headless) - 表示缺少显示设备等. 与图形渲染等功能的底层实现相关.
    * - spring boot应用通常通过命令行运行(无图形界面), 因此默认打开(true).
    */
    configureHeadlessProperty();

    /**
    * 2.1 初始化SpringApplicationRunListener(s)
    * SpringApplicationRunListener[s] 实际上是多个SpringApplicationRunListener的集合, 用于遍历执行这些Listener
    * 而Listener的实现类通过SpringFactoriesLoader加载.
    */
    SpringApplicationRunListeners listeners = getRunListeners(args);
    listeners.starting();
    try {
        ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);  // 命令行args的包装类

        /**
        * 2.2 根据Web类型, 实例化对应的Environment类型, 并做些准备工作
        */
        ConfigurableEnvironment environment = prepareEnvironment(listeners, applicationArguments);
        configureIgnoreBeanInfo(environment); // 将spring.beaninfo.ignore设置到system property和Environment
        Banner printedBanner = printBanner(environment); // 打印logo

        /**
        * 2.3 根据Web类型, 实例化对应的ApplicationEnvironment类型, 并做些准备工作
        */
        context = createApplicationContext();   
        exceptionReporters = getSpringFactoriesInstances(SpringBootExceptionReporter.class,
                new Class[] { ConfigurableApplicationContext.class }, context);
        prepareContext(context, environment, listeners, applicationArguments, printedBanner);
        refreshContext(context);                      // 刷新ApplicationContext(加载bean等)
        afterRefresh(context, applicationArguments);  // hook方法, 默认为空

        // 一些启动后的处理
        stopWatch.stop();
        if (this.logStartupInfo) {
            new StartupInfoLogger(this.mainApplicationClass).logStarted(getApplicationLog(), stopWatch);
        }
        listeners.started(context);
        callRunners(context, applicationArguments);
    }
    catch (Throwable ex) {
        handleRunFailure(context, ex, exceptionReporters, listeners);
        throw new IllegalStateException(ex);
    }

    try {
        listeners.running(context);
    }
    catch (Throwable ex) {
        handleRunFailure(context, ex, exceptionReporters, null);
        throw new IllegalStateException(ex);
    }
    return context;
}
```
[``SpringApplicationRunListener``](./ch02%20SpringApplicationRunListener.md), [``Environment``](./ch02%20Environment.md), [``ApplicationContext``](./ch03%20ApplicationContext.md)的概念比较重要, 而且实例化和准备过程都比较复杂, 因此分别在分别的章节进行介绍.


扩展知识: SPI, SpringFactoriesLoader