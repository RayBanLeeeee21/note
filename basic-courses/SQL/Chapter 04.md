# Chapter 04 高级SQL

## 4.1 SQL的数据类型与模式
* 内建数据类型
* 自定义数据类型 

### 4.1.1 SQL中内建的数据类型

数据类型 : 
* date : 年月日
* time(p) : 时分秒, p表示秒的精度
* timestamp(p) : date+time

(未完)

### 4.1.2 用户自定义类型
用户自定义类型 : 
* 独特类型 : 
    * e.g. : 
        *   ```sql
            create type Dollars as numeric(12,2) final;
            create type Pounds as numeric(12,2) final;
            create table account(
                account_id bigint unsigned,
                balance Dollars
            );
            ```
    * 为Dollars类型的属性赋予Pounds类型的值时会报错 
* 结构化数据类型 : 允许**嵌套记录结构**, **数组**, **多重集**