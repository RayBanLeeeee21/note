# ibuf

#### 简介

IBUF:
- 设计初衷: 辅助索引插入时在磁盘中不连续, 通过先插入缓冲, 再批量写磁盘, 来**减少IO**
- 限制: 
    - **辅助索引**: 主键索引相对连续, 不需要这种优化
    - **非唯一索引**: 无法立即判断同key记录是否存在
- 过程: 
    1. `INSERT/UPDATE/DELETE`时, 先写入change buffer
    2. 在一定条件下, 被merge (应用到索引上) 
        - 有事务要读尚未merge的索引
        - IBUF空间不够: (小于1/32)
        - Master Thread定时触发

#### 内部实现 

Insert Buffer:
- 逻辑结构: **B+树**: 
    - 顺序访问: merge时用到
    - 随机访问: 读索引触发merge时可能用到
- **change buffer page**: 意味也会产生redo log. 也会定时持久化到磁盘, 以防宕机丢失. 


Insert Buffer B+树
- key: 
    ```yaml
    space:    # 4B  表空间ID 
    mark:     # 1B  
    offset:   # 4B  页偏移
    ```
- 叶结点:
    ```yaml
    key_fields:                  # 共9B
        space:                      # 4B  
        mark:                       # 1B
        offset:                     # 4B
    metadata:                    # 共4B
        IBUF_REC_OFFSET_COUNT:      # 2B 记录顺序, 保证replay顺序
        IBUF_REC_OFFSET_TYPE:       # 1B 类型
        IBUF_REC_OFFSET_FLAGS:      # 1B
    secondary-index-recode:      # 数据字段
        ...
    ```

Insert Buffer Bitmap: 特殊页, 用来记录表空中所有Insert Buffer Bitmap的位置与状态
- 固定为表空间**第2页**
- 管理16384页, 每页状态信息4B
    ```yaml
    IBUF_BITMAP_FREE:     # 2b: 0: 无剩余, 1: 剩余>1/32, 2: 剩余>1/16, 3: 剩余>1/8
    IBUF_BITMAP_BUFFERED: # 1b: 缓存有记录
    IBUF_BITMAP_IBUF:     # 2b: IBUF标志, 表示这是一个Insert Buffer页
    ```

#### 配置&管理

配置:
- `innodb_change_buffering`: 开启的Change Buffer类型
    - `inserts`: 插入
    - `deletes`: 删除
    - `purges`: 更新. UPDATE操作会先记录delete buffer对记录做标记, 再记录purge buffer真正删除记录
    - `changes`: `inserts`+`delete`
    - `all`
    - `none`
- `innodb_change_buffer_max_size` 最大buffer尺寸


管理: 
- 查看IBUF使用情况: `show engine innodb status`
    ```
    Ibuf: size 1, free list len 0, seg size 2, 0 merges
    merged operations:
     insert 0, delete mark 0, delete 0
    discarded operations:
     insert 0, delete mark 0, delete 0
    ```
    - `size`: IBUF总大小(MB)
    - `free list len`: 空闲列表长度
    - `merges`: 合并了多少次
    - `merged operations`: 被合并的操作数
    - `discarded operations`: 被丢弃的操作数(如表被删除)