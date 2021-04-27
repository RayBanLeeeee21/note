## RemoteConfigRepository

功能:
* 提供远程的config: 
    1. 如果内存中没有config, 则先从远程同步config到内存
    2. 返回内存中的config
* 同步config到内存:
    1. 选择config service
    2. 发送HTTP请求获取config
    3. 同步到内存
* 定时同步(5 min)
* 实时同步: long poll返回新的config信息时, onLongPollNotified()被调用, 立即同步


```java
package com.ctrip.framework.apollo.internals;

// import ...

/**
 * @author jason song(song_s@ctrip.com)
 */
public class RemoteConfigRepository extends AbstractConfigRepository {
    private static final Logger logger = LoggerFactory.getLogger(RemoteConfigRepository.class);
    private static final Joiner STRING_JOINER = Joiner.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR);
    private static final Joiner.MapJoiner MAP_JOINER = Joiner.on("&").withKeyValueSeparator("=");
    private static final Escaper pathEscaper = UrlEscapers.urlPathSegmentEscaper();
    private static final Escaper queryParamEscaper = UrlEscapers.urlFormParameterEscaper();

    private final ConfigServiceLocator m_serviceLocator;
    private final HttpUtil m_httpUtil;
    private final ConfigUtil m_configUtil;
    private final RemoteConfigLongPollService remoteConfigLongPollService;
    private volatile AtomicReference<ApolloConfig> m_configCache;
    private final String m_namespace;
    private final static ScheduledExecutorService m_executorService;
    private final AtomicReference<ServiceDTO> m_longPollServiceDto;
    private final AtomicReference<ApolloNotificationMessages> m_remoteMessages;
    private final RateLimiter m_loadConfigRateLimiter;
    private final AtomicBoolean m_configNeedForceRefresh;
    private final SchedulePolicy m_loadConfigFailSchedulePolicy;
    private final Gson gson;

    static {
        m_executorService = Executors.newScheduledThreadPool(1,
                ApolloThreadFactory.create("RemoteConfigRepository", true));
    }

    /**
     * Constructor.
     *
     * @param namespace the namespace
     */
    public RemoteConfigRepository(String namespace) {
        m_namespace = namespace;
        m_configCache = new AtomicReference<>();
        m_configUtil = ApolloInjector.getInstance(ConfigUtil.class);
        m_httpUtil = ApolloInjector.getInstance(HttpUtil.class);
        m_serviceLocator = ApolloInjector.getInstance(ConfigServiceLocator.class);
        remoteConfigLongPollService = ApolloInjector.getInstance(RemoteConfigLongPollService.class);
        m_longPollServiceDto = new AtomicReference<>();
        m_remoteMessages = new AtomicReference<>();
        m_loadConfigRateLimiter = RateLimiter.create(m_configUtil.getLoadConfigQPS());
        m_configNeedForceRefresh = new AtomicBoolean(true);
        m_loadConfigFailSchedulePolicy = new ExponentialSchedulePolicy(m_configUtil.getOnErrorRetryInterval(),
                m_configUtil.getOnErrorRetryInterval() * 8);
        gson = new Gson();
        this.trySync();
        this.schedulePeriodicRefresh();
        this.scheduleLongPollingRefresh();
    }

    @Override
    public Properties getConfig() {
        // 先从配置源将配置同步到内存中
        if (m_configCache.get() == null) {
            this.sync();
        }

        // 返回内存中的配置
        return transformApolloConfigToProperties(m_configCache.get());
    }


    // 该类型repository不用设置upstream
    @Override
    public void setUpstreamRepository(ConfigRepository upstreamConfigRepository) {
        //remote config doesn't need upstream
    }

    @Override
    public ConfigSourceType getSourceType() {
        return ConfigSourceType.REMOTE;
    }

    // 定时refresh任务
    private void schedulePeriodicRefresh() {
        logger.debug("Schedule periodic refresh with interval: {} {}",
                m_configUtil.getRefreshInterval(), m_configUtil.getRefreshIntervalTimeUnit());
        m_executorService.scheduleAtFixedRate(
            new Runnable() {
                @Override
                public void run() {
                    Tracer.logEvent("Apollo.ConfigService", String.format("periodicRefresh: %s", m_namespace));
                    logger.debug("refresh config for namespace: {}", m_namespace);
                    trySync();  // 同步
                    Tracer.logEvent("Apollo.Client.Version", Apollo.VERSION);
                }
            },
            m_configUtil.getRefreshInterval(), 
            m_configUtil.getRefreshInterval(),
            m_configUtil.getRefreshIntervalTimeUnit()
        );
    }


    // 从远端获取config, 然后与旧的比较, 如果有区别, 则更改, 并通知监听配置的listener
    @Override
    protected synchronized void sync() {
        Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "syncRemoteConfig");

        try {

            ApolloConfig previous = m_configCache.get();
            ApolloConfig current = loadApolloConfig();  // 从远端获取
            if (previous != current) {
                logger.debug("Remote Config refreshed!");
                m_configCache.set(current);
                this.fireRepositoryChange(m_namespace, this.getConfig());  通知listener
            }

            if (current != null) {
                Tracer.logEvent(String.format("Apollo.Client.Configs.%s", current.getNamespaceName()),
                        current.getReleaseKey());
            }

            transaction.setStatus(Transaction.SUCCESS);
        } catch (Throwable ex) {
            transaction.setStatus(ex);
            throw ex;
        } finally {
            transaction.complete();
        }
    }

    //  
    private Properties transformApolloConfigToProperties(ApolloConfig apolloConfig) {
        Properties result = new Properties();
        result.putAll(apolloConfig.getConfigurations());
        return result;
    }

    // 从远端获取配置
    private ApolloConfig loadApolloConfig() {
        
        // rateLimiter决定要不要sleep()一段时间(控制速度)
        if (!m_loadConfigRateLimiter.tryAcquire(5, TimeUnit.SECONDS)) {
            //wait at most 5 seconds
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
            }
        }
        
        String appId = m_configUtil.getAppId();
        String cluster = m_configUtil.getCluster();
        String dataCenter = m_configUtil.getDataCenter();
        Tracer.logEvent("Apollo.Client.ConfigMeta", STRING_JOINER.join(appId, cluster, m_namespace));
        int maxRetries = m_configNeedForceRefresh.get() ? 2 : 1;
        long onErrorSleepTime = 0; // 0 means no sleep
        Throwable exception = null;

        // 调用serviceLocator, 从本地内存或者远端获取configService列表
        List<ServiceDTO> configServices = getConfigServices();
        String url = null;
        for (int i = 0; i < maxRetries; i++) {
            List<ServiceDTO> randomConfigServices = Lists.newLinkedList(configServices);
            Collections.shuffle(randomConfigServices);

            // 优先访问notify本客户端的server(放到第0个)
            if (m_longPollServiceDto.get() != null) {
                randomConfigServices.add(0, m_longPollServiceDto.getAndSet(null));
            }

            // 按顺序(随机顺序)向configServer发请求, 直到有一个成功
            for (ServiceDTO configService : randomConfigServices) {

                // 如果上一次尝试失败, 则要sleep一段时间再尝试
                // 初始为0
                if (onErrorSleepTime > 0) {
                    logger.warn(
                            "Load config failed, will retry in {} {}. appId: {}, cluster: {}, namespaces: {}",
                            onErrorSleepTime, m_configUtil.getOnErrorRetryIntervalTimeUnit(), appId, cluster, m_namespace);

                    try {
                        m_configUtil.getOnErrorRetryIntervalTimeUnit().sleep(onErrorSleepTime);
                    } catch (InterruptedException e) {
                        //ignore
                    }
                }

                url = assembleQueryConfigUrl(
                    configService.getHomepageUrl(), 
                    appId, cluster, m_namespace, dataCenter, 
                    m_remoteMessages.get(), 
                    m_configCache.get()
                );

                logger.debug("Loading config from {}", url);
                HttpRequest request = new HttpRequest(url);

                Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "queryConfig");
                transaction.addData("Url", url);
                try {

                    // 发请求获取config
                    HttpResponse<ApolloConfig> response = m_httpUtil.doGet(request, ApolloConfig.class);
                    m_configNeedForceRefresh.set(false); // 不强制刷新
                    m_loadConfigFailSchedulePolicy.success(); // 将ErrorRetryInterval重置为0

                    transaction.addData("StatusCode", response.getStatusCode());
                    transaction.setStatus(Transaction.SUCCESS);

                    // configService中配置未改变, 直接返回
                    if (response.getStatusCode() == 304) {
                        logger.debug("Config server responds with 304 HTTP status code.");
                        return m_configCache.get();
                    }

                    // 从response中获取结果(config)
                    ApolloConfig result = response.getBody();

                    logger.debug("Loaded config for {}: {}", m_namespace, result);

                    return result;
                } catch (ApolloConfigStatusCodeException ex) {
                    ApolloConfigStatusCodeException statusCodeException = ex;
                    //config not found
                    if (ex.getStatusCode() == 404) {
                        String message = String.format(
                                "Could not find config for namespace - appId: %s, cluster: %s, namespace: %s, " +
                                        "please check whether the configs are released in Apollo!",
                                appId, cluster, m_namespace);
                        statusCodeException = new ApolloConfigStatusCodeException(ex.getStatusCode(),
                                message);
                    }
                    Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(statusCodeException));
                    transaction.setStatus(statusCodeException);
                    exception = statusCodeException;
                } catch (Throwable ex) {
                    Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(ex));
                    transaction.setStatus(ex);
                    exception = ex;
                } finally {
                    transaction.complete();
                }

                // 如果要求强制刷新(如404), 则正常sleep(默认1秒), 否则睡眠时间指数增长
                onErrorSleepTime = m_configNeedForceRefresh.get() ?
                    m_configUtil.getOnErrorRetryInterval() :
                    m_loadConfigFailSchedulePolicy.fail();
            }

        }
        String message = String.format(
                "Load Apollo Config failed - appId: %s, cluster: %s, namespace: %s, url: %s",
                appId, cluster, m_namespace, url);
        throw new ApolloConfigException(message, exception);
    }

    // 生成GET HTTP请求
    String assembleQueryConfigUrl(
        String uri, String appId, String cluster, String namespace, String dataCenter,
        ApolloNotificationMessages remoteMessages, // 上一个notifiactionMessage
        ApolloConfig previousConfig // 上一个config
    ) {

        String path = "configs/%s/%s/%s";
        List<String> pathParams =
                Lists.newArrayList(pathEscaper.escape(appId), pathEscaper.escape(cluster),
                        pathEscaper.escape(namespace));
        Map<String, String> queryParams = Maps.newHashMap();

        if (previousConfig != null) {
            queryParams.put("releaseKey", queryParamEscaper.escape(previousConfig.getReleaseKey()));
        }

        if (!Strings.isNullOrEmpty(dataCenter)) {
            queryParams.put("dataCenter", queryParamEscaper.escape(dataCenter));
        }

        String localIp = m_configUtil.getLocalIp();
        if (!Strings.isNullOrEmpty(localIp)) {
            queryParams.put("ip", queryParamEscaper.escape(localIp));
        }

        if (remoteMessages != null) {
            queryParams.put("messages", queryParamEscaper.escape(gson.toJson(remoteMessages)));
        }

        String pathExpanded = String.format(path, pathParams.toArray());

        if (!queryParams.isEmpty()) {
            pathExpanded += "?" + MAP_JOINER.join(queryParams);
        }
        if (!uri.endsWith("/")) {
            uri += "/";
        }
        return uri + pathExpanded;
    }

    private void scheduleLongPollingRefresh() {
        remoteConfigLongPollService.submit(m_namespace, this);
    }

    // 被通知后
    // 1. 保存通知自己的configService实例
    // 2. 保存configService返回的ApolloNotificationMessages
    // 3. 将同步config的任务(请求config)交给线程池
    public void onLongPollNotified(ServiceDTO longPollNotifiedServiceDto, ApolloNotificationMessages remoteMessages) {
        m_longPollServiceDto.set(longPollNotifiedServiceDto); // 保存通知自己的configService实例
        m_remoteMessages.set(remoteMessages); // 保存configService返回的ApolloNotificationMessages
        m_executorService.submit(new Runnable() {
            @Override
            public void run() {
                m_configNeedForceRefresh.set(true);
                trySync();
            }
        });
    }

    // 获取configservice列表
    private List<ServiceDTO> getConfigServices() {
        List<ServiceDTO> services = m_serviceLocator.getConfigServices();
        if (services.size() == 0) {
            throw new ApolloConfigException("No available config service");
        }

        return services;
    }
}
```

