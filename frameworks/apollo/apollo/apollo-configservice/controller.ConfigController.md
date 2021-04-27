# ConfigController

<!-- ConfigController:
* queryConfig():
    1. Namespace去.properties后缀, 转小写
    2. 先查找私有的
    3. 再查找公有的, 找不到则404
    4. 审计
    5. 用户提供的release版本一致则返回304
    6. 否则返回配置
* namespaceBelongsToAppId()
* findPublicConfig()
    * 根据Namespace找到其AppId, 再结合其所在AppId查找配置 -->

```java
@RestController
@RequestMapping("/configs")
public class ConfigController {
  private static final Splitter X_FORWARDED_FOR_SPLITTER = Splitter.on(",").omitEmptyStrings()
      .trimResults();
  private final ConfigService configService;
  private final AppNamespaceServiceWithCache appNamespaceService;
  private final NamespaceUtil namespaceUtil;
  private final InstanceConfigAuditUtil instanceConfigAuditUtil;
  private final Gson gson;

  private static final Type configurationTypeReference = new TypeToken<Map<String, String>>() {
      }.getType();

  public ConfigController(
      final ConfigService configService,
      final AppNamespaceServiceWithCache appNamespaceService,
      final NamespaceUtil namespaceUtil,
      final InstanceConfigAuditUtil instanceConfigAuditUtil,
      final Gson gson) {
    this.configService = configService;
    this.appNamespaceService = appNamespaceService;
    this.namespaceUtil = namespaceUtil;
    this.instanceConfigAuditUtil = instanceConfigAuditUtil;
    this.gson = gson;
  }

  @GetMapping(value = "/{appId}/{clusterName}/{namespace:.+}")
  public ApolloConfig queryConfig(@PathVariable String appId, @PathVariable String clusterName,
                                  @PathVariable String namespace,
                                  @RequestParam(value = "dataCenter", required = false) String dataCenter,
                                  @RequestParam(value = "releaseKey", defaultValue = "-1") String clientSideReleaseKey,
                                  @RequestParam(value = "ip", required = false) String clientIp,
                                  @RequestParam(value = "messages", required = false) String messagesAsString,
                                  HttpServletRequest request, HttpServletResponse response) throws IOException {
    String originalNamespace = namespace;
    
    namespace = namespaceUtil.filterNamespaceName(namespace); // 去除".properties"后缀

    /*
        在缓存中查找该namespace, :
        1. 将lowerCase("${appId}+${namespace}")作为key;
        2. 在"application"命名空间的cache查找该key, 找到则覆盖原有的namespace参数值;
        3. 在public命名空间的cache中查找该key, 找到则覆盖原有的namespace参数值;
        4. 都没找到, 则继续用原值.
    */
    namespace = namespaceUtil.normalizeNamespace(appId, namespace);

    
    if (Strings.isNullOrEmpty(clientIp)) { // 无clientIp参数则从request中获取
      clientIp = tryToGetClientIp(request);
    }

    ApolloNotificationMessages clientMessages = transformMessages(messagesAsString); // NotifactionMessage 格式转换(String转对象)

    List<Release> releases = Lists.newLinkedList(); // 保存release查找结果

    /*
        先找app中的私有 namespace.
    */
    String appClusterNameLoaded = clusterName;
    if (!ConfigConsts.NO_APPID_PLACEHOLDER.equalsIgnoreCase(appId)) {
      Release currentAppRelease = configService.loadConfig(appId, clientIp, appId, clusterName, namespace,
          dataCenter, clientMessages); // 根据配置, 从cache或db查找

      if (currentAppRelease != null) {
        releases.add(currentAppRelease); // 保存查找结果
        //we have cluster search process, so the cluster name might be overridden
        appClusterNameLoaded = currentAppRelease.getClusterName(); // 更新cluster名
      }
    }

    /***************************************************************
    此处可能发生的并发问题:
        * 假如在上一步找到了app中指定的私有namespace, 并且此时发生以下情况: 
            1. 数据库中该namespace被删除
            2. 另一个app新增了一个公开的同名namespace, 且cluster名也相同
        * 则下一步又会获取到这个公有的namespace
    ***************************************************************/

    /*
        如果namespace不属于app, 则要查找public的namespace.
        * 从namespace反查出所属appId, 再按私有namespace的方法查找
    */
    if (!namespaceBelongsToAppId(appId, namespace)) {
      Release publicRelease = this.findPublicConfig(appId, clientIp, clusterName, namespace,
          dataCenter, clientMessages); 
      if (!Objects.isNull(publicRelease)) {
        releases.add(publicRelease);
      }
    }

    /*
        找不到config则返回404.
    */
    if (releases.isEmpty()) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND,
          String.format(
              "Could not load configurations with appId: %s, clusterName: %s, namespace: %s",
              appId, clusterName, originalNamespace));
      Tracer.logEvent("Apollo.Config.NotFound",
          assembleKey(appId, clusterName, originalNamespace, dataCenter));
      return null;
    }

    /*
        审计releases
        * 阻塞队列+单线程池实现异步审计
    */
    auditReleases(appId, clusterName, dataCenter, clientIp, releases);

    
    String mergedReleaseKey = releases.stream().map(Release::getReleaseKey)
            .collect(Collectors.joining(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR));

    /*  
        client端给出的release的key与查找到的key一致(即release未更新), 则告诉client 304(未修改)
    */
    if (mergedReleaseKey.equals(clientSideReleaseKey)) {
      // Client side configuration is the same with server side, return 304
      response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
      Tracer.logEvent("Apollo.Config.NotModified",
          assembleKey(appId, appClusterNameLoaded, originalNamespace, dataCenter));
      return null;
    }

    /*
        release已更新, 则返回新的配置release
    */
    ApolloConfig apolloConfig = new ApolloConfig(appId, appClusterNameLoaded, originalNamespace,
        mergedReleaseKey);

    // 合并releases.
    // * 逻辑上讲, releases列表只能有一个release(私有/公有)
    // * 但在高并发情况下, 有可能releases列表中同时出现两个release(一个公有一个私有)
    // * 不同release中可能有同名的key, 排前面的release (私有的) 优先级更高, 不会被覆盖
    apolloConfig.setConfigurations(mergeReleaseConfigurations(releases));

    Tracer.logEvent("Apollo.Config.Found", assembleKey(appId, appClusterNameLoaded,
        originalNamespace, dataCenter));
    return apolloConfig;
  }

  private boolean namespaceBelongsToAppId(String appId, String namespaceName) {
    //Every app has an 'application' namespace
    if (Objects.equals(ConfigConsts.NAMESPACE_APPLICATION, namespaceName)) {
      return true;
    }

    //if no appId is present, then no other namespace belongs to it
    if (ConfigConsts.NO_APPID_PLACEHOLDER.equalsIgnoreCase(appId)) {
      return false;
    }

    AppNamespace appNamespace = appNamespaceService.findByAppIdAndNamespace(appId, namespaceName);

    return appNamespace != null;
  }

  /**
   * 查找公有的namespace:
   *    从namespace反查出所属appId, 再按私有namespace的方法查找
   * @param clientAppId the application which uses public config
   * @param namespace   the namespace
   * @param dataCenter  the datacenter
   */
  private Release findPublicConfig(String clientAppId, String clientIp, String clusterName,
                                   String namespace, String dataCenter, ApolloNotificationMessages clientMessages) {
    AppNamespace appNamespace = appNamespaceService.findPublicNamespaceByName(namespace);

    //check whether the namespace's appId equals to current one
    if (Objects.isNull(appNamespace) || Objects.equals(clientAppId, appNamespace.getAppId())) {
      return null;
    }

    String publicConfigAppId = appNamespace.getAppId();

    return configService.loadConfig(clientAppId, clientIp, publicConfigAppId, clusterName, namespace, dataCenter,
        clientMessages);
  }

  /**
   * Merge configurations of releases.
   * Release in lower index override those in higher index
   * 合并配置中的key-value对.
   * releases列表中, 先出现的release优先级更高, 不会被后面的值覆盖.
   */
  Map<String, String> mergeReleaseConfigurations(List<Release> releases) {
    Map<String, String> result = Maps.newHashMap();
    for (Release release : Lists.reverse(releases)) {
      result.putAll(gson.fromJson(release.getConfigurations(), configurationTypeReference));
    }
    return result;
  }

  // 字符串拼接
  private String assembleKey(String appId, String cluster, String namespace, String dataCenter) {
    List<String> keyParts = Lists.newArrayList(appId, cluster, namespace);
    if (!Strings.isNullOrEmpty(dataCenter)) {
      keyParts.add(dataCenter);
    }
    return keyParts.stream().collect(Collectors.joining(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR));
  }

  /*
    对releases列表的每个release进行审计(阻塞队列+单线程池实现异步审计)
  */
  private void auditReleases(String appId, String cluster, String dataCenter, String clientIp,
                             List<Release> releases) {
    if (Strings.isNullOrEmpty(clientIp)) {
      //no need to audit instance config when there is no ip
      return;
    }
    for (Release release : releases) {
      instanceConfigAuditUtil.audit(appId, cluster, dataCenter, clientIp, release.getAppId(),
          release.getClusterName(),
          release.getNamespaceName(), release.getReleaseKey());
    }
  }

  private String tryToGetClientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-FORWARDED-FOR");
    if (!Strings.isNullOrEmpty(forwardedFor)) {
      return X_FORWARDED_FOR_SPLITTER.splitToList(forwardedFor).get(0);
    }
    return request.getRemoteAddr();
  }

  // 消息格式转换
  ApolloNotificationMessages transformMessages(String messagesAsString) {
    ApolloNotificationMessages notificationMessages = null;
    if (!Strings.isNullOrEmpty(messagesAsString)) {
      try {
        notificationMessages = gson.fromJson(messagesAsString, ApolloNotificationMessages.class);
      } catch (Throwable ex) {
        Tracer.logError(ex);
      }
    }

    return notificationMessages;
  }
}

```

