## localfileconfigrepository

功能:
* 提供config:
    1. 如果内存中没有config, 则先从upstream(远程)或本地cache同步config到内存
    2. 返回内存中的config
* 同步config到内存   
    1. 该repository有个upstream(默认为RemoteConfigRepository), 首先会从upstream中远程获取config
        * 如果从upstream获取配置成功, 则除了把config放到内存以外, 还要持久化到本地cache文件中
    2. 如果无法从upstream中远程获取, 则只能从本地cache中加载
* 实时同步: 该repository实现了RepositoryChangeListener, 在upstream的config发生改变时, 会自动调用onRepositoryChange()方法同步config

```java
package com.ctrip.framework.apollo.internals;

// import ...

/**
 * @author Jason Song(song_s@ctrip.com)
 */
public class LocalFileConfigRepository extends AbstractConfigRepository
        implements RepositoryChangeListener {
    private static final Logger logger = LoggerFactory.getLogger(LocalFileConfigRepository.class);
    private static final String CONFIG_DIR = "/config-cache";
    private final String m_namespace;
    private File m_baseDir;
    private final ConfigUtil m_configUtil;
    private volatile Properties m_fileProperties;
    private volatile ConfigRepository m_upstream;

    private volatile ConfigSourceType m_sourceType = ConfigSourceType.LOCAL;

    /**
     * Constructor.
     *
     * @param namespace the namespace
     */
    public LocalFileConfigRepository(String namespace) {
        this(namespace, null);
    }

    public LocalFileConfigRepository(String namespace, ConfigRepository upstream) {
        m_namespace = namespace;
        m_configUtil = ApolloInjector.getInstance(ConfigUtil.class);
        this.setLocalCacheDir(findLocalCacheDir(), false);
        this.setUpstreamRepository(upstream);
        this.trySync();
    }

    // 设置新目录, 并同步到新的cache file下(可选)
    void setLocalCacheDir(File baseDir, boolean syncImmediately) {
        m_baseDir = baseDir;
        this.checkLocalConfigCacheDir(m_baseDir);
        if (syncImmediately) {
            this.trySync();
        }
    }

    // 检查cache目录是否存在
    private File findLocalCacheDir() {
        try {
            String defaultCacheDir = m_configUtil.getDefaultLocalCacheDir();
            Path path = Paths.get(defaultCacheDir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            if (Files.exists(path) && Files.isWritable(path)) {
                return new File(defaultCacheDir, CONFIG_DIR);
            }
        } catch (Throwable ex) {
            //ignore
        }

        return new File(ClassLoaderUtil.getClassPath(), CONFIG_DIR);
    }

    // 提供config
    @Override
    public Properties getConfig() {

        // 1. 先同步: 
        // * 先从upstream获取
        // * 若失败, 再从cache file中加载
        if (m_fileProperties == null) {
            sync();
        }

        // 2. 返回结果
        Properties result = new Properties();
        result.putAll(m_fileProperties);
        return result;
    }

    // 注册新的upstream  
    @Override
    public void setUpstreamRepository(ConfigRepository upstreamConfigRepository) {
        if (upstreamConfigRepository == null) {
            return;
        }
        // 先向原有的upstream注销
        if (m_upstream != null) {
            m_upstream.removeChangeListener(this);
        }

        // 绑定新的upstream
        m_upstream = upstreamConfigRepository;

        // 从新的upstream同步(覆盖现在repository中已有的properties, 并且覆盖cache file)
        trySyncFromUpstream();
        upstreamConfigRepository.addChangeListener(this);
    }

    @Override
    public ConfigSourceType getSourceType() {
        return m_sourceType;
    }

    // 监听到upstream的变化时, 用从upstream换取的newproperties替换掉repository中原有的
    @Override
    public void onRepositoryChange(String namespace, Properties newProperties) {
        if (newProperties.equals(m_fileProperties)) {
            return;
        }
        Properties newFileProperties = new Properties();
        newFileProperties.putAll(newProperties);
        updateFileProperties(newFileProperties, m_upstream.getSourceType());
        this.fireRepositoryChange(namespace, newProperties);  // 通知监听自己的listener
    }

    // 同步
    @Override
    protected void sync() {
        // 1. 先尝试从upstream中加载(同步)
        boolean syncFromUpstreamResultSuccess = trySyncFromUpstream();

        if (syncFromUpstreamResultSuccess) {
            return;
        }

        // 2. 第一步失败, 则再尝试从cache file中加载
        Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "syncLocalConfig");
        Throwable exception = null;
        try {
            transaction.addData("Basedir", m_baseDir.getAbsolutePath());
            m_fileProperties = this.loadFromLocalCacheFile(m_baseDir, m_namespace);
            m_sourceType = ConfigSourceType.LOCAL;
            transaction.setStatus(Transaction.SUCCESS);
        } catch (Throwable ex) {
            Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(ex));
            transaction.setStatus(ex);
            exception = ex;
            //ignore
        } finally {
            transaction.complete();
        }

        if (m_fileProperties == null) {
            m_sourceType = ConfigSourceType.NONE;
            throw new ApolloConfigException(
                    "Load config from local config failed!", exception);
        }
    }

    // 从upstream repositroy中同步(加载)
    private boolean trySyncFromUpstream() {
        if (m_upstream == null) {
            return false;
        }
        try {
            updateFileProperties(m_upstream.getConfig(), m_upstream.getSourceType());
            return true;
        } catch (Throwable ex) {
            Tracer.logError(ex);
            logger
                    .warn("Sync config from upstream repository {} failed, reason: {}", m_upstream.getClass(),
                            ExceptionUtil.getDetailMessage(ex));
        }
        return false;
    }

    // 用参数中newProperties替换repository中原有的properties, 并持久化到文件中
    private synchronized void updateFileProperties(Properties newProperties, ConfigSourceType sourceType) {
        this.m_sourceType = sourceType;
        if (newProperties.equals(m_fileProperties)) {
            return;
        }
        this.m_fileProperties = newProperties;
        persistLocalCacheFile(m_baseDir, m_namespace);
    }

    // 从文件 basedir/namespace 下加载properties
    private Properties loadFromLocalCacheFile(File baseDir, String namespace) throws IOException {
        Preconditions.checkNotNull(baseDir, "Basedir cannot be null");

        File file = assembleLocalCacheFile(baseDir, namespace);
        Properties properties = null;

        if (file.isFile() && file.canRead()) {
            InputStream in = null;

            try {
                in = new FileInputStream(file);

                properties = new Properties();
                properties.load(in);
                logger.debug("Loading local config file {} successfully!", file.getAbsolutePath());
            } catch (IOException ex) {
                Tracer.logError(ex);
                throw new ApolloConfigException(String
                        .format("Loading config from local cache file %s failed", file.getAbsolutePath()), ex);
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException ex) {
                    // ignore
                }
            }
        } else {
            throw new ApolloConfigException(
                    String.format("Cannot read from local cache file %s", file.getAbsolutePath()));
        }

        return properties;
    }

    // 将当时properties持久化到目录 basedir/namespace下
    void persistLocalCacheFile(File baseDir, String namespace) {
        if (baseDir == null) {
            return;
        }
        File file = assembleLocalCacheFile(baseDir, namespace);

        OutputStream out = null;

        Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "persistLocalConfigFile");
        transaction.addData("LocalConfigFile", file.getAbsolutePath());
        try {
            out = new FileOutputStream(file);
            m_fileProperties.store(out, "Persisted by DefaultConfig");
            transaction.setStatus(Transaction.SUCCESS);
        } catch (IOException ex) {
            ApolloConfigException exception =
                    new ApolloConfigException(
                            String.format("Persist local cache file %s failed", file.getAbsolutePath()), ex);
            Tracer.logError(exception);
            transaction.setStatus(exception);
            logger.warn("Persist local cache file {} failed, reason: {}.", file.getAbsolutePath(),
                    ExceptionUtil.getDetailMessage(ex));
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ex) {
                    //ignore
                }
            }
            transaction.complete();
        }
    }

    // 检查cache目录是否存在, 不存在则创建
    private void checkLocalConfigCacheDir(File baseDir) {
        if (baseDir.exists()) {
            return;
        }
        Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "createLocalConfigDir");
        transaction.addData("BaseDir", baseDir.getAbsolutePath());
        try {
            Files.createDirectory(baseDir.toPath());
            transaction.setStatus(Transaction.SUCCESS);
        } catch (IOException ex) {
            ApolloConfigException exception =
                    new ApolloConfigException(
                            String.format("Create local config directory %s failed", baseDir.getAbsolutePath()),
                            ex);
            Tracer.logError(exception);
            transaction.setStatus(exception);
            logger.warn(
                    "Unable to create local config cache directory {}, reason: {}. Will not able to cache config file.",
                    baseDir.getAbsolutePath(), ExceptionUtil.getDetailMessage(ex));
        } finally {
            transaction.complete();
        }
    }

    // 合成针对namespace的文件路径
    File assembleLocalCacheFile(File baseDir, String namespace) {
        String fileName =
                String.format("%s.properties", Joiner.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR)
                        .join(m_configUtil.getAppId(), m_configUtil.getCluster(), namespace));
        return new File(baseDir, fileName);
    }
}

```