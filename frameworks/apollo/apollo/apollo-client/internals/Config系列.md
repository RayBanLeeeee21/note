## AbstractConfig


```java
package com.ctrip.framework.apollo.internals;

// import ...


/**
 * @author Jason Song(song_s@ctrip.com)
 */
public abstract class AbstractConfig implements Config {
    private static final Logger logger = LoggerFactory.getLogger(AbstractConfig.class);

    private static final ExecutorService m_executorService;


    /**
        对config中特定property感兴趣的监听器
    */
    private final List<ConfigChangeListener> m_listeners = Lists.newCopyOnWriteArrayList();

    /**
        监听器 -> 监听器感兴趣的key(property) 
    */
    private final Map<ConfigChangeListener, Set<String>> m_interestedKeys = Maps.newConcurrentMap();

    /**
        监听器 -> 监听器感兴趣的key前缀(property前缀)
    */
    private final Map<ConfigChangeListener, Set<String>> m_interestedKeyPrefixes = Maps.newConcurrentMap();


    private final ConfigUtil m_configUtil;
    private volatile Cache<String, Integer> m_integerCache;
    private volatile Cache<String, Long> m_longCache;
    private volatile Cache<String, Short> m_shortCache;
    private volatile Cache<String, Float> m_floatCache;
    private volatile Cache<String, Double> m_doubleCache;
    private volatile Cache<String, Byte> m_byteCache;
    private volatile Cache<String, Boolean> m_booleanCache;
    private volatile Cache<String, Date> m_dateCache;
    private volatile Cache<String, Long> m_durationCache;
    private final Map<String, Cache<String, String[]>> m_arrayCache;
    private final List<Cache> allCaches;
    private final AtomicLong m_configVersion; //indicate config version

    static {
        m_executorService = Executors.newCachedThreadPool(ApolloThreadFactory
                .create("Config", true));
    }

    public AbstractConfig() {
        m_configUtil = ApolloInjector.getInstance(ConfigUtil.class);
        m_configVersion = new AtomicLong();
        m_arrayCache = Maps.newConcurrentMap();
        allCaches = Lists.newArrayList();
    }

    @Override
    public void addChangeListener(ConfigChangeListener listener) {
        addChangeListener(listener, null);
    }

    @Override
    public void addChangeListener(ConfigChangeListener listener, Set<String> interestedKeys) {
        addChangeListener(listener, interestedKeys, null);
    }

    /**
        记录监听器, 以及监听器感兴趣的key/key前缀
    */
    @Override
    public void addChangeListener(ConfigChangeListener listener, Set<String> interestedKeys, Set<String> interestedKeyPrefixes) {
        if (!m_listeners.contains(listener)) {
            m_listeners.add(listener);
            if (interestedKeys != null && !interestedKeys.isEmpty()) {
                m_interestedKeys.put(listener, Sets.newHashSet(interestedKeys));
            }
            if (interestedKeyPrefixes != null && !interestedKeyPrefixes.isEmpty()) {
                m_interestedKeyPrefixes.put(listener, Sets.newHashSet(interestedKeyPrefixes));
            }
        }
    }

    /**
        删除监听器及其对应的key/key前缀
    */
    @Override
    public boolean removeChangeListener(ConfigChangeListener listener) {
        m_interestedKeys.remove(listener);
        m_interestedKeyPrefixes.remove(listener);
        return m_listeners.remove(listener);
    }
    
    /**

    */
    @Override
    public Integer getIntProperty(String key, Integer defaultValue) {
        try {
            // 初始化cache
            if (m_integerCache == null) {
                synchronized (this) {
                    if (m_integerCache == null) {
                        m_integerCache = newCache();
                    }
                }
            }

            // 1. cache有结果, 则直接返回
            // 2. cache无结果, 则从数据库加载, 并对其应用TO_INT_FUNCTION, 放到cache中
            return getValueFromCache(key, Functions.TO_INT_FUNCTION, m_integerCache, defaultValue);
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getIntProperty for %s failed, return default value %d", key,
                            defaultValue), ex));
        }
        return defaultValue;
    }

    /**
        以下略, 类似于getIntProperty
    */
    public Long getLongProperty(String key, Long defaultValue) {}
    public Short getShortProperty(String key, Short defaultValue) {}
    public Float getFloatProperty(String key, Float defaultValue) {}
    public Double getDoubleProperty(String key, Double defaultValue) {}
    public Byte getByteProperty(String key, Byte defaultValue) {}
    public Boolean getBooleanProperty(String key, Boolean defaultValue) {}



    // ???
    @Override
    public String[] getArrayProperty(String key, final String delimiter, String[] defaultValue) {
        try {
            if (!m_arrayCache.containsKey(delimiter)) {
                synchronized (this) {
                    if (!m_arrayCache.containsKey(delimiter)) {
                        m_arrayCache.put(delimiter, this.<String[]>newCache());
                    }
                }
            }

            Cache<String, String[]> cache = m_arrayCache.get(delimiter);
            String[] result = cache.getIfPresent(key);

            if (result != null) {
                return result;
            }

            return getValueAndStoreToCache(key, new Function<String, String[]>() {
                @Override
                public String[] apply(String input) {
                    return input.split(delimiter);
                }
            }, cache, defaultValue);
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getArrayProperty for %s failed, return default value", key), ex));
        }
        return defaultValue;
    }

    @Override
    public <T extends Enum<T>> T getEnumProperty(String key, Class<T> enumType, T defaultValue) {
        try {
            String value = getProperty(key, null);

            if (value != null) {
                return Enum.valueOf(enumType, value);
            }
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getEnumProperty for %s failed, return default value %s", key,
                            defaultValue), ex));
        }

        return defaultValue;
    }

    /**
        以下略, 类似于getIntProperty
    */
    @Override
    public Date getDateProperty(String key, Date defaultValue);

    /**
        dataCache中保存的是已经格式化过的date, 而这里指定了其它格式, 所以不能从cache取
    */
    @Override
    public Date getDateProperty(String key, String format, Date defaultValue) {
        try {
            String value = getProperty(key, null);

            if (value != null) {
                return Parsers.forDate().parse(value, format);
            }
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getDateProperty for %s failed, return default value %s", key,
                            defaultValue), ex));
        }

        return defaultValue;
    }

    /**
        dataCache中保存的是已经格式化过的date, 而这里指定了其它格式, 所以不能从cache取
    */
    @Override
    public Date getDateProperty(String key, String format, Locale locale, Date defaultValue) {
        try {
            String value = getProperty(key, null);

            if (value != null) {
                return Parsers.forDate().parse(value, format, locale);
            }
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getDateProperty for %s failed, return default value %s", key,
                            defaultValue), ex));
        }

        return defaultValue;
    }

    /**
        以下略, 类似于getIntProperty
    */
    @Override
    public long getDurationProperty(String key, long defaultValue){}

    /**
        从property源获取property, 然后对其应用function后返回
    */
    @Override
    public <T> T getProperty(String key, Function<String, T> function, T defaultValue) {
        try {
            String value = getProperty(key, null);

            if (value != null) {
                return function.apply(value);
            }
        } catch (Throwable ex) {
            Tracer.logError(new ApolloConfigException(
                    String.format("getProperty for %s failed, return default value %s", key,
                            defaultValue), ex));
        }

        return defaultValue;
    }

    /**
        如果cache有就从cache拿, 否则从property源加载, 应用paser转换, 再放到cache中
    */
    private <T> T getValueFromCache(String key, Function<String, T> parser, Cache<String, T> cache, T defaultValue) {
        T result = cache.getIfPresent(key);

        if (result != null) {
            return result;
        }

        return getValueAndStoreToCache(key, parser, cache, defaultValue);
    }

    private <T> T getValueAndStoreToCache(String key, Function<String, T> parser, Cache<String, T> cache, T defaultValue) {
        long currentConfigVersion = m_configVersion.get();
        String value = getProperty(key, null);   // 从property源加载

        if (value != null) {
            T result = parser.apply(value);

            if (result != null) {
                synchronized (this) {
                    
                    // 如果cache版本更新, 则不放到cache先 ???
                    if (m_configVersion.get() == currentConfigVersion) {
                        cache.put(key, result);
                    }
                }
                return result;
            }
        }

        return defaultValue;
    }

    //
    private <T> Cache<String, T> newCache() {
        Cache<String, T> cache = CacheBuilder.newBuilder()
                .maximumSize(m_configUtil.getMaxConfigCacheSize())
                .expireAfterAccess(m_configUtil.getConfigCacheExpireTime(), m_configUtil.getConfigCacheExpireTimeUnit())
                .build();
        allCaches.add(cache);
        return cache;
    }

    /**
     * 清除所有cache
     */
    protected void clearConfigCache() {
        synchronized (this) {
            for (Cache c : allCaches) {
                if (c != null) {
                    c.invalidateAll();
                }
            }
            m_configVersion.incrementAndGet();
        }
    }


    /**
        发生changeEvent时, 筛选出对changeEvent感兴趣的监听器, 依次调用其onChange()方法
    */
    protected void fireConfigChange(final ConfigChangeEvent changeEvent) {
        for (final ConfigChangeListener listener : m_listeners) {
            // check whether the listener is interested in this change event
            if (!isConfigChangeListenerInterested(listener, changeEvent)) {
                continue;
            }
            m_executorService.submit(new Runnable() {
                @Override
                public void run() {
                    String listenerName = listener.getClass().getName();
                    Transaction transaction = Tracer.newTransaction("Apollo.ConfigChangeListener", listenerName);
                    try {
                        listener.onChange(changeEvent);
                        transaction.setStatus(Transaction.SUCCESS);
                    } catch (Throwable ex) {
                        transaction.setStatus(ex);
                        Tracer.logError(ex);
                        logger.error("Failed to invoke config change listener {}", listenerName, ex);
                    } finally {
                        transaction.complete();
                    }
                }
            });
        }
    }

    /**
        如果更新事件中, 有监听器匹配的key/property(完全匹配或者前缀匹配), 则视为监听器对事件感兴趣
    */
    private boolean isConfigChangeListenerInterested(ConfigChangeListener configChangeListener, ConfigChangeEvent configChangeEvent) {
        Set<String> interestedKeys = m_interestedKeys.get(configChangeListener);
        Set<String> interestedKeyPrefixes = m_interestedKeyPrefixes.get(configChangeListener);

        if ((interestedKeys == null || interestedKeys.isEmpty())
                && (interestedKeyPrefixes == null || interestedKeyPrefixes.isEmpty())) {
            return true; // no interested keys means interested in all keys
        }

        if (interestedKeys != null) {
            for (String interestedKey : interestedKeys) {
                if (configChangeEvent.isChanged(interestedKey)) {
                    return true;
                }
            }
        }

        if (interestedKeyPrefixes != null) {
            for (String prefix : interestedKeyPrefixes) {
                for (final String changedKey : configChangeEvent.changedKeys()) {
                    if (changedKey.startsWith(prefix)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
        根据更改先后的properties计算ConfigChange集合(列表)
    */
    List<ConfigChange> calcPropertyChanges(String namespace, Properties previous,
                                           Properties current) {
        if (previous == null) {
            previous = new Properties();
        }

        if (current == null) {
            current = new Properties();
        }

        // 获取更改先后的keys集
        Set<String> previousKeys = previous.stringPropertyNames();
        Set<String> currentKeys = current.stringPropertyNames();

        // 集合运算得出增/公有/改的key
        Set<String> commonKeys = Sets.intersection(previousKeys, currentKeys);
        Set<String> newKeys = Sets.difference(currentKeys, commonKeys);
        Set<String> removedKeys = Sets.difference(previousKeys, commonKeys);

        List<ConfigChange> changes = Lists.newArrayList();

        // 创建ADDED类型的ConfigChange
        for (String newKey : newKeys) {
            changes.add(new ConfigChange(namespace, newKey, null, current.getProperty(newKey),
                    PropertyChangeType.ADDED));
        }

        // 创建DELETED类型的ConfigChange
        for (String removedKey : removedKeys) {
            changes.add(new ConfigChange(namespace, removedKey, previous.getProperty(removedKey), null,
                    PropertyChangeType.DELETED));
        }

        // 从公有的key中找到更改的, 创建MODIFIED类型的ConfigChange
        for (String commonKey : commonKeys) {
            String previousValue = previous.getProperty(commonKey);
            String currentValue = current.getProperty(commonKey);
            if (Objects.equal(previousValue, currentValue)) {
                continue;
            }
            changes.add(new ConfigChange(namespace, commonKey, previousValue, currentValue,
                    PropertyChangeType.MODIFIED));
        }

        return changes;
    }
}


```
## DefaultConfig

