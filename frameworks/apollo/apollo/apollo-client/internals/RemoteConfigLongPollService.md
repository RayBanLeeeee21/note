## RemoteConfigLongPollService

作用:
* 发long poll 
    * 定时发notification(60s)给config service, 获取配置的最新状态
    * long poll后发现配置有更新, 则通知感兴趣的RemoteConfigRepository

```java
package com.ctrip.framework.apollo.internals;

// import ...

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public class RemoteConfigLongPollService {
    private static final Logger logger = LoggerFactory.getLogger(RemoteConfigLongPollService.class);
    private static final Joiner STRING_JOINER = Joiner.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR);
    private static final Joiner.MapJoiner MAP_JOINER = Joiner.on("&").withKeyValueSeparator("=");
    private static final Escaper queryParamEscaper = UrlEscapers.urlFormParameterEscaper();
    private static final long INIT_NOTIFICATION_ID = ConfigConsts.NOTIFICATION_ID_PLACEHOLDER;
    //90 seconds, should be longer than server side's long polling timeout, which is now 60 seconds
    private static final int LONG_POLLING_READ_TIMEOUT = 90 * 1000;
    private final ExecutorService m_longPollingService;
    private final AtomicBoolean m_longPollingStopped;
    private SchedulePolicy m_longPollFailSchedulePolicyInSecond;
    private RateLimiter m_longPollRateLimiter;
    private final AtomicBoolean m_longPollStarted;
    private final Multimap<String, RemoteConfigRepository> m_longPollNamespaces;
    private final ConcurrentMap<String, Long> m_notifications;
    private final Map<String, ApolloNotificationMessages> m_remoteNotificationMessages;//namespaceName -> watchedKey -> notificationId
    private Type m_responseType;
    private Gson gson;
    private ConfigUtil m_configUtil;
    private HttpUtil m_httpUtil;
    private ConfigServiceLocator m_serviceLocator;

    /**
     * Constructor.
     */
    public RemoteConfigLongPollService() {
        m_longPollFailSchedulePolicyInSecond = new ExponentialSchedulePolicy(1, 120); //in second
        m_longPollingStopped = new AtomicBoolean(false);
        m_longPollingService = Executors.newSingleThreadExecutor(
                ApolloThreadFactory.create("RemoteConfigLongPollService", true));
        m_longPollStarted = new AtomicBoolean(false);
        m_longPollNamespaces =
                Multimaps.synchronizedSetMultimap(HashMultimap.<String, RemoteConfigRepository>create());
        m_notifications = Maps.newConcurrentMap();
        m_remoteNotificationMessages = Maps.newConcurrentMap();
        m_responseType = new TypeToken<List<ApolloConfigNotification>>() {
        }.getType();
        gson = new Gson();
        m_configUtil = ApolloInjector.getInstance(ConfigUtil.class);
        m_httpUtil = ApolloInjector.getInstance(HttpUtil.class);
        m_serviceLocator = ApolloInjector.getInstance(ConfigServiceLocator.class);
        m_longPollRateLimiter = RateLimiter.create(m_configUtil.getLongPollQPS());
    }

    // 注册namespace对应的config, 及其repository
    public boolean submit(String namespace, RemoteConfigRepository remoteConfigRepository) {
        boolean added = m_longPollNamespaces.put(namespace, remoteConfigRepository);
        m_notifications.putIfAbsent(namespace, INIT_NOTIFICATION_ID);

        // 检查定时longPoll是否启动
        if (!m_longPollStarted.get()) {
            startLongPolling();
        }
        return added;
    }

    private void startLongPolling() {
        if (!m_longPollStarted.compareAndSet(false, true)) {
            //already started
            return;
        }
        try {
            final String appId = m_configUtil.getAppId();
            final String cluster = m_configUtil.getCluster();
            final String dataCenter = m_configUtil.getDataCenter();
            final long longPollingInitialDelayInMills = m_configUtil.getLongPollingInitialDelayInMills();
            m_longPollingService.submit(new Runnable() {
                @Override
                public void run() {
                    if (longPollingInitialDelayInMills > 0) {
                        try {
                            logger.debug("Long polling will start in {} ms.", longPollingInitialDelayInMills);
                            TimeUnit.MILLISECONDS.sleep(longPollingInitialDelayInMills);
                        } catch (InterruptedException e) {
                            //ignore
                        }
                    }
                    doLongPollingRefresh(appId, cluster, dataCenter); // 刷新(发notificationMessage)
                }
            });
        } catch (Throwable ex) {

            // 出异常, 停止
            m_longPollStarted.set(false);
            ApolloConfigException exception =
                    new ApolloConfigException("Schedule long polling refresh failed", ex);
            Tracer.logError(exception);
            logger.warn(ExceptionUtil.getDetailMessage(exception));
        }
    }

    void stopLongPollingRefresh() {
        this.m_longPollingStopped.compareAndSet(false, true);
    }

    private void doLongPollingRefresh(String appId, String cluster, String dataCenter) {
        final Random random = new Random();
        ServiceDTO lastServiceDto = null;  // 上一个configService
        while (!m_longPollingStopped.get() && !Thread.currentThread().isInterrupted()) {

            // 速率控制
            if (!m_longPollRateLimiter.tryAcquire(5, TimeUnit.SECONDS)) {
                //wait at most 5 seconds
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                }
            }
            Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "pollNotification");
            String url = null;
            try {

                // 随机获取一个configService
                if (lastServiceDto == null) {
                    List<ServiceDTO> configServices = getConfigServices();
                    lastServiceDto = configServices.get(random.nextInt(configServices.size()));
                }

                url = assembleLongPollRefreshUrl(lastServiceDto.getHomepageUrl(), appId, cluster, dataCenter,
                                m_notifications);

                logger.debug("Long polling from {}", url);
                HttpRequest request = new HttpRequest(url);
                request.setReadTimeout(LONG_POLLING_READ_TIMEOUT);

                transaction.addData("Url", url);

                // 发请求
                final HttpResponse<List<ApolloConfigNotification>> response =
                        m_httpUtil.doGet(request, m_responseType);

                logger.debug("Long polling response: {}, url: {}", response.getStatusCode(), url);
                if (response.getStatusCode() == 200 && response.getBody() != null) {
                    updateNotifications(response.getBody());
                    updateRemoteNotifications(response.getBody());
                    transaction.addData("Result", response.getBody().toString());
                    notify(lastServiceDto, response.getBody());  // 通知对应的remoteConfigRepository
                }

                // 负载均衡(只在304时做)
                if (response.getStatusCode() == 304 && random.nextBoolean()) {
                    lastServiceDto = null;
                }

                m_longPollFailSchedulePolicyInSecond.success();  // 成功, 则将错误后重试等待时间清0
                transaction.addData("StatusCode", response.getStatusCode());
                transaction.setStatus(Transaction.SUCCESS);
            } catch (Throwable ex) {
                lastServiceDto = null;
                Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(ex));
                transaction.setStatus(ex);
                long sleepTimeInSecond = m_longPollFailSchedulePolicyInSecond.fail();
                logger.warn(
                        "Long polling failed, will retry in {} seconds. appId: {}, cluster: {}, namespaces: {}, long polling url: {}, reason: {}",
                        sleepTimeInSecond, appId, cluster, assembleNamespaces(), url, ExceptionUtil.getDetailMessage(ex));
                try {
                    TimeUnit.SECONDS.sleep(sleepTimeInSecond);
                } catch (InterruptedException ie) {
                    //ignore
                }
            } finally {
                transaction.complete();
            }
        }
    }

    // 从m_longPollNamespaces中找出对更改的namespace感兴趣的RemoteConfigRepository, 通知它们namespace对应的配置已经更新
    // RemoteConfigRepository会再发请求给configService去获取新的config
    private void notify(ServiceDTO lastServiceDto, List<ApolloConfigNotification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return;
        }
        for (ApolloConfigNotification notification : notifications) {
            String namespaceName = notification.getNamespaceName();
            //create a new list to avoid ConcurrentModificationException
            List<RemoteConfigRepository> toBeNotified =
                    Lists.newArrayList(m_longPollNamespaces.get(namespaceName));
            ApolloNotificationMessages originalMessages = m_remoteNotificationMessages.get(namespaceName);
            ApolloNotificationMessages remoteMessages = originalMessages == null ? null : originalMessages.clone();
            //since .properties are filtered out by default, so we need to check if there is any listener for it
            toBeNotified.addAll(m_longPollNamespaces
                    .get(String.format("%s.%s", namespaceName, ConfigFileFormat.Properties.getValue())));
            for (RemoteConfigRepository remoteConfigRepository : toBeNotified) {
                try {
                    remoteConfigRepository.onLongPollNotified(lastServiceDto, remoteMessages);
                } catch (Throwable ex) {
                    Tracer.logError(ex);
                }
            }
        }
    }

    // 记录更改的namespace所对应的最新notificationId
    private void updateNotifications(List<ApolloConfigNotification> deltaNotifications) {
        for (ApolloConfigNotification notification : deltaNotifications) {
            if (Strings.isNullOrEmpty(notification.getNamespaceName())) {
                continue;
            }
            String namespaceName = notification.getNamespaceName();
            if (m_notifications.containsKey(namespaceName)) {
                m_notifications.put(namespaceName, notification.getNotificationId());
            }
            //since .properties are filtered out by default, so we need to check if there is notification with .properties suffix
            String namespaceNameWithPropertiesSuffix =
                    String.format("%s.%s", namespaceName, ConfigFileFormat.Properties.getValue());
            if (m_notifications.containsKey(namespaceNameWithPropertiesSuffix)) {
                m_notifications.put(namespaceNameWithPropertiesSuffix, notification.getNotificationId());
            }
        }
    }

    // 将更改的namespace所对应的notificationMessage记录下来
    private void updateRemoteNotifications(List<ApolloConfigNotification> deltaNotifications) {
        for (ApolloConfigNotification notification : deltaNotifications) {
            if (Strings.isNullOrEmpty(notification.getNamespaceName())) {
                continue;
            }

            if (notification.getMessages() == null || notification.getMessages().isEmpty()) {
                continue;
            }

            ApolloNotificationMessages localRemoteMessages =
                    m_remoteNotificationMessages.get(notification.getNamespaceName());
            if (localRemoteMessages == null) {
                localRemoteMessages = new ApolloNotificationMessages();
                m_remoteNotificationMessages.put(notification.getNamespaceName(), localRemoteMessages);
            }

            localRemoteMessages.mergeFrom(notification.getMessages());
        }
    }

    private String assembleNamespaces() {
        return STRING_JOINER.join(m_longPollNamespaces.keySet());
    }

    String assembleLongPollRefreshUrl(String uri, String appId, String cluster, String dataCenter,
                                      Map<String, Long> notificationsMap) {
        Map<String, String> queryParams = Maps.newHashMap();
        queryParams.put("appId", queryParamEscaper.escape(appId));
        queryParams.put("cluster", queryParamEscaper.escape(cluster));
        queryParams
                .put("notifications", queryParamEscaper.escape(assembleNotifications(notificationsMap)));

        if (!Strings.isNullOrEmpty(dataCenter)) {
            queryParams.put("dataCenter", queryParamEscaper.escape(dataCenter));
        }
        String localIp = m_configUtil.getLocalIp();
        if (!Strings.isNullOrEmpty(localIp)) {
            queryParams.put("ip", queryParamEscaper.escape(localIp));
        }

        String params = MAP_JOINER.join(queryParams);
        if (!uri.endsWith("/")) {
            uri += "/";
        }

        return uri + "notifications/v2?" + params;
    }

    String assembleNotifications(Map<String, Long> notificationsMap) {
        List<ApolloConfigNotification> notifications = Lists.newArrayList();
        for (Map.Entry<String, Long> entry : notificationsMap.entrySet()) {
            ApolloConfigNotification notification = new ApolloConfigNotification(entry.getKey(), entry.getValue());
            notifications.add(notification);
        }
        return gson.toJson(notifications);
    }

    private List<ServiceDTO> getConfigServices() {
        List<ServiceDTO> services = m_serviceLocator.getConfigServices();
        if (services.size() == 0) {
            throw new ApolloConfigException("No available config service");
        }

        return services;
    }
}

```
