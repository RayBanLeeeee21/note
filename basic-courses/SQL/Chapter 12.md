## 12.1 概述

查询处理过程:
-   ```mermaid
    graph LR
        begin(开始)
        analyizer[语法分析与翻译器]
        optimizer[优化器]
        executor[执行器]
        DB((DB))
        done(结束)

        begin-->|SQL|analyizer
        analyizer-->|关系代数表达式|optimizer
            DB-->|统计数据|optimizer
        optimizer-->|执行计划|executor
            DB-->|数据|executor
        executor-->|查询结果|done
        
    ```
- 语法分析与翻译:
    1. 构造语法分析树
    2. 校验: 检查**语法错误**; 检查表名等
- 优化: 生成的关系表达式可能不止一种, 要添加**注释**说明执行顺序, 即生成**执行计划**
    - **执行计划**: 用于查询的**执行原语**的序列
    - 有些数据库可能不是用关系代数表达式来表示查询, 而是用加了注释的语法分析树来表示查询


## 12.2 查询代价的度量

开销度量:
- 磁盘开销: 传送磁盘块数, IO次数
- 响应时间: 取决于
    - 数据在缓存中的分布
    - 数据在磁盘中的分布