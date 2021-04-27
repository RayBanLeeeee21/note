
## ConfigRepository
com.ctrip.framework.apollo.internals.ConfigRepository
```java
package com.ctrip.framework.apollo.internals;

// import ...

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public interface ConfigRepository {
    
    // 获取config
    public Properties getConfig();

    // 设置fallback后的upstreamRepository
    public void setUpstreamRepository(ConfigRepository upstreamConfigRepository);

    public void addChangeListener(RepositoryChangeListener listener);
    public void removeChangeListener(RepositoryChangeListener listener);

    public ConfigSourceType getSourceType();
}

```

## AbstractConfigRepository

AbstractConfigRepository:
* 功能:
    * 增/删changeListener
    * fireRepositoryChange: 逐个调用listener方法

```java
package com.ctrip.framework.apollo.internals;

// import ...

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public abstract class AbstractConfigRepository implements ConfigRepository {
    private static final Logger logger = LoggerFactory.getLogger(AbstractConfigRepository.class);
    private List<RepositoryChangeListener> m_listeners = Lists.newCopyOnWriteArrayList();

    protected boolean trySync() {
        try {
            sync();
            return true;
        } catch (Throwable ex) {
            Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(ex));
            logger.warn("Sync config failed, will retry. Repository {}, reason: {}", this.getClass(), ExceptionUtil
                            .getDetailMessage(ex));
        }
        return false;
    }

    protected abstract void sync();

    @Override
    public void addChangeListener(RepositoryChangeListener listener) {
        if (!m_listeners.contains(listener)) {
            m_listeners.add(listener);
        }
    }

    @Override
    public void removeChangeListener(RepositoryChangeListener listener) {
        m_listeners.remove(listener);
    }

    protected void fireRepositoryChange(String namespace, Properties newProperties) {
        for (RepositoryChangeListener listener : m_listeners) {
            try {
                listener.onRepositoryChange(namespace, newProperties);
            } catch (Throwable ex) {
                Tracer.logError(ex);
                logger.error("Failed to invoke repository change listener {}", listener.getClass(), ex);
            }
        }
    }
}

```




