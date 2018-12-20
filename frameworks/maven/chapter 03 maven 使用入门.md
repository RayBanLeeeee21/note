# chapter 03 Maven使用入门

## 3.1 编写POM

Hello World项目:
* 代码
    ```xml
    <project>
        <modelVersion>4.0.0</modelVersion>
        
        <groupId>com.juvenxu.mvnbook</groupId>   <!-- 坐标组成元素 -->
        <artifactId>hello-world</artifactId>     <!-- 坐标组成元素 -->
        <version>1.0-SNAPSHOT</version>          <!-- 坐标组成元素 --> 
        <packaging>jar</packaging>               <!-- 打包格式, 默认:jar-->
        <name>Maven Hello World Project</name>

        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.1</version>
                    <configuration>
                        <source>1.8</source>
                        <target>1.8</target>
                    </configuration>
                </plugin>
            </plugins>
        </build>
        
    </project>
    ```
* 坐标:
    * groupId: 项目组
    * artifactId: 项目在组中的唯一ID
    * version: 项目版本号


## 3.2-3.4

默认目录:
* 主代码: ``src/main/java``
* 资源: ``src/main/resource``
* 测试代码: ``src/test/java``
* 测试资源: ``src/test/resouces``
* 输出目录: ``target``
    * 主代码输出类, 资源: ``target/classes``
    * 测试代码输出类, 资源: ``target/test-classes``

mvn目标:
* mvn clean 过程 (插件:插件版本:插件目录:目录实现)
    1. **清理target**: ``maven-clean-plugin:x.x:clean (default-clean)``
* mvn install 目标
    1. 打包资源: ``maven-resources-plugin:x.x:resources (default-resources)``
    2. 编译主代码: ``maven-compiler-plugin:x.x:compile (default-compile)``
    3. 打包测试资源: ``maven-resources-plugin:x.x:testResources (default-resources)``
    4. 编译测试代码: ``maven-compiler-plugin:x.x:testCompile (default-compile)``
    5. 测试: ``maven-surefire-plugin:x.x:test (default-test)``
    6. 打包: ``maven-jar-plugin:x.x:jar (default-jar)``
    7. 安装: ``maven-install-plugin:x.x:install (default-install)``
* mvn compile 目标: install中的1-2
* mvn test 目标: install中的1-5
* mvn package 目标: install中的1-6
* mvn install 目标: install中的1-7

带main函数的jar(略)

## 3.5 archetype 生成项目骨架

骨架生成过程:
1. 命令: ``mvn org.apache.maven.plugins:maven-archetype-plugin:3.0.1:generate``
    * 插件groupId : 插件artifactId(插件名) : 插件版本 : 目标
2. 提示选择模板:
    ```
    Choose archetype:
    1: internal -> org.apache.maven.archetypes:maven-archetype-archetype (An archetype which contains a sample archetype.)
    2: internal -> org.apache.maven.archetypes:maven-archetype-j2ee-simple (An archetype which contains a simplifed sample J2EE application.)
    3: internal -> org.apache.maven.archetypes:maven-archetype-plugin (An archetype which contains a sample Maven plugin.)
    4: internal -> org.apache.maven.archetypes:maven-archetype-plugin-site (An archetype which contains a sample Maven plugin site.
        This archetype can be layered upon an existing Maven plugin project.)
    5: internal -> org.apache.maven.archetypes:maven-archetype-portlet (An archetype which contains a sample JSR-268 Portlet.)
    6: internal -> org.apache.maven.archetypes:maven-archetype-profiles ()
    7: internal -> org.apache.maven.archetypes:maven-archetype-quickstart (An archetype which contains a sample Maven project.)
    8: internal -> org.apache.maven.archetypes:maven-archetype-site (An archetype which contains a sample Maven site which demonstrates
        some of the supported document types like APT, XDoc, and FML and demonstrates how
        to i18n your site. This archetype can be layered upon an existing Maven project.)
    9: internal -> org.apache.maven.archetypes:maven-archetype-site-simple (An archetype which contains a sample Maven site.)
    10: internal -> org.apache.maven.archetypes:maven-archetype-webapp (An archetype which contains a sample Maven Webapp project.)
    Choose a number or apply filter (format: [groupId:]artifactId, case sensitive contains): 7:
    ```
3. 提示输入项目参数
    ```
    Define value for property 'groupId': com.juvenxu.mvnbook
    Define value for property 'artifactId': hello-world
    Define value for property 'version' 1.0-SNAPSHOT: :
    Define value for property 'package' com.juvenxu.mvnbook: :
    Confirm properties configuration:
    groupId: com.juvenxu.mvnbook
    artifactId: hello-world
    version: 1.0-SNAPSHOT
    package: com.juvenxu.mvnbook
    Y: :
    ```
