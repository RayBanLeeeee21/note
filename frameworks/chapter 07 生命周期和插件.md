# chapter 07 生命周期和插件

## 7.1 何为生命周期

生命周期
* 特征: **模板方法** + **插件机制**
* 伪代码
    ```java
    public abstract class AbstractBuild{
        public void build(){
            initialize();
            compile();
            test();
            packagee();
            integrationTest();
            deploy();
        }
        public abstract void initialize();
        public abstract void compile();
        public abstract void test();
        public abstract void packagee();
        public abstract void integrationTest();
        public abstract void deploy();
    }
    ```

插件机制: 构件生命周期的每个步骤都可以与某个插件的行为绑定
* 默认插件:
    * 编译: maven-compiler-plugin
    * 测试: maven-surefire-plugin
    * ...
* 用户自定义插件

## 7.2 生命周期详解

### 7.2.2 clean生命周期

clean生命周期:
* pre-clean
* clean
* post-clean

### 7.2.3 default生命周期
default生命周期
1.  * validate
    * initialize
2.  * generate-sources
    * **process-sources**: 复制src/main/resouces的文件到classpath
3.  * generate-reources
    * process-reources
4.  * **compile**: 编译主代码到classpath
    * process-classes
5.  * generate-test-sources
    * **process-test-sources**: 复制src/test/resouces的文件到测试classpath
6.  * generate-test-resources
    * process-test-resources
7.  * **test-compile**: 编译测试代码到测试classpath
    * process-test-classes
    * **test**: 测试
8.  * prepare-package
    * **package**: 打包
9.  * pre-integration-test
    * integration-test
    * post-integration-test
10. * verify
11. * **install**: 安装到本地仓库
12. * **deploy**: 部署到远程仓库

### 7.2.4 site生命周期
site生命周期
* pre-site
* site
* post-site
* site-deploy

### 7.2.5 命令行与生命周期

示例:
* ``mvn clean``: clean周期的 pre-clean => **clean**
* ``mvn test``: default周期的 validate => **test**
* ``mvn clean install``: clean周期的 pre-clean => clean, default周期的 validate => install
* ``mvn clean deploy site-deploy``: clean周期的 pre-clean => clean, default周期的所有阶段, site的所有阶段