
ServerCron:

- 时间相关
    - 更新watchDog
    - 更新缓存时间
    - 计算LRU
- 内存相关
    - 计算峰值内存大小
    - 计算常驻内存集大小
- 检查有没收到shutdown
- 信息打印
- clientsCron
    - 检查客户端是否超时
    - 检查是否要重分配输入缓冲区
- databasesCron
    - 定期清理过期键
    - 检查resize
- 持久化相关检查
    - 有后台保存任务: 检查是否完成, 完成则进行RDB替换/AOF替换
    - 无后台保存任务:   
        - 检查是否有被阻塞的BGREWRITEAOF要执行
        - 检查是否要达到了RDB或AOF的同步条件, 达到则要执行
- 


```cpp
/* This is our timer interrupt, called server.hz times per second.
 * Here is where we do a number of things that need to be done asynchronously.
 * For instance:
 *
 * - Active expired keys collection (it is also performed in a lazy way on
 *   lookup).
 * - Software watchdog.
 * - Update some statistic.
 * - Incremental rehashing of the DBs hash tables.
 * - Triggering BGSAVE / AOF rewrite, and handling of terminated children.
 * - Clients timeout of different kinds.
 * - Replication reconnection.
 * - Many more...
 *
 * Everything directly called here will be called server.hz times per second,
 * so in order to throttle execution of things we want to do less frequently
 * a macro is used: run_with_period(milliseconds) { .... }
 */

int serverCron(struct aeEventLoop *eventLoop, long long id, void *clientData) {
    int j;
    UNUSED(eventLoop);
    UNUSED(id);
    UNUSED(clientData);

    // 通过SIGALRM实现一个软件级别的watchdog, 如果超时就在SAGALRM处理器中打日志
    // 参考debug.c的"Software Watchdog"
    if (server.watchdog_period) watchdogScheduleSignal(server.watchdog_period);

    // 更新缓存时间
    updateCachedTime();

    run_with_period(100) {
        trackInstantaneousMetric(STATS_METRIC_COMMAND,server.stat_numcommands);
        trackInstantaneousMetric(STATS_METRIC_NET_INPUT,
                server.stat_net_input_bytes);
        trackInstantaneousMetric(STATS_METRIC_NET_OUTPUT,
                server.stat_net_output_bytes);
    }

    // 用于计算对象的LRU
    server.lruclock = getLRUClock();

    // 计算峰值内存
    if (zmalloc_used_memory() > server.stat_peak_memory)
        server.stat_peak_memory = zmalloc_used_memory();

    // 计算常驻内存集(RSS)
    server.resident_set_size = zmalloc_get_rss();

    // 以安全的方式shutdown
    if (server.shutdown_asap) {
        if (prepareForShutdown(SHUTDOWN_NOFLAGS) == C_OK) exit(0);
        serverLog(LL_WARNING,"SIGTERM received but errors trying to shut down the server, check the logs for more information");
        server.shutdown_asap = 0;
    }

    // 5秒打印一次非空的数据库
    run_with_period(5000) {
        for (j = 0; j < server.dbnum; j++) {
            long long size, used, vkeys;

            size = dictSlots(server.db[j].dict);
            used = dictSize(server.db[j].dict);
            vkeys = dictSize(server.db[j].expires);
            if (used || vkeys) {
                serverLog(LL_VERBOSE,"DB %d: %lld keys (%lld volatile) in %lld slots HT.",j,used,vkeys,size);
                /* dictPrintStats(server.dict); */
            }
        }
    }

    // 5000秒打印一次客户端和slave的数量
    if (!server.sentinel_mode) {
        run_with_period(5000) {
            serverLog(LL_VERBOSE,
                "%lu clients connected (%lu slaves), %zu bytes in use",
                listLength(server.clients)-listLength(server.slaves),
                listLength(server.slaves),
                zmalloc_used_memory());
        }
    }

    // 客户端相关定时任务
    clientsCron();

    // 数据库相关定时任务
    databasesCron();

    // 检查是否有被BGSAVE阻塞而未执行的BGREWRITEAOF, 有的话执行掉
    if (server.rdb_child_pid == -1 && server.aof_child_pid == -1 &&
        server.aof_rewrite_scheduled)
    {
        rewriteAppendOnlyFileBackground();
    }

    /* Check if a background saving or AOF rewrite in progress terminated. */
    if (server.rdb_child_pid != -1 || server.aof_child_pid != -1 ||
        ldbPendingChildren())
    {
        int statloc;
        pid_t pid;

        // wait3选项设置为非阻塞等待, 立即取结果
        //      如果有结束的保存任务, 则进行RDB/AOF替换
        if ((pid = wait3(&statloc,WNOHANG,NULL)) != 0) {
            int exitcode = WEXITSTATUS(statloc);
            int bysignal = 0;

            if (WIFSIGNALED(statloc)) bysignal = WTERMSIG(statloc);

            if (pid == -1) {
                serverLog(LL_WARNING,"wait3() returned an error: %s. "
                    "rdb_child_pid = %d, aof_child_pid = %d",
                    strerror(errno),
                    (int) server.rdb_child_pid,
                    (int) server.aof_child_pid);
            } else if (pid == server.rdb_child_pid) {
                backgroundSaveDoneHandler(exitcode,bysignal);
            } else if (pid == server.aof_child_pid) {
                backgroundRewriteDoneHandler(exitcode,bysignal);
            } else {
                if (!ldbRemoveChild(pid)) {
                    serverLog(LL_WARNING,
                        "Warning, detected child with unmatched pid: %ld",
                        (long)pid);
                }
            }
            updateDictResizePolicy();
        }
    } else {
        
        // 如果没有正在进行的SAVE / BGSAVE / AOF, 则要看下需不需要保存RDB/AOF
        for (j = 0; j < server.saveparamslen; j++) {
        struct saveparam *sp = server.saveparams+j;

        // 检查是否达到了某个自动保存条件
        //      前提是没有正在执行的BGSAVE或AOF保存任务, 
        if (server.dirty >= sp->changes &&
            server.unixtime-server.lastsave > sp->seconds &&
            (server.unixtime-server.lastbgsave_try >
                CONFIG_BGSAVE_RETRY_DELAY ||
                server.lastbgsave_status == C_OK))
        {
            serverLog(LL_NOTICE,"%d changes in %d seconds. Saving...",
                sp->changes, (int)sp->seconds);
            rdbSaveBackground(server.rdb_filename);
            break;
        }
        }

        // 检查有没达到要进行AOF的条件 (AOF命令达到一定阈值)
        if (server.rdb_child_pid == -1 &&
            server.aof_child_pid == -1 &&
            server.aof_rewrite_perc &&
            server.aof_current_size > server.aof_rewrite_min_size)
        {
        long long base = server.aof_rewrite_base_size ?
                        server.aof_rewrite_base_size : 1;
        long long growth = (server.aof_current_size*100/base) - 100;
        if (growth >= server.aof_rewrite_perc) {
            serverLog(LL_NOTICE,"Starting automatic rewriting of AOF on %lld%% growth",growth);
            rewriteAppendOnlyFileBackground();
        }
        }
    }


    // 如果延期的AOF执行完毕, 要将缓冲区的命令再刷到文件中
    if (server.aof_flush_postponed_start) flushAppendOnlyFile(0);

    /* AOF write errors: in this case we have a buffer to flush as well and
     * clear the AOF error in case of success to make the DB writable again,
     * however to try every second is enough in case of 'hz' is set to
     * an higher frequency. */
    run_with_period(1000) {
        if (server.aof_last_write_status == C_ERR)
            flushAppendOnlyFile(0);
    }

    // 释放加到"异步关闭客户端队列"的客户端
    freeClientsInAsyncFreeQueue();

    // 将一些"CLIENT_BLOCKED"标志位被重置(即从阻塞变成非阻塞)的客户端加到unblocked queue
    clientsArePaused();

    /* Replication cron function -- used to reconnect to master,
     * detect transfer failures, start background RDB transfers and so forth. */

    // 1秒执行一次复制cron
    run_with_period(1000) replicationCron();

    /* Run the Redis Cluster cron. */
    // 100毫秒执行一次集群cron
    run_with_period(100) {
        if (server.cluster_enabled) clusterCron();
    }

    // 100毫秒执行一次哨兵cron(在哨兵模式下)
    run_with_period(100) {
        if (server.sentinel_mode) sentinelTimer();
    }

    // 清理过期的socket
    run_with_period(1000) {
        migrateCloseTimedoutSockets();
    }

    /* Start a scheduled BGSAVE if the corresponding flag is set. This is
     * useful when we are forced to postpone a BGSAVE because an AOF
     * rewrite is in progress.
     *
     * Note: this code must be after the replicationCron() call above so
     * make sure when refactoring this file to keep this order. This is useful
     * because we want to give priority to RDB savings for replication. */
    if (server.rdb_child_pid == -1 && server.aof_child_pid == -1 &&
        server.rdb_bgsave_scheduled &&
        (server.unixtime-server.lastbgsave_try > CONFIG_BGSAVE_RETRY_DELAY ||
         server.lastbgsave_status == C_OK))
    {
        if (rdbSaveBackground(server.rdb_filename) == C_OK)
            server.rdb_bgsave_scheduled = 0;
    }

    server.cronloops++;
    return 1000/server.hz;
}
```


