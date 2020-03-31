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

//...

```
### 1.1 推断web类型

推断Web容器类型时, 通过判断是否存在特定类(或者缺少特定的必要类), 来判断Web容器类型(React/Servlet/无Web容器). 
类是否存在, 通过``ClassLoader#forName()``来判断, 默认情况下是查找``classpath``路径下的类.

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

``SpringApplication#getSpringFactoriesInstances()``方法通过``SpringFactoriesLoader#loadFactoryNames()``来获取一系列``Initializer``和``Listener``接口的实现类名, 然后再通过``BeanUtils#instantiateClass()``方法来实例化这些实现类. 

<br/>

从``SpringApplication#getSpringFactoriesInstances()``到``SpringFactoriesLoader#loadFactoryNames()``的调用栈(下到上)为:

```java
loadFactoryNames(Class, ClassLoader):121, SpringFactoriesLoader
getSpringFactoriesInstances(Class, Class[], Object[]):419, SpringApplication
getSpringFactoriesInstances(Class):413, SpringApplication
<init>(ResourceLoader, Class[]):269, SpringApplication
<init>(Class[]):250, SpringApplication
```


``SpringFactoriesLoader#loadFactoryNames()``的实现:
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
        // 在 META-INF/spring.factories 中, 保存着形如
        //     接口1=类1,类2,类3
        // 这样的键值对.
        // META-INF/spring.factories用于指定, 对于指定接口, 哪些实现类需要加载
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
``SpringFactoriesLoader``及``META-INF/spring.factories``的功能与jdk的[SPI机制](https://www.jianshu.com/p/3a3edbcd8f24)相似, 都是通过配置文件来加载接口的特定实现类(而不是将这些类的实例化硬编码到java代码中), 但其配置方法与SPI机制相比更简洁. 一个``spring.factories``可以指定多个接口的需要实例化的实现类. 而jdk的SPI机制中, 每个接口需要单独的配置文件. 例如 JDBC Driver:
```properties
# ServiceLoader类固定地从 classpath:META-INF/services加载配置
# 每个接口的实现类的配置, 都分别[以该接口全限定类名为文件名]的资源文件中

# Mysql的JDBC Driver类 [mysql:mysql-connector-java:5.1.47]
# - 资源路径: classpath:META-INF/services/java.sql.Driver
com.mysql.jdbc.Driver
com.mysql.fabric.jdbc.FabricMySQLDriver

# Oracle的JDBC Driver类 [com.oracle:ojdbc6:11.2.0.3]
# - 资源路径: classpath:META-INF/services/java.sql.Driver
oracle.jdbc.OracleDriver

```
但``spring.factories``的缺点也很明显 -- 使用SPI不需要引入任何依赖, 因为是jdk自带的功能, 而通过``spring.factories``实例化类需要引入Spring的依赖, *在开发非Spring应用的SDK时引入额外的依赖会使SDK的配置更复杂*.

