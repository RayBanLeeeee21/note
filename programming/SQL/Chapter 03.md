# Chapter 03 SQL

## 3.1 背景

SQL的组成部分 : 
* 数据定义语言(DDL) : 定义关系模式, 删除关系, 修改关系模式
* 数据操纵语言(DML) : 增删改查
* 完整性(integrity) : 数据必须满足的完整性约束条件
* 视图定义
* 事务控制
* 嵌入式SQL和动态SQL
* 授权

## 3.2 数据定义

关系信息定义 : 
* 关系模式
* 属性值域
* 完整性约束
* 关系维持的索引集合
* 关系的安全性和权限信息
* 关系的物理存储结构

### // 3.2.1 基本域类型

域类型 : 
* CHAR(n) : 长度固定为n的字符串
* VARCHAR(n) : 长度最大为n的字符串
* INT :
* SMALLINT : 
* NUMERIC(p, d) : 总长度为p, d位小数的数
* REAL, DOUBLE
* FLOAT(n) : 精度至少为n位的小数

### // 3.2.2 SQL的基本模式定义 

**create** (模式定义) :
*   ```SQL
    # 方言 : mysql
    # 用户
    CREATE TABLE `user` (
        `user_id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT,
        `username` CHAR(50) NOT NULL,
        PRIMARY KEY (`user_id`),
        UNIQUE INDEX `username` (`username`)
    )
    COLLATE='utf8_general_ci'
    ENGINE=InnoDB;
    ```
*   ```SQL
    # 方言 : mysql
    # (朋友圈)状态
    CREATE TABLE `status` (
        `id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT,
        `user_id` BIGINT(20) UNSIGNED NOT NULL,
        `status_class` ENUM('A','B','C') NULL DEFAULT NULL,
        `content` VARCHAR(140) NULL DEFAULT NULL,
        PRIMARY KEY (`id`),
        INDEX `user_id` (`user_id`),
        CONSTRAINT `user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
    )
    COLLATE='utf8_general_ci'
    ENGINE=InnoDB
    AUTO_INCREMENT=8;
    ```
*   ```SQL
    # 方言 : mysql
    # 用户关系
    CREATE TABLE `relationship` (
        `id1` BIGINT(20) UNSIGNED NOT NULL,
        `id2` BIGINT(20) UNSIGNED NOT NULL,
        `relationship_class` ENUM('friend','parent') NOT NULL,
        PRIMARY KEY (`id1`, `id2`) # 以id1与id2的组合为主键
    )
    COLLATE='utf8_general_ci'
    ENGINE=InnoDB;
    ```
**alter** (更改模式) : 
*   ```SQL
    # 方言 : mysql
    # (朋友圈)状态
    ALTER TABLE `status`
        ADD COLUMN `字段 5` VARCHAR(140) NULL DEFAULT NULL AFTER `status_class`,
        DROP COLUMN `content`,
        ADD UNIQUE INDEX `字段 5` (`字段 5`);
    ```

## 3.3 SQL查询的基本结构 

SQL查询 : 
*   ```SQL
    select distinct u1.username as name1, u2.username as name2
    from user as u1, relationship as rl, user as u2 
    where
        (u1.id = rl.user1_id and u2.id = rl.user2_id);
    ```

### // 3.3.1 select 子句

**distinct** :  
*   ```SQL
    select distinct id from table  # 表示去除重复 
    ```

**all** :
*   ```SQL
    select all id from table  # 表示不去除重复(可省略)
    ```

**算术表达式**(广义投影运算) : 
*   ```SQL
    select id*100 from table # 算术表达式可含有 + - * /
    ```

### // 3.3.2 where子句

逻辑连词 : **not**, **or**, **and** 
比较运算符 : <, <=, \>, \>=, =, <> / !=, between..and..(等价于..<=..&&..\>=..)

### // 3.3.3 - 3.3.5 from, 更名, 元组变量

**as** (更名运算) : 可出现在select和from子句
**元组变量** :
*   ```SQL
    # as 同时出现在select 和where子句
    # u1, u2, rl为元组变量
    select u1.username as username1, u2.username as username2 
        from user as u1, user as u2, relationship as rl　
        where u1.user_id = rl.id1 and u2.user_id = rl.id2
    ``` 
### // 3.3.6 字符串运算

**%** : 匹配任何子串
**_** : 匹配一个字符
**escape** : 表转义
*   ```SQL
    select user_name from user_table where user_name like 'abc\%d%' escape '\';
    ```

### // 3.3.7 排序

**asc** : 升序, 默认
**desc** : 降序
*   ```SQL
    select distinct 
        u1.username as name1, u2.username as name2
    from user as u1, relationship as rl, user as u2 
    where (u1.id = rl.user1_id and u2.id = rl.user2_id)
    order by u2.username desc, u1.username asc;
    ```
* 重复 ?

## 3.4 集合运算
### // 3.4.1 union  <!-- fdasdfda-->
**union** : 默认去重
* union all : 不去重

### // 3.4.2 intersect
**intersect** : 默认去重 (mysql不支持)
* intersect all : 不去重

### // 3.4.3 except
**except** : 默认去重 (mysql不支持)
* except all : 不去重
* 	```SQL
        (select * from user)
    union all 
        (select * from user)
    order by 
        username desc, id; asc
    ```

## 3.5 聚集函数
聚集函数 : **avg**, **min**, **max**, **sum**, **count**, **group by**, **having** 
*   ```SQL
    # having 与 group 的关系类似 select 与 where
    # count
    (select u1.username as name1, count(distinct u2.id) as id2
        from user as u1, relationship as rl, user as u2 
        where (u1.id = rl.user1_id and u2.id = rl.user2_id)
        group by u1.username
            having count(distinct u2.id) > 3    
        order by u2.username asc, u1.username desc)
    ```
* count(*)的括号中不能用distinct

## 3.6 空值 
// 略, 见2.5

## 3.7 嵌套子查询 
### // 3.7.1 集合成员资格
**in / not in** (集合成员资格) : 
*   ```SQL
    # in
    select * 
        from user as u1
        where (u1.user_id, u1.username) in (
            select ub.user_id, ub.username
                from user_backup as ub
            )
    ```
*   ```SQL
    # not in
    select * 
        from user as u1
        where (u1.user_id, u1.username) not in (
            select ub.user_id, ub.username
                from user_backup as ub
            )
    ``` 

### // 3.7.2 集合的比较
**some/any**
**all**
*   ```SQL
    select * 
        from user as u1
        where u1.user_id > some 
            (select ub.user_id
                from user_backup as ub);
    ``` 
*   ```SQL
    select * 
        from user as u1
        where u1.user_id > all  
            (select ub.user_id
                from user_backup as ub);
    ``` 

### // 3.7.2 是否为空关系

**exists / not exists**
*   ```SQL
    # exists
    # 该例子的查询结果与3.7.1中的in的例子等效
    select * 
        from user as u1
        where exists (
            select ub.user_id, ub.name
                from user_backup as ub
                where ub.user_id = u1.user_id and ub.name = u1.username
            )
    ```  