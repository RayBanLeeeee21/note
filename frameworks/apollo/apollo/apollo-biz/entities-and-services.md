# 主要实体与业务逻辑
(未完成)

Audit: 审计, 即记录对实体类的修改操作(增删改)
* 域:
    * enum OP: INSERT, UPDATE, DELETE
    * entityName: 实体类的类名
    * entityId: 实体的ID
    * opName: 操作类型(INSERT, UPDATE, DELETE)
    * comment
* service类下的服务(部分):
    ```java
    /**
        保存审计: 持久化一个修改操作
    */
    void audit(String entityName, Long entityId, Audit.OP op, String owner);
    void audit(Audit audit);
  }
    ```

AppNamespaces: App下**与cluster无关**的命名空间
* 域:
    * name: 命名空间名
    * appId: 命名空间所属App
    * format: 命名空间格式(即配置文件格式, 如xml)
    * isPublic: 配置所在空间
    * comment
* 与其它实体的关系:
    * Namespace: 1-(0..n)
* **与Namespace区别**:
    * 该实体只是说明某一命名空间属于一个App, 并没有说明其属于哪个集群, 即是说与实际部署无关.
* service类下的服务(部分):
    ```java
    /**
        检查以"appId+namespaceName"为标识的命名空间是否已存在.
    */
    public boolean isAppNamespaceNameUnique(String appId, String namespaceName);

    /**
        查找公共命名空间是否存在.
        * 公共命名空间全局唯一, 故查找时不需要appId.
    */
    public AppNamespace findPublicNamespaceByName(String namespaceName);

    /**
        查找App下的命名空间(与cluster无关).
    */
    public AppNamespace findOne(String appId, String namespaceName);

    /**
        查找App下的多个命名空间(与cluster无关).
    */
    public List<AppNamespace> findByAppIdAndNamespaces(String appId, Set<String> namespaceNames);

    /**
        为一个App(appId保存在参数appNamespace中)创建命名空间:
        1. 在AppNamespace表中新增一条与cluster无关的命名空间的记录.
        2. app下所有cluster中同步该命名空间: 在Namespace表中为app下每个cluster分别新增一条记录, 每条记录将该AppNamespace分别关联到对应的cluster.
    */
    public AppNamespace createAppNamespace(AppNamespace appNamespace);

    /**
        app下所有cluster中同步指定命名空间: 在Namespace表中, 为app下每个cluster分别新增一条记录, 每条记录将该AppNamespace分别关联到对应的cluster.
    */
    public void createNamespaceForAppNamespaceInAllCluster(String appId, String namespaceName, String createBy)
    ```

Cluster:
* 域:
    * name: 集群名
    * appId: 所在App
    * **parentClusterId**: 父集群
* service类下的服务(部分):
    ```java
    /**
        创建cluster的同时, 把cluster所在的app的命名空间同步到该cluster下
        * 同步到cluster即是说, 在Namespace表中创建与该cluster相关联的记录.
    */
    public Cluster saveWithInstanceOfAppNamespaces(Cluster entity);

    /**
        只是创建cluster, 而不在该cluster中同步App中原有的命名空间.
    */
    public Cluster saveWithoutInstanceOfAppNamespaces(Cluster entity);
    ```
