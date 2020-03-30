# Chapter 01 spring boot 启动 - SpringApplication.run


## 1. SpringApplication实例化

```java
public SpringApplication(ResourceLoader resourceLoader, Class<?>... primarySources) {
    this.resourceLoader = resourceLoader;
    Assert.notNull(primarySources, "PrimarySources must not be null");
    this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));

    // 推断web类型
    this.webApplicationType = WebApplicationType.deduceFromClasspath();

    // 加载Initializer和Listener - SpringFctoriesLoader提供类名, 再实例化
    setInitializers((Collection) getSpringFactoriesInstances(ApplicationContextInitializer.class));
    setListeners((Collection) getSpringFactoriesInstances(ApplicationListener.class));

    // 推断main方法所在类
    this.mainApplicationClass = deduceMainApplicationClass();   
}
```