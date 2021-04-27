## AppController

public AppDTO create(appDto)
* 过程: 
    1. 通过ID检查app是否已存在
    2. 创建新的app
    3. 为新app创建默认app命名空间 app >nmspc
    4. 为新App创建默认集群 app > cluster
    5. 为app下的所有app命名空间, 创建关联默认集群的命名空间app > cluster >nmspc

delete(appId, operator)
* 过程: 
    1. 找到所有 app > cluster 
    2. 删除每个 app > cluster
       1. 删除每个 cluster >nmspc
          1. 删除所有nmspc下的item
          2. 删除所有关联的提交commit
          3. 如果不是childNamespace, 则删除所有发布版本release
          4. 删除其childNamespace及其release ??? 怎么实现
          5. 删除更改记录releaseHistory
          6. 删除依赖该app的客户端实例instance
          7. 释放对nmspc的锁
          8. 删除nmspc
          9. publish事件
       2. 删除 cluster
    3. 删除每个 app >nmspc
    4. 删除app


update(app)
* 过程:
    1. 检查app是否存在
    2. 更新数据库

find(name, pageable)
* 作用: name为空则找所有, 否则找特定名字的app

get(appId)
* 作用: 根据ID查找

isAppIdUnique(appId)
* 作用: 根据ID判断app是否创建过

## AppNamespaceController

create(appNamespace, silentCreation)
* 过程:
    1. 先查找 app >nmspc
    2. 如果不存在则创建
       1. 找到app下所有非child的cluster
       2. 为这些cluster创建app > cluster >nmspc
    3. 否则, silentCreation ? 返回原来的app >nmspc: 抛异常

delete(appId, namespaceName)
* 过程:
    1. 检查 app >nmspc是否存在
    2. 删除 app >nmspc
       1. 查找所有的app > cluster >nmspc
       2. 删除所有的app > cluster >nmspc
       3. 删除 app >nmspc

findPublicAppNamespaceAllNamespaces(publicNamespaceName, pageable)
* 过程:
    1. 根据publicNamespaceName查找到对应的 app > nmspc
    2. 根据 app > nmspc找到所有的app > cluster > nmspc返回
    3. 过滤掉childNamespace (对应灰度 cluster)

countPublicAppNamespaceAssociatedNamespaces
* 作用: 返回与公有的 app >nmspc关联的所有app > cluster >nmspc的记录数

getAppNamespaces(appId)

## ClusterController

create(appId, autoCreatePrivateNamespace,  clusterDto)
* 过程
    1. 检查cluster是否存在, 不存在则报错
    2. autoCreatePrivateNamespace ? 为cluster创建app > cluster >nmspc: 不创建

delete(appId, clusterName, operator)
* 过程:
    1. 检查cluster是否存在
    2. 检查是否默认 cluster, 默认的不能被删除
    3. 删除 cluster
       1. 删除关联的app > cluster >nmspc
       2. 删除 cluster

find(appId)
* 作用: 查找指定app下的所有 app > cluster

get(appId, clusterName)
* 作用: 查找指定 app > cluster

isAppIdUnique(appId, clusterName) 
* 作用: 判断 app > cluster 是否已存在

## CommitController

find(appId, clusterName, namespaceName, pageable)
* 作用: 查找app > cluster >nmspc的所有 commit

## IndexController
index():
* ```return "apollo-adminservice";```


## InstanceConfigController
getByRelease(releaseId, pageable)
* 作用: 根据releaseId查找依赖特定版本的配置的**客户端实例及其对应配置**的列表
* 过程:
    1. 根据releaseId获取release
    2. 根据releaseKey查找有效的(最近1d1h内的)关联关系instanceConfig的列表
       * instanceConfig保存了客户端实例的instanceId与配置的app > cluster >nmspc的关联关系
    3. 生成多值Map: instanceId->instanceConfigs (*一个实例可能订阅多个config*)
    4. 根据3中Map的key集(即instanceIds集)去数据库找对应的instances列表
    5. 将instances的app > cluster >nmspc及对应的instanceConfig放在一起, 返回结果

