# chapter 06 仓库


## 6.2 仓库布局
构件在仓库的路径格式:
* ```groupId/artifactId/version/artifactId-version[-extension].packaging```

## 6.3 仓库分类

仓库分类:
* 本地仓库
* 远程仓库:
    1. (私服)
    2. 中央仓库
    3. 其它仓库

## 6.3.1 本地仓库

setting.xml文件
* 默认路径
    * linux中为```~/.m2/setting.xml```
    * windows中为```C:\Users\username\.m2\setting.xml```
* ```mvn install --settings c:\user\settings.xml```可指定暂时的设置

本地仓库配置:
*   ```xml
    <!-- setting.xml 文件 -->
    <settings>
        <localRepository>~/.m2/repository</localRepository> <!-- 本地仓库路径 -->
    </settings>
    ```


## 6.3.3 中央仓库

仓库配置
* \<id\> : 仓库的唯一标识
* \<name\> : 仓库名
* \<layout\> : 仓库布局
* \<snapshot\> : 快照版配置
* 默认中央仓库配置
    ```xml
    <!-- $MAVEN_HOME/lib/maven-model-builder-x.x.x.jar/pom.xml -->
    <project>
        <repositories>
            <repository>
                <id>central</id>
                <name>Central Repository</name>
                <url>https://repo.maven.apache.org/maven2</url> 
                <layout>default</layout>                         <!-- 仓库布局, maven1要配置为legacy -->
                <snapshots>
                    <enabled>false</enabled>
                </snapshots>
            </repository>
        </repositories>
        ......
    </project>
    ```

### 6.3.4 私服

私服:
* 特点: 
    * 处于**局域网**
    * 代理**广域网**的远程仓库
* 优点:
    * 减少对外网的请求, 节省连接外网的带宽, 减少中央仓库的负荷
    * 外网无法连接时, 私服可以暂时提供服务
    * 部署私有构件


## 6.4 远程仓库配置

配置:
*   ```xml
    <!--pom.xml-->

    <project>
        ......
        <repository>
            <id>jboss</id>
            <name>JBoss Repository</name>
            <url>http://repository.jboss.com/maven2</url>
            <release>
                <enable>true</enable>
            </release>
            <snapshot>
                <enable>false</enable>
                <updatePolicy></updatePolicy>
            </snapshot>
            <layout>default</layout> 
        </repository>
        ......
    </project>
    ```
    * \<id\>: 仓库的唯一标识符
        * 中央仓库的默认值为central
    * \<layout\>: 
        * default表示maven2/maven3的布局
        * legacy是maven1的布局
    * \<snapshot\>
        * \<updatePolicy\>: 更新策略
            * daily: 每天
            * always: 每次构建
            * interval: X: X分钟一次
        * \<checkSumPolicy\>: 校验构件
            * fail
            * warn
            * ignore
        
### 6.4.1 远程仓库认证

仓库认证信息在**settings.xml**中

配置:
*   ```xml
    <!-- setting -->
    <settings>
        ......
        <servers>
            <server>
                <id>my-proj</id>
                <username>username</username>
                <password>pwd</password>
            </server>
        </servers>
        ......
    </settings>
    ```

### 6.4.2 部署至远程仓库
配置:
*   ```xml
    <!-- pom.xml -->
    <project>
        ......
        <distributionManagement>
            <repository>                    <!-- release仓库 -->
                <id>project-releases</id>
                <name>Proj Release Repository</name>
                <url>http://192.168.1.100/content/repositories/proj-releases</url>
            </repository>                   <!-- snapshot仓库 -->
            <snapshotRepository>
                <id>project-snapshots</id>
                <name>Proj Snapshot Repository</name>
                <url>http://192.168.1.100/content/repositories/proj-snapshots</url>
            </snapshotRepository>
        </distributionManagement>
        ......
    </project>
    ```
发布命令: ```mvn deploy```


## 6.5 快照版本
SNAPSHOT机制: **自动生成时间戳, 由maven自动管理** 

## 6.6 从仓库解析依赖的机制

依赖解析机制:
* 依赖范围为system时, 直接从本地解析 
* 如果显式指定了发布版的版本号, 则先寻找本地仓库, 再遍历远程仓库, 从对应的仓库下载
* 如果是LATEST或RELEASE版本, 则遍历远程仓库的元数据, 与本地仓库的元数据比较, 解析出版本
* 如果是SNAPSHOT, 则遍历远程仓库的无数据, 与本地仓库的元数据比较, 解析出快照版本号
* 对于SNAPSHOT版本, 复制到本地仓库时, 时间戳更改为SNAPSHOT

元数据groupId/artifactId/maven-metadata.xml
* 示例
    ```xml
    <!-- groupId/artifactId/maven-metadata.xml -->
    <?xml version = "1.0" encoding = "UTF-8" ?>
    <metadata>
        <groupId>org.sonatype.nexus</groupId>
        <artifactId>nexus</artifactId>
        <versioning>
            <latest>1.4.2-SNAPSHOT</latest>             <!-- 最新版本(包括发布版和快照版) -->
            <release>1.4.0</release>                    <!-- 最新发布版 -->
            <versions>                                  <!-- 所有版本 -->
                <version>1.3.5</version>
                <version>1.3.6</version>
                <version>1.4.0-SNAPSHOT</version>   
                <version>1.4.0</version>
                <version>1.4.0.1-SNAPSHOT</version>
                <version>1.4.1-SNAPSHOT</version>
                <version>1.4.2-SNAPSHOT</version>
            </version>
            <lastUpdated>20091214221557</lastUpdated>   <!-- 最近更新的构件的更新日期 -->
        </versioning>
    </metadata>
    ```
    * \<lastUpdated\>是最后一个更新的构件的更新日期, 不管这个构件的版本号是不是最大

元数据groupId/artifactId/version/maven-metadata.xml
* 只有SNAPSHOT版有这种元数据, RELEASE版没有
* 示例
    ```xml
    <!-- groupId/artifactId/version/maven-metadata.xml -->
    <?xml version = "1.0" encoding = "UTF-8" ?>
    <metadata>
        <groupId>org.sonatype.nexus</groupId>
        <artifactId>nexus</artifactId>
        <version>1.4.2-SNAPSHOT</version>
        <versioning>
            <snapshot>
                <timestamp>20091214.221414</timestamp>
                <buildNumber>13</buildNumber>
            </snapshot>
            <lastUpdated>20091214221558</lastUpdated>
        </versioning>
    </metadata>
    ```

## 6.7 镜像

镜像: 如果仓库X可以提供Y存储的所有内容, 那X是Y的一个镜像
* 配置
    ```xml
    <!-- settings.xml -->
    <settings> 
        ......
        <mirrors>
            <mirror>
                <id>maven.net.cn</id>
                <name>one of the central mirrors in China</name>
                <url>http://maven.net.cn/content/groups/public/</url>
                <mirrorOf>central</merrorOf>    <!-- maven.net.cn是id为central的仓库的镜像 -->
            </mirror>
        </mirrors>
        ......
    </settings>
    ```

\<mirrorOf\> 高级配置:
* ```<mirrorOf> * </mirrorOf>```: 匹配所有远程仓库
* ```<mirrorOf> external:* </mirrorOf>```: 匹配所有**非私有**远程仓库
* ```<mirrorOf> repo1, repo2 </mirrorOf>```: 匹配repo1, repo2
* ```<mirrorOf> *, !repo1 </mirrorOf>```: 匹配除repo1外远程仓库