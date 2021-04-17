# chapter 19 事务

事务相关命令:
* WATCH key
* MULTI: 开始事务
    * 执行MULTI后只能执行DISCARD或者EXEC, 不能WATCH或者MULTI
* DISCARD: 丢弃事务
* EXEC: 执行


MULTI实现:
* 执行MULTI命令时, 对应RedisClient的flag的REDIS_MULTI位被置位
* 执行命令时
    ```
    if client.flag & REDIS_MULTI != 0
        if command in {MULTI, WATCH, DISCARD, EXEC}
            直接执行
        else
            命令入队
    ```
* 命令队列:
    ```cpp
    typedef struct RedisClient{
        // ...
        MultiState multiState;
        // ...
    };
    
    typedef struct MultiState{
        MultiCommand*commands; // 命令队列
        int count;
    };

    typedef struct MultiCommand{
        robj ** argv;
        int argc;
        struct redisCommand *cmd;
    };
    ```
* 执行事务
    * 