getByReleasesNotIn(appId, clusterName, namespaceName, releaseIds)
* 作用: 查找依赖app > cluster >nmspc的配置(不属于releaseIds中任一版本的配置)的**客户端实例及其对应配置**的列表
* 过程:
    1. 根据releaseIds在数据库中获取releaseKeys列表
    2. 根据releaseKeys与app > cluster >nmspc查找不属于releaseKeys的关联关系instanceConfigs列表
    3. 生成多值Map: instanceId->instanceConfigs (*一个实例可能订阅多个config*)
    4. 利用3中Map的key集(即instanceIds集)去数据库找instances列表
    5. 将instances的app > cluster >nmspc及对应的instanceConfig放在一起, 返回结果

getInstancesByNamespace(appId, clusterName, namespaceName, instanceAppId, pageable)
* 作用: 
    1. 获取监听app > cluster >nmspc的所有应用实例 (instanceAppId为空时)
    2. 获取监听app > cluster >nmspc的指定 app 的所有应用实例

getInstancesCountByNamespace

## ItemController
create(appId, clusterName, namespaceName, itemDTO)
* 作用: 在app > cluster >nmspc下创建item
* 过程:
    1. 检查是否已存在
    2. 创建item
    3. 创建commit
    4. 返回创建值

update(appId, clusterName, namespaceName, itemId, itemDTO)
* 作用: 在app > cluster >nmspc下更新item
* 过程:
    1. 检查是否已存在
    2. 更新item
    3. 创建commit
    4. 返回更新值

delete(itemId, operator)
* 作用: 在app > cluster >nmspc下删除item
* 过程:
    1. 检查是否已存在
    2. 更新item
    3. 创建commit
    4. 返回更新值

findItems(appId, clusterName, namespaceName)
* 作用: 获取app > cluster >nmspc下的所有item

get(itemId)

get(appId, clusterName, namespaceName, key)

## ItemSetController 
create(appId, clusterName, namespaceName, changeSet)
* 作用: 将Item更改集(changeSet)应用到 app > cluster > name
* 过程:
    1. 如果有要创建item, 则创建item记录到数据库
    2. 如果有要更新的item, 先检查是否存在, 然后更新数据库, 并将旧值放到返回值中
    3. 如果有要删除的item, 则从数据库删除
    4. 创建新的commit
    5. 返回更新过程(增/改/删过程的新旧值)

## NamespaceBranchController

createBranch(appId, clusterName, namespaceName, operator)
* 作用: 在app > cluster(父) > nmspc下建新的分支
* 过程:
    1. 检查app > cluster(父) > nmspc是否存在
    2. 开始创建app > cluster(父) > nmspc的子命名空间app > cluster(子) > nmspc
        1. 检查app > cluster(父) > nmspc是否已经有子命名空间 (一个主分支只能有一个灰度分支)
        2. 检查app > cluster(父)是否存在, 不存在抛异常
        3. 创建app > cluster(子), 其中cluster名自动生成, 保存到数据库
        4. 创建app > cluster(子) > nmspc, 保存到数据库

findBranchGrayRules(appId, cluster, namespaceName, branchName)
* 作用: 获取针对app > cluster > nmspc的子(灰度)分支的灰度规则
    * 灰度规则: **客户端app+IP**的列表, 其中客户端app固定为app > cluster > nmspc的集群名

updataBranchGrayRules(appId, clusterName, namespaceName, branchName, newRuleDTO)
* 作用: 增/删/改app > cluster > nmspc的子分支branch的灰度规则
* 过程:
    1. 根据app > cluster > nmspc与branchName获取旧的rule
    2. 根据app > cluster > nmspc获取旧的release
    3. 将旧releaseId(*无则设为0*)与新rule关联, 然后保存新rule到数据库
    4. 如果存在旧rule, 则将旧rule从数据库删除
    5. 保存releaseHistory
    6. **发送更新灰度规则的消息异步**

deleteBranch(appId, clutserName, namespaceName, branchName, operator)
* 作用: 删除灰度分支
    * 该方法负责删除灰度集群, 更新灰度规则, 创建与操作相关的releaseHistory, **但不负责合并分支**
