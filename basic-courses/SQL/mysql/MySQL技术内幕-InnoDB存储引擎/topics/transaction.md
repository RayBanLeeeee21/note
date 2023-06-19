

# 问题

## 事务与binlog的冲突

### Statement-based binlog 与 read-committed 的不一致问题

前提:
- binlog日志以事务为单位, 在事务提交时写到binlog文件
- read-committed隔离级别时, 事务可能出现幻读

场景: 假设user表中`id=1`的记录, 当前`version=1`

主库:
```sql
-- Time = t1, 事务A执行Q1
insert into opt_log values (
    select null, 
           id as user_id, 
           version                           -- A看到 opt_log.version = 1
    from user where id = 1  
);     
 
-- Time = t2, 事务B执行Q2
update user set version = 2 where id = 1;    -- B看到 user.version = 2   


-- Time = t3, 事务B执行Q3
commit;                                     
-- 提交后实际结果: user.version = 2             -- user.version 持久化为 2

-- Time = t3, 事务B执行Q3
commit;                                       -- opt_log.version 持久化为 1                                     
```

在T3时刻, 事务B提交, 语句(Q2, Q3)写入binlog文件
在T4时刻, 事务A提交, 语句(Q1, Q4)写入binlog文件
binlog 实际记录顺序为(Q2, Q3, Q1, Q4)


备库收到binlog后, 实际执行顺序为
```sql
-- version 初始为 1
 
-- Time = t2, 事务B执行Q2
update user set version = 2 where id = 1;    -- B看到 user.version = 2   


-- Time = t3, 事务B执行Q3
commit;                                     
-- 提交后实际结果: user.version = 2             -- user.version 持久化为 2

-- 事务A执行Q1
insert into opt_log values (
    select null, 
           id as user_id, 
           version                           -- A看到 opt_log.version = 2
    from user where id = 1  
); 

-- Time = t3, 事务B执行Q3
commit;                                       -- opt_log.version 持久化为 2        
```

最终结果: 主库中version=1, 备库中version=2