### 缓存时间

缓存时间: 分为秒级和微秒级, 精度不高(>=100ms, 即cron周期)
- 适用于低精度场景
    - LRU
    - 判断是否执行BGSAVE等持久化
    - 计算上线时间
    - 计算客户端活跃时间等

```cpp
void updateCachedTime(void) {
    server.unixtime = time(NULL);
    server.mstime = mstime();
}
```

### shutdown

```cpp
int prepareForShutdown(int flags) {
    int save = flags & SHUTDOWN_SAVE;
    int nosave = flags & SHUTDOWN_NOSAVE;

    serverLog(LL_WARNING,"User requested shutdown...");

    /* Kill all the Lua debugger forked sessions. */
    ldbKillForkedSessions();

    /* Kill the saving child if there is a background saving in progress.
       We want to avoid race conditions, for instance our saving child may
       overwrite the synchronous saving did by SHUTDOWN. */
    if (server.rdb_child_pid != -1) {
        serverLog(LL_WARNING,"There is a child saving an .rdb. Killing it!");
        kill(server.rdb_child_pid,SIGUSR1);
        rdbRemoveTempFile(server.rdb_child_pid);
    }

    if (server.aof_state != AOF_OFF) {
        /* Kill the AOF saving child as the AOF we already have may be longer
         * but contains the full dataset anyway. */
        if (server.aof_child_pid != -1) {
            /* If we have AOF enabled but haven't written the AOF yet, don't
             * shutdown or else the dataset will be lost. */
            if (server.aof_state == AOF_WAIT_REWRITE) {
                serverLog(LL_WARNING, "Writing initial AOF, can't exit.");
                return C_ERR;
            }
            serverLog(LL_WARNING,
                "There is a child rewriting the AOF. Killing it!");
            kill(server.aof_child_pid,SIGUSR1);
        }
        /* Append only file: fsync() the AOF and exit */
        serverLog(LL_NOTICE,"Calling fsync() on the AOF file.");
        aof_fsync(server.aof_fd);
    }

    /* Create a new RDB file before exiting. */
    if ((server.saveparamslen > 0 && !nosave) || save) {
        serverLog(LL_NOTICE,"Saving the final RDB snapshot before exiting.");
        /* Snapshotting. Perform a SYNC SAVE and exit */
        if (rdbSave(server.rdb_filename) != C_OK) {
            /* Ooops.. error saving! The best we can do is to continue
             * operating. Note that if there was a background saving process,
             * in the next cron() Redis will be notified that the background
             * saving aborted, handling special stuff like slaves pending for
             * synchronization... */
            serverLog(LL_WARNING,"Error trying to save the DB, can't exit.");
            return C_ERR;
        }
    }

    /* Remove the pid file if possible and needed. */
    if (server.daemonize || server.pidfile) {
        serverLog(LL_NOTICE,"Removing the pid file.");
        unlink(server.pidfile);
    }

    /* Best effort flush of slave output buffers, so that we hopefully
     * send them pending writes. */
    flushSlavesOutputBuffers();

    /* Close the listening sockets. Apparently this allows faster restarts. */
    closeListeningSockets(1);
    serverLog(LL_WARNING,"%s is now ready to exit, bye bye...",
        server.sentinel_mode ? "Sentinel" : "Redis");
    return C_OK;
}
```

### 自动保存

saveparam: 
```cpp
struct saveparam {
    time_t seconds;
    int changes;
};
```