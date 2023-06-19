# Redo Log


## 基本概念

Redo Log: 记录对page的更新操作
- 记录方式: **顺序写入**, **round robin循环使用**
- redo log group: 每个group有多个redo log file, group之间互为副本
- LSN: 日志序号, 单位为字节, 表示日志最新进度, **循环自增使用**

配置
- `innodb_log_group_home_dir`: 可以指定redo log的目录
- `innodb_mirrored_log_groups`: 组数
- `innodb_log_files_in_groups`: 组内redo log文件数
- `innodb_log_file_size`
    - 太小会导致频繁`Async/Sync Flush Checkpoint`
    - 太大导致宕机恢复慢


## Redo Log持久化

刷磁盘(记录Checkpoint)时机:
- Sharp Checkpoint: 同步所有脏页. 关闭时使用
    - `innodb_fast_shutdown=1`时生效
- Fuzzy Checkpoint: 同步部分脏页. 运行时使用
    - Master Thread Checkpoint
    - FLUSH_LRU_LIST Checkpoint: LRU空闲页不够, 移除脏页时
    - Dirty Page too much Checkpoint: 脏页太多
    - Async/Sync Flush Checkpoint: redo log文件不够用
- commit
    - `innodb_flush_log_at_tx_commit`:
        - 0: 提交时不flush, 由Master Thread同步
        - 1: 提交时flush并fsync (**真正保证持久性**)
        - 2: 提交时flush, 不fsync, 由操作系统决定

Async/Sync Flush Checkpoint: 在Checkpoint落后Redo Log太多时触发
- Async Flush: 触发时机 `redo_lsn - checkpoint_lsn > 75% total_redo_log_size`
    - 阻塞发现问题的用户线程
- Sync Flush: 触发时机 `redo_lsn - checkpoint_lsn > 90% total_redo_log_size`
    - 阻塞所有用户线程


## Redo Log原理

Redo Log与事务关系
- Redo Log在事务执行过程中不间断被写入, 如果多个事务并发, 则可能穿插记录到redo log
    ```
    T1, T2, T1, *T2, T3, T1, *T3, *T1
    ```

#### Redo Log block

Redo Log block: redo log写磁盘时的基本单位
- 大小: 512 byte
    - 与扇区大小相同, 因此写到磁盘上是原子的, 不用double write, 但还是有个tailer用来校验
- 结构:
    ```yml
    log block header:
        header no:           4 byte      # block id, 循环自增
        data length:         2 byte      # 有效数据长度, 最大为512字节
        first rec group:     2 byte      # 首记录指针
        checkpoint no:       4 byte      # 最近LSN

    data: []
    log block tailer:        4 byte      # 值为header no, 用于校验信息是否完整
    ```
<br/>

前4个block
- redo log file header: 记录一些元信息
- checkpoint1:
- 空
- checkpoint2: 

双checkpoint的作用: 
- 交替使用来记录最新的check point LSN
- 分别占一个block是为了保证不会同步丢失