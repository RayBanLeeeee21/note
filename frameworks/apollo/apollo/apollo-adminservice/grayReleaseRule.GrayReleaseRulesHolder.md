# grayReleaseRule

## 1.1 特性
implements:
```JAVA
/**
    ReleaseMessageScanner定时扫描数据库
    发现新的releaseMessage时, 依次调用注册了的Listener的handleMessage(message, channel)方法
*/
public interface ReleaseMessageListener {
  void handleMessage(ReleaseMessage message, String channel);
}
```

作用:
* 监听数据库中的releaseMessage, **实时**更新cache中的灰度release的路由规则:
    1. 根据releaseMessage的消息内容(即命名空间标识 app > cluster >nmspc), 找到数据库中更新的规则列表
    2. 对比cache数据的版本号, 将新的规则列表加载(覆盖)到cache中
* **定时**更新cache中的灰度release的路由规则

应用场景:
* 该方法主要由configservice调用, 用来监听灰度规则的变化

### 1.2 源代码解读

```JAVA
package com.ctrip.framework.apollo.biz.grayReleaseRule;

// import ...

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public class GrayReleaseRulesHolder implements ReleaseMessageListener, InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(GrayReleaseRulesHolder.class);
    private static final Joiner STRING_JOINER = Joiner.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR);
    private static final Splitter STRING_SPLITTER =
            Splitter.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR).omitEmptyStrings();

    @Autowired
    private GrayReleaseRuleRepository grayReleaseRuleRepository;
    @Autowired
    private BizConfig bizConfig;

    private int databaseScanInterval;

    /**
        用于执行定时任务, 定时扫描rule是否有刷新
    */
    private ScheduledExecutorService executorService;

    /**
        GrayReleaseRuleCache: 针对指定配置的一组规则, 规定了哪些app下的哪些IP可以访问该配置
        * e.g., 
        {
            "app1":{IP1, IP2, ...},
            "app2":{IP3, IP4, ...}
        }
        * 下面的多值Map用于根据被监听配置的标识(app > cluster > nmspc), 查找该配置对应的一组规则
    */
    private Multimap<String, GrayReleaseRuleCache> grayReleaseRuleCache;

    /** 
        该配置用于根据客户端信息(clientApp+IP)与客户端感兴趣的namespace名, 查找对应的规则
    */
    private Multimap<String, Long> reversedGrayReleaseRuleCache;
    

    /**
        表示cache的版本, 用于判断cache是否过期
    */
    private AtomicLong loadVersion;

    public GrayReleaseRulesHolder() {
        // 初始化cache版本: 缓冲数据加载时, 会设置当前的加载版本, 然后以此来判断是否过期
        loadVersion = new AtomicLong(); 

        // 以下两个map必须是线程安全的
        grayReleaseRuleCache = Multimaps.synchronizedSetMultimap(HashMultimap.create());
        reversedGrayReleaseRuleCache = Multimaps.synchronizedSetMultimap(HashMultimap.create());
        
        // 用于定时刷新cache的线程池(单线程池)
        executorService = Executors.newScheduledThreadPool(1, ApolloThreadFactory
                .create("GrayReleaseRulesHolder", true));
    }

    @Override // 该方法在bean属性被注入后, 由框架自动执行
    public void afterPropertiesSet() throws Exception {

        populateDataBaseInterval(); // 懒加载databaseScanInterval

        // 初始化时同步执行扫描任务, 加载rule到缓存中, 执行完以后, 其它线程才能访问bean
        periodicScanRules();

        // 启动定时刷新cache的线程
        executorService.scheduleWithFixedDelay(
            this::periodicScanRules,
            getDatabaseScanIntervalSecond(),    // 初始延时
            getDatabaseScanIntervalSecond(),    // 间隔延时
            getDatabaseScanTimeUnit()           // 延时单位
        );
    }

    /**
        该方法监听releaseMessage, 在releaseMessage发生时, 立即检查规则的刷新
        * ReleaseMessageListener接口方法的调用机制:
            * DatabaseMessageSender类将releaseMessage存到数据库
            * ReleaseMessageScanner定时扫描数据库, 扫描到新的releaseMessage时, 调用handleMessage方法处理消息
    */
    @Override
    public void handleMessage(ReleaseMessage message, String channel) {
        // 检查topic合法性
        logger.info("message received - channel: {}, message: {}", channel, message);
        String releaseMessage = message.getMessage();
        if (!Topics.APOLLO_RELEASE_TOPIC.equals(channel) || Strings.isNullOrEmpty(releaseMessage)) {
            return;
        }

        // 解析出releaseMessage中描述的发生变动(即rule改变)的命名空间的标识 app > cluster > nmspc 
        List<String> keys = STRING_SPLITTER.splitToList(releaseMessage);
        if (keys.size() != 3) {
            logger.error("message format invalid - {}", releaseMessage);
            return;
        }
        String appId = keys.get(0);
        String cluster = keys.get(1);
        String namespace = keys.get(2);

        // 根据命名空间的标识 app > cluster > nmspc找到对应的规则列表
        List<GrayReleaseRule> rules = grayReleaseRuleRepository
                .findByAppIdAndClusterNameAndNamespaceName(appId, cluster, namespace);
        
        // 合并到cache中
        mergeGrayReleaseRules(rules);
    }

    /**
       该方法由守护线程定时调用, 进行扫描 
    */
    private void periodicScanRules() {
        Transaction transaction = Tracer.newTransaction("Apollo.GrayReleaseRulesScanner",
                "scanGrayReleaseRules");
        try {
            loadVersion.incrementAndGet(); // 扫描一次, 版本自增一次
            scanGrayReleaseRules(); // 扫描
            transaction.setStatus(Transaction.SUCCESS);
        } catch (Throwable ex) {
            transaction.setStatus(ex);
            logger.error("Scan gray release rule failed", ex);
        } finally {
            transaction.complete();
        }
    }

    /**
        从cache找到client(以app > IP标识)对应的灰度release的ID
    */
    public Long findReleaseIdFromGrayReleaseRule(
        String clientAppId, // 
        String clientIp, 
        String configAppId, 
        String configCluster, 
        String configNamespaceName
    ) {
        String key = assembleGrayReleaseRuleKey(configAppId, configCluster, configNamespaceName);
        if (!grayReleaseRuleCache.containsKey(key)) {
            return null;
        }

        // 原文注释: create a new list to avoid ConcurrentModificationException
        //     * grayReleaseRuleCache为多值map, get()方法返回一个value集合
        //     * 而foreach循环用Iteration实现, 如果在迭代过程中, value集合的元素被其它线程删除, 会抛ConcurrentModificationException
        //     * 因此要新创建一个list来进行迭代, 而不能直接迭代原集合
        List<GrayReleaseRuleCache> rules = Lists.newArrayList(grayReleaseRuleCache.get(key));
        
        // grayReleaseRule列表中, 每个元素对应一个分支(可能有些分支已经被删除)
        for (GrayReleaseRuleCache rule : rules) {
            // 检查分支状态, 跳过已删除的分支
            if (rule.getBranchStatus() != NamespaceBranchStatus.ACTIVE) {
                continue;
            }
            // 分支有效, 且client可以访问, 则返回该releaseId
            if (rule.matches(clientAppId, clientIp)) { // 指定appId与IP的client才能得到release
                return rule.getReleaseId();
            }
        }
        return null;
    }

    /**
     * 检查是否有针对客户端(以app > IP > namespace标识)的灰度rule
     * 注意: 即使该方法返回true, 也不一定说明客户端可以加载该release, 因为cluster(branch)可能不匹配否
     */
    public boolean hasGrayReleaseRule(String clientAppId, String clientIp, String namespaceName) {
        // 指定releaseId的可访问的app > client集合中包含该app > client
        return reversedGrayReleaseRuleCache.containsKey(assembleReversedGrayReleaseRuleKey(clientAppId, namespaceName, clientIp)) 
        // 或者该releaseId可以被app下所有IP访问
        || reversedGrayReleaseRuleCache.containsKey 
                (assembleReversedGrayReleaseRuleKey(clientAppId, namespaceName, GrayReleaseRuleItemDTO
                        .ALL_IP));
    }

    /**
        从头开始扫描数据库, 加载数据库中所有有效的rule到cache中
    */  
    private void scanGrayReleaseRules() {
        long maxIdScanned = 0;
        boolean hasMore = true;

        while (hasMore && !Thread.currentThread().isInterrupted()) {

            // 从 > maxIdScanned的记录开始处理
            List<GrayReleaseRule> grayReleaseRules = grayReleaseRuleRepository
                    .findFirst500ByIdGreaterThanOrderByIdAsc(maxIdScanned);
            if (CollectionUtils.isEmpty(grayReleaseRules)) {
                break;
            }

            // 合并到cache
            mergeGrayReleaseRules(grayReleaseRules);
            int rulesScanned = grayReleaseRules.size();
            maxIdScanned = grayReleaseRules.get(rulesScanned - 1).getId(); // 更新扫描记录数

            // 如果这一批扫描的记录刚好为500, 则说明可能还有记录, 继续下一轮扫描
            hasMore = rulesScanned == 500;
        }
    }

    /**
        将数据库的一批rule更新到cache中
    */
    private void mergeGrayReleaseRules(List<GrayReleaseRule> grayReleaseRules) {
        if (CollectionUtils.isEmpty(grayReleaseRules)) {
            return;
        }

        // 遍历从数据库加载的rule
        for (GrayReleaseRule grayReleaseRule : grayReleaseRules) {
            // 跳过未关联releaseId的grayReleaseRule
            // 无releaseId表示灰度分支未发布, 因此客户端没有必要访问
            if (grayReleaseRule.getReleaseId() == null || grayReleaseRule.getReleaseId() == 0) {
                continue;
            }

            // key: 标识rule所在的 app > cluster > nmspc
            String key = assembleGrayReleaseRuleKey(grayReleaseRule.getAppId(), grayReleaseRule
                    .getClusterName(), grayReleaseRule.getNamespaceName());

            // create a new list to avoid ConcurrentModificationException
            // 根据key, 从cache中取出对应app > cluster > nmspc的所有rule
            //    * 每个rule对应一个branchName
            List<GrayReleaseRuleCache> rules = Lists.newArrayList(grayReleaseRuleCache.get(key));
            GrayReleaseRuleCache oldRule = null;

            // 根据分支名来找到可能要被替换掉的oldRule
            for (GrayReleaseRuleCache ruleCache : rules) {
                if (ruleCache.getBranchName().equals(grayReleaseRule.getBranchName())) {
                    oldRule = ruleCache;
                    break;
                }
            }

            // cache中原来没有存OldRule
            // 且当前rule记录的分支状态为无效(说明rule被删除), 没必要加载到cache
            if (oldRule == null 
                && grayReleaseRule.getBranchStatus() != NamespaceBranchStatus.ACTIVE) {
                continue;
            }

            // branch的rule未被加载过
            // 或者 当前rule记录的ID更大(即该rule更新, 也不管新rule是否是isDelete)
            // 则将数据库的grayReleaseRule放到cache
            if (oldRule == null || grayReleaseRule.getId() > oldRule.getRuleId()) {
                addCache(key, transformRuleToRuleCache(grayReleaseRule));
                if (oldRule != null) { 
                    removeCache(key, oldRule); // 多值map不像普通map可以覆盖旧值, 因此要显式删除
                }
            
            // cache中有OldRule, 且oldRule的版本更新
            } else {

                // oldRule有效, 则更新其版本号
                if (oldRule.getBranchStatus() == NamespaceBranchStatus.ACTIVE) {
                    oldRule.setLoadVersion(loadVersion.get());
                
                // 从cache中删除落后两个版本号的oldRule
                } else if ((loadVersion.get() - oldRule.getLoadVersion()) > 1) {
                    //remove outdated inactive branch rule after 2 update cycles
                    removeCache(key, oldRule);
                }
            }
        }
    }

    /**
        根据GrayReleaseRuleCache, 更新两个cache
    */
    private void addCache(String key, GrayReleaseRuleCache ruleCache) {
        // 检查分支状态
        if (ruleCache.getBranchStatus() == NamespaceBranchStatus.ACTIVE) {
            for (GrayReleaseRuleItemDTO ruleItemDTO : ruleCache.getRuleItems()) {
                for (String clientIp : ruleItemDTO.getClientIpList()) {
                    reversedGrayReleaseRuleCache.put(assembleReversedGrayReleaseRuleKey(ruleItemDTO
                            .getClientAppId(), ruleCache.getNamespaceName(), clientIp), ruleCache.getRuleId());
                }
            }
        }
        grayReleaseRuleCache.put(key, ruleCache);
    }

    /**
        从两个cache清除与ruleCache相关的数据
    */
    private void removeCache(String key, GrayReleaseRuleCache ruleCache) {

        grayReleaseRuleCache.remove(key, ruleCache);

        for (GrayReleaseRuleItemDTO ruleItemDTO : ruleCache.getRuleItems()) {
            for (String clientIp : ruleItemDTO.getClientIpList()) {
                reversedGrayReleaseRuleCache.remove(assembleReversedGrayReleaseRuleKey(ruleItemDTO
                        .getClientAppId(), ruleCache.getNamespaceName(), clientIp), ruleCache.getRuleId());
            }
        }
    }

    /**
        将grayReleaseRule对象(PO)转换成GrayReleaseRuleCache对象
        * 主要将JSON格式的rule转成GrayReleaseRuleItemDTO对象
    */
    private GrayReleaseRuleCache transformRuleToRuleCache(GrayReleaseRule grayReleaseRule) {
        Set<GrayReleaseRuleItemDTO> ruleItems;
        try {
            ruleItems = GrayReleaseRuleItemTransformer.batchTransformFromJSON(grayReleaseRule.getRules());
        } catch (Throwable ex) {
            ruleItems = Sets.newHashSet();
            Tracer.logError(ex);
            logger.error("parse rule for gray release rule {} failed", grayReleaseRule.getId(), ex);
        }

        GrayReleaseRuleCache ruleCache = new GrayReleaseRuleCache(
            grayReleaseRule.getId(),
            grayReleaseRule.getBranchName(), 
            grayReleaseRule.getNamespaceName(), 
            grayReleaseRule.getReleaseId(), 
            grayReleaseRule.getBranchStatus(), 
            loadVersion.get(),                    // 设定为当前的版本
            ruleItems
        );

        return ruleCache;
    }

    // 加载扫描延时(间隔)
    private void populateDataBaseInterval() {
        databaseScanInterval = bizConfig.grayReleaseRuleScanInterval();
    }

    private int getDatabaseScanIntervalSecond() {
        return databaseScanInterval;
    }

    private TimeUnit getDatabaseScanTimeUnit() {
        return TimeUnit.SECONDS;
    }

    private String assembleGrayReleaseRuleKey(String configAppId, String configCluster, String
            configNamespaceName) {
        return STRING_JOINER.join(configAppId, configCluster, configNamespaceName);
    }

    private String assembleReversedGrayReleaseRuleKey(String clientAppId, String
            clientNamespaceName, String clientIp) {
        return STRING_JOINER.join(clientAppId, clientNamespaceName, clientIp);
    }

}

```