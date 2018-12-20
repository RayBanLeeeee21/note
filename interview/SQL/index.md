
索引类型:
* 唯一索引: 键不可重复
* 主键索引: 特殊的唯一索引
* 聚集索引: 物理顺序与逻辑顺序一致的索引, 一个表只能有一个聚集索引
* 联合索引: 多列字段构成的索引
    * 对于AB联合索引, where a="xx"可以用索引查, where b="xx"不能用索引查

索引不可用的情况:
* 隐式转换导致索引失效: select * from user phone tu_mdn=13333333333;
* 对索引列进行运算导致索引失效(+-*/, !=): select * from test where id-1=9
* 对索引使用内部函数:select * from test where round(id)=10
    * 可建立函数索引 
* <> 、not in 、not exist、!=
* where index = 1 or nonidex = 2
* like "%xxx"