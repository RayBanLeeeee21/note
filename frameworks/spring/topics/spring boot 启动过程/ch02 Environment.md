# Chapter 02 Environment

## 2.1 Environment相关API

在了解``Environment``实现类的原理之前, 需要一些与``Environment``相关的API的先验知识, 了解``Environment``提供了什么功能, 在Spring中起到了怎样的作用.

下图以``StandardEnvironment``类为例, 列出了``StandardEnvironment``所有父类/接口的主要方法.
<img src="./resources/environment.svg" style="width: 100%"/>

由于涉及的方法比较多, 看起来比较繁杂, 笔者将各接口方法按功能进行分类和简化, 得到简化后的"**伪**"类图. 如果想要了解Spring官方对于这些接口的规范以及其它细节, 读者应该直接参考Spring的源代码. 此处只是对接口各自的职责范围和依赖关系做一些梳理.
![](./resources/environment-sim.svg)


## 2.2 Environment的实现类
在Spring boot启动的过程中, 可能实际化的``Environment``类型有三种: ``StandardServletEnvironment``, ``StandardReactiveWebEnvironment``, ``StandardEnvironment``, 分别对应SERVLET, REATIVE和无Web类型这三种情况.
```java
/**
坐标: org.springframework.boot.SpringApplication#getOrCreateEnvironment()
*/
private ConfigurableEnvironment getOrCreateEnvironment() {
    if (this.environment != null) {
        return this.environment;
    }
    switch (this.webApplicationType) {
    case SERVLET:
        return new StandardServletEnvironment();
    case REACTIVE:
        return new StandardReactiveWebEnvironment();
    default:
        return new StandardEnvironment();
    }
}
```
其中``StandardServletEnvironment``的``StandardReactiveWebEnvironment``都继承自``StandardEnvironment``, 而``StandardEnvironment``继承自``AbstractEnvironment``. 从上一节可以看到, ``Environment``


### 2.2 Environment的准备过程 - prepareEnvironment()

```java
private ConfigurableEnvironment prepareEnvironment(SpringApplicationRunListeners listeners,
        ApplicationArguments applicationArguments) {
    /**
    * 根据Web类型来推断Environment类型并初始化.
    * - JVM参数 & 环境变量中定义的属性将在Environment的实例化阶段加载到Environment.
    */
    ConfigurableEnvironment environment = getOrCreateEnvironment();             

    // 
    configureEnvironment(environment, applicationArguments.getSourceArgs());
    listeners.environmentPrepared(environment); // 通知listener(s)
    bindToSpringApplication(environment);

    // 判断要不要将Environment转换类型
    // 疑问: 是不是WebType或者Environment中途发生变化
    if (!this.isCustomEnvironment) { 
        environment = new EnvironmentConverter(getClassLoader()).convertEnvironmentIfNecessary(environment,
                deduceEnvironmentClass());
    }
    ConfigurationPropertySources.attach(environment);
    return environment;
}

/**
* 根据Web类型来推断Environment类型并初始化.
* - JVM参数 & 环境变量中定义的属性将在Environment的实例化阶段加载到Environment.
*/
private ConfigurableEnvironment getOrCreateEnvironment() {
    if (this.environment != null) {
        return this.environment;
    }
    switch (this.webApplicationType) {
    case SERVLET:
        return new StandardServletEnvironment();
    case REACTIVE:
        return new StandardReactiveWebEnvironment();
    default:
        return new StandardEnvironment();
    }
}
```