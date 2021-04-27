# ConfigService系列

## 1. AbstractConfigService

### 1.1 特性

* implements
    * ```ConfigService```:
        ```java
        
        public interface ConfigService extends ReleaseMessageListener {
        /**
        * 根据参数中的信息加载config
        *
        * @param clientAppId       : client所在app (灰度发布时据此进行匹配)
        * @param clientIp          : client的IP    (灰度发布时据此进行匹配)
        * @param configAppId       : 所请求release的所在app
        * @param configClusterName : 所请求release的所在cluster
        * @param configNamespace   : 所请求release的所在namespace
        * @param dataCenter        : client的数据中心 (作为configClusterName的后备)
        * @param clientMessages    : client给的其它信息 (用于辅助查找release)
        * @return the Release
        */
        Release loadConfig(
            String clientAppId, String clientIp 
            String configAppId, String configClusterName, String configNamespace, 
            String dataCenter, ApolloNotificationMessages clientMessages);
        }
        ```
    * ```ReleaseMessageListener```:
        ```java
        /**
            上个接口继承了ReleaseMessageListener, 强制对ReleaseMessage进行处理
            * ReleaseMessage: 配置被发布时, 伴随产生的消息
        */
        public interface ReleaseMessageListener {
        void handleMessage(ReleaseMessage message, String channel);
        }
        ```
* 作用:
    * AbstractConfigService应用了**模板模式**, 提供了模板方法```loadConfig(...)```
        * **```loadConfig(...)```方法**: 按以下顺序尝试获取给定release
            1. 根据appId+**cluster**+namespace获取**灰度**release;
            2. 根据appId+**cluster**+namespace获取**最近活跃的**release
            3. 根据appId+**clientDataCenter**+namespace获取**灰度**release;
            4. 根据appId+**clientDataCenter**+namespace获取**最近活跃的**release
            5. 根据appId+**defaultCluster**+namespace获取**灰度**release;
            6. 根据appId+**defaultCluster**+namespace获取**最近活跃的**release
        * 获取release可以从**db**获取, 也可以带**cache**
* 使用场景:
    * 可以继承以下方法实现自己的ConfigService:
        * ```findActiveOne(releaseId, clientMessages)```
        * ```findLatestActiveRelease(configAppId, configClusterName, configNamespaceName, clientMessages)```
    * 例如
        * ```DefaultConfigService```的实现: 直接从db读取release (下一节)
        * ```ConfigServiceWithCache```的实现: 带cache查询release (下下节)

### 1.2 源码解读

