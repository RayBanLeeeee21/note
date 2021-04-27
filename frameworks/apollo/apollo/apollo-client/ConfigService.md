ConfigService / ConfigManager / ConfigFactoryManager / ConfigRegistry / DefaultConfigFactory关系
* ConfigService: 
    * 用户直接调用该类的getConfig()/getConfigFile()方法
    * 注册 namespace -> factory 到ConfigRegistry
* ConfigManager:
    * 根据namespace提供Config / ConfigFile
    * 根据namespace, 选择ConfigFactory创建config
* ConfigFactoryManager:
    * 根据namespace提供ConfigFactory
        1. 从registry
        2. 从自带cache
        3. 从ApolloInjector获取指定namespace的factory
        4. 从ApolloInjector获取默认factory
* ConfigRegistry: 注册 namespace -> factory
    * 根据namespace提供ConfigFactory
* DefaultConfigFactory:
    * **创建Config**, 并关联其repository (local等)


```java

package com.ctrip.framework.apollo;

// import ...

/**
 * Entry point for client config use
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ConfigService {
    private static final ConfigService s_instance = new ConfigService();

    private volatile ConfigManager m_configManager;  // 关联的ConfigManager
    private volatile ConfigRegistry m_configRegistry; // 关联的ConfigRegistry

    private ConfigManager getManager() {
        if (m_configManager == null) {
            synchronized (this) {
                if (m_configManager == null) {
                    m_configManager = ApolloInjector.getInstance(ConfigManager.class);
                }
            }
        }

        return m_configManager;
    }

    private ConfigRegistry getRegistry() {
        if (m_configRegistry == null) {
            synchronized (this) {
                if (m_configRegistry == null) {
                    m_configRegistry = ApolloInjector.getInstance(ConfigRegistry.class);
                }
            }
        }

        return m_configRegistry;
    }

    /**
     * Get Application's config instance.
     *
     * @return config instance
     */
    public static Config getAppConfig() {
        return getConfig(ConfigConsts.NAMESPACE_APPLICATION);
    }

    /**
     * 从ConfigManager获取, 然后从factory获取Config
     */
    public static Config getConfig(String namespace) {
        return s_instance.getManager().getConfig(namespace);
    }

    /**
     * 从ConfigManager获取factory, 然后从factory获取ConfigFile
     */
    public static ConfigFile getConfigFile(String namespace, ConfigFileFormat configFileFormat) {
        return s_instance.getManager().getConfigFile(namespace, configFileFormat);
    }

    static void setConfig(Config config) {
        setConfig(ConfigConsts.NAMESPACE_APPLICATION, config);
    }

    /**
     * Manually set the config for the namespace specified, use with caution.
     */
    static void setConfig(String namespace, final Config config) {
        s_instance.getRegistry().register(namespace, new ConfigFactory() {
            @Override
            public Config create(String namespace) {
                return config;
            }

            @Override
            public ConfigFile createConfigFile(String namespace, ConfigFileFormat configFileFormat) {
                return null;
            }

        });
    }

    /**
    *   注册application对应的factory
    */
    static void setConfigFactory(ConfigFactory factory) {
        setConfigFactory(ConfigConsts.NAMESPACE_APPLICATION, factory);
    }

    /**
     * 注册 namespace -> factory 到ConfigRegistory
     */
    static void setConfigFactory(String namespace, ConfigFactory factory) {
        s_instance.getRegistry().register(namespace, factory);
    }

    // for test only
    static void reset() {
        synchronized (s_instance) {
            s_instance.m_configManager = null;
            s_instance.m_configRegistry = null;
        }
    }
}

```

```java
package com.ctrip.framework.apollo.internals;

import java.util.Map;

// import ...

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public class DefaultConfigManager implements ConfigManager {
    private ConfigFactoryManager m_factoryManager;

    private Map<String, Config> m_configs = Maps.newConcurrentMap();
    private Map<String, ConfigFile> m_configFiles = Maps.newConcurrentMap();

    public DefaultConfigManager() {
        m_factoryManager = ApolloInjector.getInstance(ConfigFactoryManager.class);
    }

    /**
     * 从FactoryManager获取factory, 然后用factory创建config
     */
    @Override
    public Config getConfig(String namespace) {
        Config config = m_configs.get(namespace);

        if (config == null) {
            synchronized (this) {
                config = m_configs.get(namespace);

                if (config == null) {
                    ConfigFactory factory = m_factoryManager.getFactory(namespace);

                    config = factory.create(namespace);
                    m_configs.put(namespace, config);
                }
            }
        }

        return config;
    }

    /**
     * 从FactoryManager获取factory, 然后用factory创建configFile
     */
    @Override
    public ConfigFile getConfigFile(String namespace, ConfigFileFormat configFileFormat) {
        String namespaceFileName = String.format("%s.%s", namespace, configFileFormat.getValue());
        ConfigFile configFile = m_configFiles.get(namespaceFileName);

        if (configFile == null) {
            synchronized (this) {
                configFile = m_configFiles.get(namespaceFileName);

                if (configFile == null) {
                    ConfigFactory factory = m_factoryManager.getFactory(namespaceFileName);

                    configFile = factory.createConfigFile(namespaceFileName, configFileFormat);
                    m_configFiles.put(namespaceFileName, configFile);
                }
            }
        }

        return configFile;
    }
}
```