```java
package com.ctrip.framework.apollo.internals;

// import ..

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public class DefaultConfig extends AbstractConfig implements RepositoryChangeListener {
    private static final Logger logger = LoggerFactory.getLogger(DefaultConfig.class);
    private final String m_namespace;
    private final Properties m_resourceProperties;  // 从资源文件加载的配置
    private final AtomicReference<Properties> m_configProperties; // 从apollo加载的配置
    private final ConfigRepository m_configRepository; // 负责加载apollo配置的repository
    private final RateLimiter m_warnLogRateLimiter; 

    private volatile ConfigSourceType m_sourceType = ConfigSourceType.NONE;

    /**
     * Constructor.
     *
     * @param namespace        the namespace of this config instance
     * @param configRepository the config repository for this config instance
     */
    public DefaultConfig(String namespace, ConfigRepository configRepository) {
        m_namespace = namespace;
        // 加载resource目录下的properties: 从META-INF/config/${namespace}.properties加载
        m_resourceProperties = loadFromResource(m_namespace); 
        m_configRepository = configRepository;
        m_configProperties = new AtomicReference<>();
        m_warnLogRateLimiter = RateLimiter.create(0.017); // 1 warning log output per minute
        // 尝试利用repository加载config创建给的config 
        initialize();
    }

    // 尝试加载config创建给的config 
    private void initialize() {
        try {
            updateConfig(
                m_configRepository.getConfig(),    // 利用repository加载
                m_configRepository.getSourceType() // 关联repository的类型
            );
        } catch (Throwable ex) {
            Tracer.logError(ex);
            logger.warn("Init Apollo Local Config failed - namespace: {}, reason: {}.",
                    m_namespace, ExceptionUtil.getDetailMessage(ex));
        } finally {
            //register the change listener no matter config repository is working or not
            //so that whenever config repository is recovered, config could get changed
            m_configRepository.addChangeListener(this);
        }
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        // step 1: 从jvm系统参数读取, i.e. -Dkey=value
        String value = System.getProperty(key);

        // step 2: 从config仓库的cache读取
        if (value == null && m_configProperties.get() != null) {
            value = m_configProperties.get().getProperty(key);
        }

        // step 3: 从环境变量读取
        if (value == null) {
            value = System.getenv(key);
        }

        // step 4: 从resources目录的cache读取
        if (value == null && m_resourceProperties != null) {
            value = (String) m_resourceProperties.get(key);
        }

        // 
        if (value == null && m_configProperties.get() == null 
                && m_warnLogRateLimiter.tryAcquire()) {  //  m_warnLogRateLimiter决定要不要发日志
            logger.warn("Could not load config for namespace {} from Apollo, please check whether the configs are released in Apollo! Return default value now!", m_namespace);
        }

        return value == null ? defaultValue : value;
    }

    @Override
    public Set<String> getPropertyNames() {
        Properties properties = m_configProperties.get();
        if (properties == null) {
            return Collections.emptySet();
        }

        return stringPropertyNames(properties);
    }

    @Override
    public ConfigSourceType getSourceType() {
        return m_sourceType;
    }

    private Set<String> stringPropertyNames(Properties properties) {
        //jdk9以下版本Properties#enumerateStringProperties方法存在性能问题，keys() + get(k) 重复迭代, jdk9之后改为entrySet遍历.
        Map<String, String> h = new HashMap<>();
        for (Map.Entry<Object, Object> e : properties.entrySet()) {
            Object k = e.getKey();
            Object v = e.getValue();
            if (k instanceof String && v instanceof String) {
                h.put((String) k, (String) v);
            }
        }
        return h.keySet();
    }

    @Override
    public synchronized void onRepositoryChange(String namespace, Properties newProperties) {
        // 检查配置是否变更, 未变量则返回
        if (newProperties.equals(m_configProperties.get())) {
            return;
        }

        // 计算出新旧Properties的更改集
        ConfigSourceType sourceType = m_configRepository.getSourceType();
        Properties newConfigProperties = new Properties();
        newConfigProperties.putAll(newProperties);
        Map<String, ConfigChange> actualChanges = updateAndCalcConfigChanges(newConfigProperties, sourceType);

        //check double checked result
        if (actualChanges.isEmpty()) {
            return;
        }

        // 执行对于config的listener.onChange()
        this.fireConfigChange(new ConfigChangeEvent(m_namespace, actualChanges));

        Tracer.logEvent("Apollo.Client.ConfigChanges", m_namespace);
    }

    // 替换掉旧的Properties(整个properties的map替换掉)
    private void updateConfig(Properties newConfigProperties, ConfigSourceType sourceType) {
        m_configProperties.set(newConfigProperties);
        m_sourceType = sourceType;
    }

    // ???
    private Map<String, ConfigChange> updateAndCalcConfigChanges(Properties newConfigProperties,
                                                                 ConfigSourceType sourceType) {
        List<ConfigChange> configChanges =
                calcPropertyChanges(m_namespace, m_configProperties.get(), newConfigProperties);

        ImmutableMap.Builder<String, ConfigChange> actualChanges =
                new ImmutableMap.Builder<>();

        /** === Double check since DefaultConfig has multiple config sources ==== **/

        //1. use getProperty to update configChanges's old value???
        for (ConfigChange change : configChanges) {
            change.setOldValue(this.getProperty(change.getPropertyName(), change.getOldValue()));
        }

        //2. 更新m_configProperties (整个property的map替换掉), 清理cache(cache已过期)
        updateConfig(newConfigProperties, sourceType);
        clearConfigCache();

        //3. use getProperty to update configChange's new value and calc the final changes
        for (ConfigChange change : configChanges) {
            change.setNewValue(this.getProperty(change.getPropertyName(), change.getNewValue()));
            switch (change.getChangeType()) {
                case ADDED:
                    if (Objects.equals(change.getOldValue(), change.getNewValue())) {
                        break;
                    }
                    if (change.getOldValue() != null) {
                        change.setChangeType(PropertyChangeType.MODIFIED);
                    }
                    actualChanges.put(change.getPropertyName(), change);
                    break;
                case MODIFIED:
                    if (!Objects.equals(change.getOldValue(), change.getNewValue())) {
                        actualChanges.put(change.getPropertyName(), change);
                    }
                    break;
                case DELETED:
                    if (Objects.equals(change.getOldValue(), change.getNewValue())) {
                        break;
                    }
                    if (change.getNewValue() != null) {
                        change.setChangeType(PropertyChangeType.MODIFIED);
                    }
                    actualChanges.put(change.getPropertyName(), change);
                    break;
                default:
                    //do nothing
                    break;
            }
        }
        return actualChanges.build();
    }

    private Properties loadFromResource(String namespace) {
        String name = String.format("META-INF/config/%s.properties", namespace);
        InputStream in = ClassLoaderUtil.getLoader().getResourceAsStream(name);
        Properties properties = null;

        if (in != null) {
            properties = new Properties();

            try {
                properties.load(in);
            } catch (IOException ex) {
                Tracer.logError(ex);
                logger.error("Load resource config for namespace {} failed", namespace, ex);
            } finally {
                try {
                    in.close();
                } catch (IOException ex) {
                    // ignore
                }
            }
        }

        return properties;
    }
}

```