# 接口定义:
BeanFactory
* 行为特点:
    * 根据bean名, 类查询对应的bean
    * 查询bean的相关属性
* 方法:
    ``` java
    // 根据bean名/类查询对应的bean
    Object getBean(String)
    <T> T getBean(String, Class<T>)
    Object getBean(String, Object...)
    <T> T getBean(Class<T>)
    <T> T getBean(Class<T>, Object...)

    // 查询bean的相关属性
    boolean isSingleton(String)
    boolean isPrototype(String)
    boolean isTypeMatch(String, ResolvableType)
    boolean isTypeMatch(String, Class<?>)
    Class<?> getType(String)
    ```

Resource
* 行为特点: 资源描述符
* 方法:
    * exists()
    * isReadable()
    * isOpen()
    * isFile()
    * getURL()
    * getURI()
    * getFile()
    * readableChannel()
    * contentLength()
    * lastModified()
    * createRelative(String)
    * getFilename()
    * getDescription()

ResourceLoader
* 行为特点: 
    * 获取资源Resource
    * 持有ClassLoader
* 方法:
    * Resource getResource(String)
    * ClassLoader getClassLoader()


AbstractBeanDefinitionReader
* 域:
    ```java
    BeanDefinitionRegistry registry;        // (接口规定) 被服务的需要注册的类
    ResourceLoader resourceLoader;          // (接口规定) 资源加载器, 用于给资源定位
    ClassLoader beanClassLoader;            // (接口规定) 加载bean的类加载器
    Environment environment;            
    BeanNameGenerator beanNameGenerator;    // (接口规定) 
    ```
* 模板方法:
    ```Java
    int loadBeanDefinitions(Resource);      // (接口方法) 
    int loadBeanDefinitions(Resource...);   // (接口方法)
    int loadBeanDefinitions(String);        // (接口方法)
    int loadBeanDefinitions(String...);     // (接口方法)

    int loadBeanDefinitions(String locations, Set<Resource>actualResources);
        // 机制:
        // 1. 如果ResourceLoader是ResourcePatternResolver类型, 
        //          调用其getResources()方法定位Resource (可能有多个匹配的返回值)
        //          然后逐个加载BeanDefinition
        //    否则
        //          调用ResourceLoader的getResource()方法定位Resource
        //          并加载BeanDefinition
        // 2. actualResources(可选)用于记录已定位的Resource对象
    ```
* 重写方法
    ```Java
    public int loadBeanDefinitions(Resource resource) throws BeanDefinitionException; 
    ```
    * XmlBeanDefinitionReader的实现
    ```python
    如果只有Resoure的location(String), 先用自带的ResourceLoader定位Resource
    对每个Resouce进行加载{
        将Resouce读取到doc(Document)
        将doc与readerContext(包含reader的引用)交给documentReader(DefaultBeanDefinitionDocumentReader)进行注册{
            documentReader将document转成XML结点root(Element)    
            documentReader把每个结点交给delegate(BeanDefinitionParserDelegate)转化成BeanDefinition
                delegate将BeanDefinition保存在BeanDefinitionHolder并返回结果
            documentReader从BeanDefinitionHolder取出结果注册到registry中
        }
        
    }
    
    ```






HierarchicalBeanFactory
* 行为特点:
    * 父级BeanFactory    
    * 当前BeanFactory的bean, 不包括其它层
* 方法:
    ``` java
    BeanFactory getParentBeanFactory();
    boolean containsLocalBean(String name);
    ```