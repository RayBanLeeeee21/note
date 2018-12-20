key
* 超码: 可以唯一地标识一条记录的字段或者字段组合
* 候选码: 任何真子集都不是超码的最小超码
* 主码(key): 被选中用来标识记录的候选码
* 主属性: 被包含在候选码中的字段
* 非主属性: 不被包含在候选码中的字段

范式:
* 1NF: 所有的数据项都是原子的. 关系数据库都满足
* 2NF: 满足1NF, 消除部分依赖(非主键字段不会只依赖于主键的部分字段)
* 3NF: 满足2NF, 消除依赖传递
* BCNF: 满足3NF, 消除主属性对候选码的部分依赖
* 例子: 
    * 2NF
        * 反例: 
            * table: A, B, C, D
                * primary_key(A, B)
                * A, B -> C
                * A -> D
        * 解决方法: 
            * table1: A, B, C 
                * primary_key(A, B)
                * A, B -> C
            * table2: A, D
                * foreign_key(A)
                * A -> D
    * 3NF
        * 反例:
            * table: A, B, C
                * primary_key(A)
                * A -> B
                * B -> C
        * 解决方法:
            * table1: A, B
                * primary_key(A)
                * A -> B
            * table2: B, C
                * foreign_key(B)
                * B -> C
    * BCNF
        * 反例: 
            * table: A, B, C, D
                * primary_key(A, B)
                * A -> C
                * C -> A
                * A, B -> D
        * 解决方法:
            * table1: A, B, D
                * primary_key(A, B)
                * A, B -> D
            * table2: A, C
                * foreign_key(A)
                * A -> C
                * C -> A
        [](https://www.cnblogs.com/langdashu/p/5924082.html)