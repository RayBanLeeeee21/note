# DatabaseMessageSender与ReleaseMessageScanner

备注: 关于DatabaseMessageSender与ReleaseMessageScanner在整个apollo中的作用, 仍待继续研究.

* package: ```package com.ctrip.framework.apollo.biz.message```
* 作用: 利用数据库实现消息(```ReleaseMessage```)的异步发送. 
    * ReleaseMessageScanner负责定时扫描数据库中新添加的消息, 并用注册的消息监听器进行处理. 
    * DatabaseMessageSender负责发送消息到数据库中, 并负责实时清理数据库中过期的消息
* 使用场景: 
    1. 调用```ReleaseMessageScanner.addMessageListener(listener)```注册消息监听器, 对消息进行处理.
    2. 调用```DatabaseMessageSender.sendMessage(message, channel)```发送消息.

## 1. DatabaseMessageSender

### 1.1 特性
* implements:
    ```java
    public interface MessageSender {
        void sendMessage(String message, String channel);
    }
    ```
* 作用: 
    * 发送消息: 将消息保存到数据库. 发送成功的消息, 同时会被加入到**待清理消息集合([BlockingQueue](https://blog.csdn.net/qq_42135428/article/details/80285737))**.
        * **Attention**: 被加入待清理消息集合(BlockQueue)的消息在数据库不会被清理掉, 只有小于其ID的消息会被清理. 对于特定的一个namespace (以**appId+cluster+namespace**为标识), 数据库中只保留其最近的一个消息 (```ReleaseMessage```), 其余旧的消息会被清理掉.
            * e.g. 
                1. {id:2, appId:a1, cluster:c1, namespace1:n1}被发送, 则id<2的消息被清理, 保留id=2.
                2. {id:3, appId:a1, cluster:c1, namespace1:n1}被发送, 则id=2的消息被清理, 保留id=3.
                3. {id:4, appId:a2, cluster:c1, namespace1:n1}被发送, 则数据库中剩下id=3与id=4.
    * 定时清理消息: 在Bean构造完成后, 开启清理线程, 定期(1秒)检查待清理消息集合(BlockingQueue)是否有消息, 有则删除早于该消息的所有消息.



### 1.2 源码解读

```java
/**
 * @author Jason Song(song_s@ctrip.com)
 */
@Component
public class DatabaseMessageSender implements MessageSender {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseMessageSender.class);
    private static final int CLEAN_QUEUE_MAX_SIZE = 100;

    /**
        待清理消息集合(BlockingQueue), 保存了待清理消息(只保存ID).  
    */
    private BlockingQueue<Long> toClean = Queues.newLinkedBlockingQueue(CLEAN_QUEUE_MAX_SIZE);

    /**
        用于定期执行消息的清理工作.
        * 单线程池.
    */
    private final ExecutorService cleanExecutorService;

    /**
        供线程池cleanExecutorService的工作线程判断是否应该停止工作的flag.
        * 调用stopClean()可以使清理工作停止.
    */
    private final AtomicBoolean cleanStopped;

    private final ReleaseMessageRepository releaseMessageRepository;

    public DatabaseMessageSender(final ReleaseMessageRepository releaseMessageRepository) {
        // 单线程.
        // 并设置成daemon线程, 这样就不用显式关闭线程.
        cleanExecutorService = Executors.newSingleThreadExecutor(ApolloThreadFactory.create("DatabaseMessageSender", true));
        
        cleanStopped = new AtomicBoolean(false);
        this.releaseMessageRepository = releaseMessageRepository;
    }

    /**
        发送消息到指定channel.
    */
    @Override
    @Transactional
    public void sendMessage(String message, String channel) {

        // 判断是否支持channel.
        logger.info("Sending message {} to channel {}", message, channel);
        if (!Objects.equals(channel, Topics.APOLLO_RELEASE_TOPIC)) {
            logger.warn("Channel {} not supported by DatabaseMessageSender!");
            return;
        }

        Tracer.logEvent("Apollo.AdminService.ReleaseMessage", message);
        Transaction transaction = Tracer.newTransaction("Apollo.AdminService", "sendMessage");
        try {

            // 保存消息到数据库.
            ReleaseMessage newMessage = releaseMessageRepository.save(new ReleaseMessage(message));

            toClean.offer(newMessage.getId()); // 将消息加入待清理消息集合(BlockingQueue)
            transaction.setStatus(Transaction.SUCCESS);
        } catch (Throwable ex) {
            logger.error("Sending message to database failed", ex);
            transaction.setStatus(ex);
            throw ex;
        } finally {
            transaction.complete();
        }
    }

    /**
        启动清理线程, 循环清理
        * @PostConstruct表示该方法在Bean被构造以后被框架调用.
        * cleanStopped被置位或线程被中断时停止.
    */
    @PostConstruct
    private void initialize() {
        // 向cleanExecutorService指定线程的内容.
        cleanExecutorService.submit(() -> {
            // 循环等待, 清理队列toClean非空则清理.
            while (!cleanStopped.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    Long rm = toClean.poll(1, TimeUnit.SECONDS); // 线程阻塞(TIMED_WAITING)等待, 直到满1秒或者取到消息
                    if (rm != null) {
                        cleanMessage(rm);                        // 清理id<rm的所有消息.
                    } else {
                        TimeUnit.SECONDS.sleep(5);
                    }
                } catch (Throwable ex) {
                    Tracer.logError(ex);
                }
            }
        });
    }

    /**
        清理id小于给定值的消息, 每次请求数据库删除100条, 直到清理完.
    */
    private void cleanMessage(Long id) {
        boolean hasMore = true;
        //double check in case the release message is rolled back
        ReleaseMessage releaseMessage = releaseMessageRepository.findById(id).orElse(null);
        if (releaseMessage == null) {
            return;
        }
        while (hasMore && !Thread.currentThread().isInterrupted()) {
            List<ReleaseMessage> messages = releaseMessageRepository.findFirst100ByMessageAndIdLessThanOrderByIdAsc(
                    releaseMessage.getMessage(), releaseMessage.getId());

            releaseMessageRepository.deleteAll(messages);
            hasMore = messages.size() == 100;

            messages.forEach(toRemove -> Tracer.logEvent(
                    String.format("ReleaseMessage.Clean.%s", toRemove.getMessage()), String.valueOf(toRemove.getId())));
        }
    }

    /**
        置位cleanStopped, 使消息清理线程停止.
    */
    void stopClean() {
        cleanStopped.set(true);
    }
}

```

## 2. ReleaseMessageScanner

### 2.1 特性
* implements:
    ```java
    public interface InitializingBean {

	/**
	 * Invoked by the containing {@code BeanFactory} after it has set all bean properties
	 * and satisfied {@link BeanFactoryAware}, {@code ApplicationContextAware} etc.
	 * <p>This method allows the bean instance to perform validation of its overall
	 * configuration and final initialization when all bean properties have been set.
	 * @throws Exception in the event of misconfiguration (such as failure to set an
	 * essential property) or if initialization fails for any other reason
	 */
	void afterPropertiesSet() throws Exception;
    }
    ```
* 作用: 
    * 监听器注册: 该服务维护一个监听器集合(CopyOnWriteArrayList)其它服务可以调用```addMessageListener(listener)```方法来将消息监听器注册到该服务.
        * *[CopyOnWriteArrayList](https://blog.csdn.net/linsongbin1/article/details/54581787): 一种线程安全的List, 写时将整个表复制一遍. 在复制过程中, 读线程则读旧表. 写完后, 引用指向新的表. 适合于**多读少写**的场景*.
    * 扫描消息: 开启一个单线程池, 定时对数据库中保存的消息进行扫描, 并针对每条消息, 顺序执行监听器的```handleMessage(message, channel)```方法, 进行一些处理.
        * 消息监听器接口:
            ```java
            public interface ReleaseMessageListener {
                void handleMessage(ReleaseMessage message, String channel);
            }
            ```


### 2.2 源码解读

```java
/**
 * @author Jason Song(song_s@ctrip.com)
 */
public class ReleaseMessageScanner implements InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(ReleaseMessageScanner.class);

    @Autowired
    private BizConfig bizConfig;

    @Autowired
    private ReleaseMessageRepository releaseMessageRepository;

    /**
        扫描数据库的时间间隔.
    */
    private int databaseScanInterval;


    /**
        监听器集合(CopyOnWriteArrayList)
    */
    private List<ReleaseMessageListener> listeners;

    /** 
        定时执行监听器handleMessage(message, channel)方法的线程池
        * 单线程
    */
    private ScheduledExecutorService executorService;

    /**
        已扫描过的最大的消息ID
    */
    private long maxIdScanned;

    public ReleaseMessageScanner() {
        listeners = Lists.newCopyOnWriteArrayList();
        // 单线程
        executorService = Executors.newScheduledThreadPool(1, ApolloThreadFactory
                .create("ReleaseMessageScanner", true));
    }

    /**
        启动executorService, 定期循环执行监听器方法.
        * Bean被注入属性后调用该方法.
    */
    @Override
    public void afterPropertiesSet() throws Exception {
        databaseScanInterval = bizConfig.releaseMessageScanIntervalInMilli();

        // 加载上次扫描过的最大的消息ID, 从该消息的下一条开始扫描.
        // ??? 如果在服务实例未启动的时候, 有新的消息被添加到数据库, 会不会错过?
        maxIdScanned = loadLargestMessageId();

        // 指定该线程池的工作线程内容.
        executorService.scheduleWithFixedDelay((Runnable) () -> {
            Transaction transaction = Tracer.newTransaction("Apollo.ReleaseMessageScanner", "scanMessage");
            try {
                // 扫描信息.
                scanMessages();
                transaction.setStatus(Transaction.SUCCESS);
            } catch (Throwable ex) {
                transaction.setStatus(ex);
                logger.error("Scan and send message failed", ex);
            } finally {
                transaction.complete();
            }
        }, databaseScanInterval, databaseScanInterval, TimeUnit.MILLISECONDS);

    }

    /**
     * add message listeners for release message.
     * 其它service可以调用该方法注册监听器, 放到监听器集合(CopyOnWriteArrayList)
     * @param listener
     */
    public void addMessageListener(ReleaseMessageListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Scan messages, continue scanning until there is no more messages.
     * 分批扫描数据库中信息(一批500条), 直到完成.
     */
    private void scanMessages() {
        boolean hasMoreMessages = true;
        while (hasMoreMessages && !Thread.currentThread().isInterrupted()) {
            hasMoreMessages = scanAndSendMessages();
        }
    }

    /**
     * scan messages and send.
     * 扫描数据库并发送消息, 每次取得id>maxIdScanned的前500条消息, 并对其中每条消息执行监听器的handleMessage(message, channel)方法.
     * 
     * @return whether there are more messages 是否还有更多数据.
     */
    private boolean scanAndSendMessages() {
        //current batch is 500.
        List<ReleaseMessage> releaseMessages =
                releaseMessageRepository.findFirst500ByIdGreaterThanOrderByIdAsc(maxIdScanned);
        if (CollectionUtils.isEmpty(releaseMessages)) {
            return false;
        }

        // 针对本批次的每条消息, 依次执行监听器的handleMessage(message, channel)方法 
        fireMessageScanned(releaseMessages);

        // 判断剩余
        int messageScanned = releaseMessages.size();
        maxIdScanned = releaseMessages.get(messageScanned - 1).getId();
        return messageScanned == 500;
    }

    /**
     * find largest message id as the current start point.
     * @return current largest message id.
     */
    private long loadLargestMessageId() {
        ReleaseMessage releaseMessage = releaseMessageRepository.findTopByOrderByIdDesc();
        return releaseMessage == null ? 0 : releaseMessage.getId();
    }

    /**
     * 针对批次中的每条消息, 依次执行监听器的handleMessage(message, channel)方法.
     * @param messages
     */
    private void fireMessageScanned(List<ReleaseMessage> messages) {
        for (ReleaseMessage message : messages) {
            for (ReleaseMessageListener listener : listeners) {
                try {
                    listener.handleMessage(message, Topics.APOLLO_RELEASE_TOPIC);
                } catch (Throwable ex) {
                    Tracer.logError(ex);
                    logger.error("Failed to invoke message listener {}", listener.getClass(), ex);
                }
            }
        }
    }
}
```