```java
public abstract class AbstractConfigService implements ConfigService {
  @Autowired
  private GrayReleaseRulesHolder grayReleaseRulesHolder;


  /**
    加载config (模板方法)
    * @param clientAppId       : client所在app (灰度发布时据此进行匹配)
    * @param clientIp          : client的IP    (灰度发布时据此进行匹配)
    * @param configAppId       : 所请求release的所在app
    * @param configClusterName : 所请求release的所在cluster
    * @param configNamespace   : 所请求release的所在namespace
    * @param String            : client的数据中心   (作为configClusterName的后备)
    * @param clientMessages    : client给的其它信息 (用于辅助查找release)
  */
  @Override
  public Release loadConfig(String clientAppId, String clientIp, String configAppId, String configClusterName,
      String configNamespace, String dataCenter, ApolloNotificationMessages clientMessages) {
    
    /*
        1. 先从指定的cluster中查找release
    */
    if (!Objects.equals(ConfigConsts.CLUSTER_NAME_DEFAULT, configClusterName)) {
      Release clusterRelease = findRelease(clientAppId, clientIp, configAppId, configClusterName, configNamespace,
          clientMessages);

      if (!Objects.isNull(clusterRelease)) {
        return clusterRelease;
      }
    }

    /*
        2. 再从client的数据中心中查找release
    */
    if (!Strings.isNullOrEmpty(dataCenter) && !Objects.equals(dataCenter, configClusterName)) {
      Release dataCenterRelease = findRelease(clientAppId, clientIp, configAppId, dataCenter, configNamespace,
          clientMessages);
      if (!Objects.isNull(dataCenterRelease)) {
        return dataCenterRelease;
      }
    }

    /*
        3. 最后从默认cluster中寻找release
    */
    return findRelease(clientAppId, clientIp, configAppId, ConfigConsts.CLUSTER_NAME_DEFAULT, configNamespace,
        clientMessages);
  }

  /**
   * 查找release
   *    1. 先查找有没合适的灰度release
   *    2. 再查找最近活跃的非灰度release
   * @param clientAppId       : client所在app (灰度发布时据此进行匹配)
   * @param clientIp          : client的IP    (灰度发布时据此进行匹配)
   * @param configAppId       : 所请求release的所在app
   * @param configClusterName : 所请求release的所在cluster
   * @param configNamespace   : 所请求release的所在namespace
   * @param clientMessages    : client给的其它信息 (用于辅助查找release)
   * @return the release
   */
  private Release findRelease(String clientAppId, String clientIp, String configAppId, String configClusterName,
      String configNamespace, ApolloNotificationMessages clientMessages) {

    /*
        1. 根据clientAppId+clientIp寻找对应的灰度release的Id 
    */
    Long grayReleaseId = grayReleaseRulesHolder.findReleaseIdFromGrayReleaseRule(clientAppId, clientIp, configAppId,
        configClusterName, configNamespace);

    Release release = null;

    /*
        2. 找到了灰度release的ID则可以根据灰度release的ID获取对应的release
    */
    if (grayReleaseId != null) {
      release = findActiveOne(grayReleaseId, clientMessages);
    }

    /*
        3. 无对应的灰度release的ID, 则组合appId+cluster+namespace查找最近活跃的release
    */
    if (release == null) {
      release = findLatestActiveRelease(configAppId, configClusterName, configNamespace, clientMessages);
    }

    return release;
  }

  /**
   * 根据release的ID来查找release
   */
  protected abstract Release findActiveOne(long id, ApolloNotificationMessages clientMessages);

  /**
   * 根据config的app+cluster+namespace来组合查找其最近一次活跃的release
   */
  protected abstract Release findLatestActiveRelease(String configAppId, String configClusterName,
      String configNamespaceName, ApolloNotificationMessages clientMessages);
```

## 2. DefaultConfigService

### 2.1 特性

* implements:
    * ```ConfigService```
    * ```ReleaseMessageListener```
* extends:
    * ```AbstractConfigService```
* 作用:
    * 实现从db中获取release的ConfigService (无cache)

### 2.2 源码解读
```java
public class DefaultConfigService extends AbstractConfigService {

  @Autowired
  private ReleaseService releaseService;   // releaseService直接调用repository类从db读取

  /*
    直接从db根据灰度release的id查找灰度release
  */
  @Override
  protected Release findActiveOne(long id, ApolloNotificationMessages clientMessages) {
    return releaseService.findActiveOne(id); 
  }

  /*
    直接从db根据appId+cluster+namespace查找release
  */
  @Override
  protected Release findLatestActiveRelease(String configAppId, String configClusterName, String configNamespace, 
   ApolloNotificationMessages clientMessages 
   ) {
    return releaseService.findLatestActiveRelease(configAppId, configClusterName,
        configNamespace);
  }

  @Override // 继承自ReleaseMessageListener
  public void handleMessage(ReleaseMessage message, String channel) {
    // since there is no cache, so do nothing
  }
}
```

## 3. ConfigServiceWithCache

### 3.1 特性

* implements:
    * ```ConfigService```
    * ```ReleaseMessageListener```
* extends:
    * ```AbstractConfigService```
* 作用:
    * 实现带cache的获取release的ConfigService
        * configCache: 缓存release对象(非灰度)
            * 查找release的key为appId+cluster+namespace
        * configIdCache: 缓存release对象(灰度)
            * 查找release的key为灰度release的ID