```java
package com.ctrip.framework.apollo.spi;

// import ...

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public class DefaultConfigFactoryManager implements ConfigFactoryManager {
    private ConfigRegistry m_registry;

    private Map<String, ConfigFactory> m_factories = Maps.newConcurrentMap();

    public DefaultConfigFactoryManager() {
        m_registry = ApolloInjector.getInstance(ConfigRegistry.class);
    }

    @Override
    public ConfigFactory getFactory(String namespace) {
        // step 1: check hacked factory
        ConfigFactory factory = m_registry.getFactory(namespace);

        if (factory != null) {
            return factory;
        }

        // step 2: check cache
        factory = m_factories.get(namespace);

        if (factory != null) {
            return factory;
        }

        // step 3: check declared config factory
        factory = ApolloInjector.getInstance(ConfigFactory.class, namespace);

        if (factory != null) {
            return factory;
        }

        // step 4: check default config factory
        factory = ApolloInjector.getInstance(ConfigFactory.class);

        m_factories.put(namespace, factory);

        // factory should not be null
        return factory;
    }
}
```

```java
package com.ctrip.framework.apollo.spi;

// import ... 

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public class DefaultConfigRegistry implements ConfigRegistry {
    private static final Logger s_logger = LoggerFactory.getLogger(DefaultConfigRegistry.class);
    private Map<String, ConfigFactory> m_instances = Maps.newConcurrentMap();

    @Override
    public void register(String namespace, ConfigFactory factory) {
        if (m_instances.containsKey(namespace)) {
            s_logger.warn("ConfigFactory({}) is overridden by {}!", namespace, factory.getClass());
        }

        m_instances.put(namespace, factory);
    }

    @Override
    public ConfigFactory getFactory(String namespace) {
        ConfigFactory config = m_instances.get(namespace);

        return config;
    }
}

```

```java
package com.ctrip.framework.apollo.spi;

// import ...

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public class DefaultConfigFactory implements ConfigFactory {
    private static final Logger logger = LoggerFactory.getLogger(DefaultConfigFactory.class);
    private ConfigUtil m_configUtil;

    public DefaultConfigFactory() {
        m_configUtil = ApolloInjector.getInstance(ConfigUtil.class);
    }

    @Override
    public Config create(String namespace) {
        ConfigFileFormat format = determineFileFormat(namespace);
        if (ConfigFileFormat.isPropertiesCompatible(format)) {
            return new DefaultConfig(namespace, createPropertiesCompatibleFileConfigRepository(namespace, format));
        }
        return new DefaultConfig(namespace, createLocalConfigRepository(namespace));
    }

    @Override
    public ConfigFile createConfigFile(String namespace, ConfigFileFormat configFileFormat) {
        ConfigRepository configRepository = createLocalConfigRepository(namespace);
        switch (configFileFormat) {
            case Properties:
                return new PropertiesConfigFile(namespace, configRepository);
            case XML:
                return new XmlConfigFile(namespace, configRepository);
            case JSON:
                return new JsonConfigFile(namespace, configRepository);
            case YAML:
                return new YamlConfigFile(namespace, configRepository);
            case YML:
                return new YmlConfigFile(namespace, configRepository);
            case TXT:
                return new TxtConfigFile(namespace, configRepository);
        }

        return null;
    }

    LocalFileConfigRepository createLocalConfigRepository(String namespace) {
        if (m_configUtil.isInLocalMode()) {
            logger.warn(
                    "==== Apollo is in local mode! Won't pull configs from remote server for namespace {} ! ====",
                    namespace);
            return new LocalFileConfigRepository(namespace);
        }
        return new LocalFileConfigRepository(namespace, createRemoteConfigRepository(namespace));
    }

    RemoteConfigRepository createRemoteConfigRepository(String namespace) {
        return new RemoteConfigRepository(namespace);
    }

    PropertiesCompatibleFileConfigRepository createPropertiesCompatibleFileConfigRepository(String namespace,
                                                                                            ConfigFileFormat format) {
        String actualNamespaceName = trimNamespaceFormat(namespace, format);
        PropertiesCompatibleConfigFile configFile = (PropertiesCompatibleConfigFile) ConfigService
                .getConfigFile(actualNamespaceName, format);

        return new PropertiesCompatibleFileConfigRepository(configFile);
    }

    // for namespaces whose format are not properties, the file extension must be present, e.g. application.yaml
    ConfigFileFormat determineFileFormat(String namespaceName) {
        String lowerCase = namespaceName.toLowerCase();
        for (ConfigFileFormat format : ConfigFileFormat.values()) {
            if (lowerCase.endsWith("." + format.getValue())) {
                return format;
            }
        }

        return ConfigFileFormat.Properties;
    }

    String trimNamespaceFormat(String namespaceName, ConfigFileFormat format) {
        String extension = "." + format.getValue();
        if (!namespaceName.toLowerCase().endsWith(extension)) {
            return namespaceName;
        }

        return namespaceName.substring(0, namespaceName.length() - extension.length());
    }

}

```


