
### 2.2.2 Spring IoC容器的设计 

```java
ClassPathXmlApplicationContext(AbstractApplicationContext).refresh()
ClassPathXmlApplicationContext(AbstractApplicationContext).obtainFreshBeanFactory()
ClassPathXmlApplicationContext(AbstractRefreshableApplicationContext).refreshBeanFactory()	
    // 调用下一个方法把BeanDefinition加载到被代理类DefaultListableBeanFactory
ClassPathXmlApplicationContext(AbstractXmlApplicationContext).loadBeanDefinitions(DefaultListableBeanFactory)
    // 创建新XmlBeanDefinitionReader, 
    // 把beanFactory(DefaultListableBeanFactory)关联到reader,
    // 把this作为ResourceLoader
    // 让reader通过this(ResourceLoader)加载到被代理类beanFactory
ClassPathXmlApplicationContext(AbstractXmlApplicationContext).loadBeanDefinitions(XmlBeanDefinitionReader)
XmlBeanDefinitionReader(AbstractBeanDefinitionReader).loadBeanDefinitions(String...)
XmlBeanDefinitionReader(AbstractBeanDefinitionReader).loadBeanDefinitions(String)
XmlBeanDefinitionReader(AbstractBeanDefinitionReader).loadBeanDefinitions(String, Set<Resource>)

```

bean注册流程:
1. loadBeanDefinitions(beanFactory);
    1. 将要注册的BeanFactory和特定的ResourceLoader交给reader(XmlBeanDefinitionReader), 由其负责加载
        1. reader根据**ResourceLoader**解析Resource路径
        2. 打开Resource, 读取Document
        3. 从Document负责解析出Xml根结点
        4. 将Xml根结点与要注册的BeanFactory交给documentReader(DefaultBeanDefinitionDocumentReader)负责注册
            1. documentReader创建delegate(BeanDefinitionParserDelegate)
                1. delegate解析结点, 把解析结果放到BeanDefinitionHolder
            2. 把解析的结果从BeanDefinitionHolder取出, 注册到BeanFactory
            
    