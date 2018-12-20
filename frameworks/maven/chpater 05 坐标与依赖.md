# chapter 05 坐标与依赖

## 5.1 何为Maven坐标

maven坐标组成元素:
* groupId: 项目组
* artifactId: 组内项目名
    * 推荐以groupId最后一部分开头, 方便区分
* version: 
    * 默认1.0-SNAPSHOT
    * SNAPSHOT版详解见6.8
* packaging: 
    * 默认jar
* classifier: 附属构件
    * 可选
    * 只能由插件生成, 不能通过POM定义


## 5.4 依赖的配置

POM中的 \<dependency\> 元素:
* 子元素:
    * \<groupId\> : 依赖所在项目组
    * \<artifactId\> : 依赖名称
    * \<version\> : 依赖版本
    * \<scope\> : 依赖范围
    * \<optional\> : 可选, 值为true或false
    * \<exclusions\> : 排除依赖的依赖
        * 子元素: \<exclusion\> (包含 \<groupId\> 和 \<artifactId\>)

version元素 (参考6.8): 
* 格式:
    * LATEST: 从发布版和快照版中找一个最新的
    * X.X.X: 发布版
    * X.X.X-SNAPSHOT: 快照版, 自动解析该版本中的最新的更新
        * 不稳定, 只建议在团队内部使用

## 5.4 依赖范围

依赖范围:
* compile:
    * scope的默认值
    * 编译, 测试, 运行有效
    * **适于提供api的库**
* runtime:
    * 编译无效; 测试, 运行有效
    * **适于提供具体实现的库**
* test:
    * 对测试有效
* provided:
    * 编译, 测试有效, 运行无效
    * **环境提供**, 如容器提供servlet-api
* system:
    * 编译, 测试有效, 运行无效
    * **用户提供**, 指定本地jar路径
*   |scope|编译有效|测试有效|运行有效|适用|
    |:-:|:-:|:-:|:-:|:-:|
    |compile|Y|Y|Y|API(spring-core)|
    |runtime|N|Y|Y|具体实现(JDBC驱动实现)|
    |provided|Y|Y|N|容器提供(servlet-api)|
    |system|Y|Y|N|本地jar|


## 5.6 依赖传递

依赖传递与依赖范围 
* 规则: 
    * 第二依赖为compile时, 传递依赖与第一依赖一致
    * 第二依赖为test时, 传递依赖关系为无依赖
    * 第一依赖为provided时, 传递依赖为provided(除test)
    * 第二依赖为runtime时, 传递依赖与第一依赖一致(compile的情况除外, 传递依赖为runtime)
*   |*第一依赖*\\*第二依赖*|**compile**|**test**|**provided**|**runtime**|
    |:-:|:-:|:-:|:-:|:-:|
    | **compile** |compile|-|-|runtime|
    |**test**|test|-|-|test|
    |**provided**|provided|-|provided|provided|
    |**runtime**|runtime|-|-|runtime|
* 原理: ?

## 5.7 依赖调解 

调解原则: 
1. 路径最短优先
2. 路径相同时, 第一声明者优先

## 5.8 可选依赖

\<dependency\> 的 \<optional\> 元素: 值为true或false
* 默认false
* B中声明对C的可选依赖时, A依赖B时需要显式声明对C的依赖
* **不推荐使用**
    * 如B对C1, C2依赖可选时, 应改成实现的B1与B2, 分别依赖C1和C2, 由调用者A来选择B1还是B2

## 5.9 最佳实践

### 5.9.1 排除依赖

\<dependency\> 的 \<exclusives\> 元素:
* 子元素 \<exclusive\> 
    * \<groupId\>
    * \<artifactId\>

## 5.9.10 归类依赖

用法:
*   ```xml
    <properties>
        <springframework.version>1.0.0</springframework.version>
    </properties>    
    <!-- ...... -->
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-core</artifactId>
        <version>${springframework.version}</version>
    <dependency>
    ```

### 5.9.3 优化依赖

优化目标:
* ```mvn dependency:list```
* ```mvn dependency:tree```
* ```mvn dependency:analyze```
    * 找出编译阶段 (包括主代码和测试) 未用到的依赖
    * 运行时 (包括主代码和测试) 的依赖无法发现