* 过程:
    1. 检查子分支是否存在
        1. 检查app > cluster(父) > nmspc是否存在
        2. 检查app > cluster(父) > nmspc的子命名空间是否存在
    2. 删除分支
        1. 检查app > cluster(子)是否存在, 不存在则可直接返回
        2. 删除rule (创建一个新的空rule, 同时删除旧rule)
        3. 删除app > cluster(子)
        4. 创建releaseHistory
        5. 发送release相关的异步消息(用DatabaseMessageSender)

loadNamespaceBranch(appId, clusterName, namespaceName)
* 作用: 
* 过程:
    1. 检查app > cluster > nmspc是否存在
    2. 查找app > cluster > nmspc的分支(子命名空间)
        1. 根据app与命名空间名找到 app > cluster > nmspc列表
        2. 查找 app > cluster的子集群
        3. 在1中的app > cluster > nmspc列表中查找cluster与2的子集群一致的, 找到就返回


## NamespaceController

create(appId, clusterName, namespaceDTO)
* 作用: 创建app > cluster > nmspc
* 过程:
    1. 检查是否存在
    2. 创建新的app > cluster > nmspc存到数据库

delete(appId, clusterName, namespaceName, operator)
* 作用: 删除app > cluster > nmspc
* 过程:
    1. 检查是否存在
    2. 删除
        1. 批量删除app > cluster > nmspc的**items**
        2. 批量删除app > cluster > nmspc的**commits**
        3. 如果app > cluster > nmspc在主分支, 则批量删除其**releases** (灰度分支没有release ???)
        4. 如果app > cluster > nmspc有子分支(子命名空间), 则删除**子分支**
            1. 删除子cluster
            2. 删除子分支的灰度规则
            3. 创建releaseHistory
            4. 批量删除子分支的release
        5. 批量删除app > cluster > nmspc的**releaseHistories**
        6. 批量删除依赖app > cluster > nmspc的**客户端实例instances**
        7. 释放namespace锁
        8. 删除app > cluster > nmspc (在数据库中将该记录设为abandoned)

find(appId, clusterName)
* 作用: 查找app > cluster下的namespace

get(namespaceId)

get(appId, clusterName, namespaceName)

findPublicNamespaceForAssociatedNamespace(appId, clusterName, namespaceName)
* 作用: 为关联命名空间寻找公共命名空间
* 返回公共命名空间的优先级:
    1. 最近publish过的(带有latestRelease)的app > cluster > nmspc
    2. 最近publish过的(带有latestRelease)的app > default > nmspc
    3. app > cluster > nmspc
    4. app > default > nmspc
    

namespacePublishInfo(appId)
* 作用: 为app下的clusters生成Map--"cluster->cluster有未publish的namespace"
* 过程: 判断namespace是否未publish的方法: namespace存在更改时间晚于最后一次release的item, 或者namespace拥有item但却没有release

## NamespaceLockController

getNamespaceLockOwner(appId, cluster, namespaceName)
* 作用: 查询 app > cluster > namespaceName 是否被锁 (是否在数据库中有锁记录)
* 过程:
    1. 检查 app > cluster > namespaceName 是否存在
    2. 如果配置中没有打开NamespaceLock则返回null
    3. 从数据库查找锁记录, 没有则返回null
    4. 返回dto(保存namespaceId)


## **ReleaseController**

get(releaseId)

findReleaseByIds(releaseIds)

findAllReleases(appId, clusterName, namespaceName, page)

findActiveReleases(appId, clusterName, namespaceName)

getLatest(appId, clusterName, namespaceName)