### 2.3 源码解读
```java
/**
 * config service with guava cache
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ConfigServiceWithCache extends AbstractConfigService {
  private static final Logger logger = LoggerFactory.getLogger(ConfigServiceWithCache.class);
  private static final long DEFAULT_EXPIRED_AFTER_ACCESS_IN_MINUTES = 60;//1 hour
  private static final String TRACER_EVENT_CACHE_INVALIDATE = "ConfigCache.Invalidate";
  private static final String TRACER_EVENT_CACHE_LOAD = "ConfigCache.LoadFromDB";
  private static final String TRACER_EVENT_CACHE_LOAD_ID = "ConfigCache.LoadFromDBById";
  private static final String TRACER_EVENT_CACHE_GET = "ConfigCache.Get";
  private static final String TRACER_EVENT_CACHE_GET_ID = "ConfigCache.GetById";
  private static final Splitter STRING_SPLITTER =
      Splitter.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR).omitEmptyStrings();

  /*
    该服务直接调用repository类
  */
  @Autowired
  private ReleaseService releaseService;  

  /*
    该服务管理releaseMessage (releaseMessage是配置发布时伴随产生的消息)
  */
  @Autowired
  private ReleaseMessageService releaseMessageService; 

  /*
    用来缓存release(非灰度), key为${appId}+${cluster}+${namespace}
  */
  private LoadingCache<String, ConfigCacheEntry> configCache;

  /*
    用来缓存灰度release, key为灰度release的ID
  */
  private LoadingCache<Long, Optional<Release>> configIdCache;

  /*
    该Entry用来保存空的release(null)
  */
  private ConfigCacheEntry nullConfigCacheEntry;
  public ConfigServiceWithCache() {
    nullConfigCacheEntry = new ConfigCacheEntry(ConfigConsts.NOTIFICATION_ID_PLACEHOLDER, null);
  }

  /**
    初始化两个cache
  */
  @PostConstruct  // 该注解表示在Bean被构造后执行该方法
  void initialize() {
    /*
        构造configCache, 用于缓存release(非灰度)
    */
    configCache = CacheBuilder.newBuilder()
        .expireAfterAccess(DEFAULT_EXPIRED_AFTER_ACCESS_IN_MINUTES, TimeUnit.MINUTES) //设置1小时后过期
        .build(new CacheLoader<String, ConfigCacheEntry>() {
          /*
            load(key)方法用于指定cache从数据库获取数据的方法, 也可以做一些预处理
            key的格式为: ${appId}+${cluster}+${namespace}
          */
          @Override
          public ConfigCacheEntry load(String key) throws Exception {
            List<String> namespaceInfo = STRING_SPLITTER.splitToList(key);
            if (namespaceInfo.size() != 3) {
              Tracer.logError(
                  new IllegalArgumentException(String.format("Invalid cache load key %s", key)));
              return nullConfigCacheEntry;
            }

            Transaction transaction = Tracer.newTransaction(TRACER_EVENT_CACHE_LOAD, key);
            try {
              /*
                获取最近的配置发布的消息, 主要用到releaseMessage的id, 作为缓存数据是否过期的判据.
              */
              ReleaseMessage latestReleaseMessage = releaseMessageService.findLatestReleaseMessageForMessages(Lists
                  .newArrayList(key));

              /*
                从数据库获取最近活跃的release
              */
              Release latestRelease = releaseService.findLatestActiveRelease(namespaceInfo.get(0), namespaceInfo.get(1),
                  namespaceInfo.get(2));

              transaction.setStatus(Transaction.SUCCESS);

              /*
                notificationId: 用于判断cache中的数据(release)是否过期
                如果获取到的release和releaseMessage无效(null), 则返回一个表示null的entry (nullConfigCacheEntry)
              */
              long notificationId = latestReleaseMessage == null ? ConfigConsts.NOTIFICATION_ID_PLACEHOLDER : latestReleaseMessage
                  .getId();
              if (notificationId == ConfigConsts.NOTIFICATION_ID_PLACEHOLDER && latestRelease == null) {
                return nullConfigCacheEntry;
              }

              /*
                成功取得release, 则将notificationId和release打包到一个configCacheEntry
              */
              return new ConfigCacheEntry(notificationId, latestRelease);
            } catch (Throwable ex) {
              transaction.setStatus(ex);
              throw ex;
            } finally {
              transaction.complete();
            }
          }
        });

    /*
        构造configIdCache, 用于缓存灰度release的ID
    */
    configIdCache = CacheBuilder.newBuilder()
        .expireAfterAccess(DEFAULT_EXPIRED_AFTER_ACCESS_IN_MINUTES, TimeUnit.MINUTES) //设置1小时后过期
        .build(new CacheLoader<Long, Optional<Release>>() {
          
          /*
            key为灰度release的ID, 与上一个cache不同
          */
          @Override
          public Optional<Release> load(Long key) throws Exception {
            Transaction transaction = Tracer.newTransaction(TRACER_EVENT_CACHE_LOAD_ID, String.valueOf(key));
            try {

              Release release = releaseService.findActiveOne(key);  // 直接从数据库读取

              transaction.setStatus(Transaction.SUCCESS);

              return Optional.ofNullable(release);
            } catch (Throwable ex) {
              transaction.setStatus(ex);
              throw ex;
            } finally {
              transaction.complete();
            }
          }
        });
  }


  /*
    ??? 为什么configIdCache不用检查其数据(灰度release)是否过期, 也不用invalidate()?
        猜想: 
            * configCache中的key为${appId}+${cluster}+${namespace}, 对于新的release, key不变, 所以要关注其版本的变化.
            * 而configIdCache中key为灰度release的ID, 新的灰度release的ID不同(即key不同).
            * 过期的灰度release会保存在cache中直到超时, 不会再被访问到.
  */
  @Override
  protected Release findActiveOne(long id, ApolloNotificationMessages clientMessages) {
    Tracer.logEvent(TRACER_EVENT_CACHE_GET_ID, String.valueOf(id));
    return configIdCache.getUnchecked(id).orElse(null);
  }

  /*
    先从cache中获取release.
    如果cache中的release已过期, 再从db获取.
  */
  @Override
  protected Release findLatestActiveRelease(String appId, String clusterName, String namespaceName,
                                            ApolloNotificationMessages clientMessages) {
    String key = ReleaseMessageKeyGenerator.generate(appId, clusterName, namespaceName);

    Tracer.logEvent(TRACER_EVENT_CACHE_GET, key);

    ConfigCacheEntry cacheEntry = configCache.getUnchecked(key);
    /* 
        如果clientMessage中所给的数据版本比cache中的新,
        那cache中该key对应的数据则过期了,
        则刷新cache.
    */
    if (clientMessages != null && clientMessages.has(key) &&
        clientMessages.get(key) > cacheEntry.getNotificationId()) {
      //invalidate the cache and try to load from db again
      invalidate(key);                                      // 使缓存失效: 从cache中删除该key
      cacheEntry = configCache.getUnchecked(key);           // 迫使cache重新从db加载
    }

    return cacheEntry.getRelease();                         // 返回release
  }

  /*
    使cache中的key失效: 删除cache中的对应key
  */
  private void invalidate(String key) {
    configCache.invalidate(key);
    Tracer.logEvent(TRACER_EVENT_CACHE_INVALIDATE, key);
  }

  /** 
    在新的配置发布时(即产生新的release, 同时伴随着新releaseMessage的产生), 该方法会被调用(参数为新的releaseMessage)
    @param message:
        * 在apollo中, message主要由adminservice产生, 保存在ApolloConfigDB中
        * 同时在本实例(config-service)中配置了ReleaseMessageScanner, 用于定时扫描ApolloConfigDB中新增的releaseMessage
            调用已注册的ReleaseMessageListener的handleMessage(message, channel)方法
        * 此处message格式为: ${appId}+${cluster}+${namespace}, 与查询release用的key保持一致
  */
  @Override
  public void handleMessage(ReleaseMessage message, String channel) {
    logger.info("message received - channel: {}, message: {}", channel, message);
    if (!Topics.APOLLO_RELEASE_TOPIC.equals(channel) || Strings.isNullOrEmpty(message.getMessage())) {
      return;
    }

    try {
      invalidate(message.getMessage());  // message格式为: ${appId}+${cluster}+${namespace}, 与查询release用的key保持一致

      // 迫使cache重新从数据库加载
      configCache.getUnchecked(message.getMessage());
    } catch (Throwable ex) {

    }
  }

  /*
    用于关联notificationId与release
  */
  private static class ConfigCacheEntry {
    private final long notificationId;
    private final Release release;

    public ConfigCacheEntry(long notificationId, Release release) {
      this.notificationId = notificationId;
      this.release = release;
    }

    public long getNotificationId() {
      return notificationId;
    }

    public Release getRelease() {
      return release;
    }
  }
}
```