**publish**(appId, clusterName, namespaceName, releaseName, comment, operator, isEmergencyPublish)
* 作用: 发布配置项
* 过程:
    1. 检查app > cluster >nmspc是否存在, 存在抛异常
    2. 发布
        1. 检查Namespace锁, 在**非紧急发布**情况下如果被锁, 则抛异常
        2. 获取app > cluster >nmspc下的所有配置项items (未发布与已发布) 
        3. 如果*nmspc为灰度*(即存在父命名空间), 则**发布分支命名空间** (灰度发布) 然后返回
        4. 在主分支发布release: 创建新release, 并获取上一个release用于创建releaseHistory
        5. 如果app > cluster >nmspc有子命名空间(灰度), 则**将更改合并到分支上**
        6. 如果存在子命名空间, 则要合并分支(以新的主分支release为基础生成灰度分支)
            1. 计算旧的主release与分支release的配置集的差集(根据releaseHistory获取或者比较新旧release的配置集获取)
            2. 将差集应用在新的主release配置集上, 生成新的分支release配置集
        7. 返回release
    3. 发异步消息(DatabaseMessageSender)
* 过程2.3: 检查nmspc是否为灰度 (尝试获取app > cluster >nmspc的父命名空间)
    1. 获取app > cluster, 然后根据其父集群的ID查找父集群
* 过程2.3: **发布分支命名空间** (灰度发布)
    1. 获取父命名空间最新的release
    2. 获取父命名空间在该release下的配置项items
    3. 以父命名空间配置集为基础, 生成灰度配置集(覆盖同名项, 删除指定项(如果有指定要删的))
    4. 创建新release与releaseHistory
        1. 获取上一个release
        2. 在release记录中添加与操作相关的信息(如**branchReleaseKeys**, 在过程2.5能用上)
        3. 创建新release
        4. 更新关联关系releaseRule(更新其关联的releaseId)
        5. 创建新的releaseHistory
* 过程2.5: 检查app > cluster >nmspc是否有分支
    1. 获取app > nmspc对应于所有cluster的app > cluster > nmspc
    2. 获取cluster的子集群
    3. 用cluster的子集群名去匹配app > cluster > nmspc
* 过程2.5: **将更改合并到灰度分支上**
    1. 获取灰度分支命名空间的上一个release
    2. 获取灰度分支命名空间的上一个release的所有配置项(主分支与灰度分支)
    3. 获取灰度分支命名空间的上一个release的灰度分支特有配置项的key集(branchReleaseKeys)
    4. 获取主命名空间的对应于上一个release的所有配置项(旧配置集)
    5. 根据234来推断上一release灰度分支对主分支配置集的更改, 产生一个表示配置项继承的map
    6. 将5的map应用于最新的主分支release, 发布新的灰度release

updateAndPublish(appId, clusterName, namespaceName, releaseName, branchName, deleteBranch, releaseComment, isEmergencyPublish, changeSets)
* 作用: 全量发布, 然后删除灰度分支(可选)
* 过程: 
    1. 确认app > cluster > nmspc存在
    2. 将灰度分支的配置项合并到主分支
        1. 检查Namespace锁, 非紧急发布情况下被锁, 则抛异常
        2. 将更改集changeSet(灰度分支对主分支的配置项的更改)应用到主分支, 增/删/改app > cluster > nmspc的items, 并创建commit
        3. 获取上一个灰度分支的releaseId
        4. 为app > cluster > nmspc创建release
        5. 创建releaseHistory
    3. 删除灰度分支(可选)
    4. 发异步消息(databaseMessageSender)

rollback(releaseId, operator)
* 过程:
    1. 先确认release存在, 且未被删除
    2. 确认有两个以上的活跃的Release(当前的与上一个), 否则无法回滚
    3. 删除最新的release(将其记录设置为abandoned)
    4. 创建新的releaseHistory
    5. 发异步消息(databaseMessageSender)
 
publish(appId, clusterName, namesapceName, operator, releasename, releaseComment, isEmergencyPublish, grayDelKeys)
* 作用: ???

## ReleaseHistoryController

findReleaseHistoriesByNamespace(appId, clusterName, namespaceName, pageable)

findReleaseHistoryByReleaseIdAndOperation( releaseId, operation, pageable) ??? 为什么会有多个相同的releaseId

findReleaseHistoryByPreviousReleaseIdAndOperation(previousReleaseId, operation, pageable)
* 作用： 根据previousReleaseId查找releaseHistory列表


