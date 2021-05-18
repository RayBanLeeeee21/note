# Chapter 05 set

## 1. 对象编码

set:
- hashtable: 不能满足intset条件的都用hashtable表示
- intset: `type(element)==integer && count(element) <=512` 

### 1.1 hashtable

参考[hashtable](./ch04-hash.md#11-hashtable)

### 1.2 intset

数据结构:
-   ```c++
    typedef struct intset{
        uint32_t encoding; // 值为1, 2, 4, 8, 即每个值的sizeof, 对应uint8t, uint16_t, uint32_t, uint64_t
        uint32_t length;   // 整数集合长度
        int8_t contents[]; // contents数组声明为int8_t, 实际上不一定为int8_t
    }
    ```

特性:
- encoding类型: int8_t, int16_t, int32_t, int64_t
  - 升级: 只要出现超出表示范围的数, 就要把原来的所有数升级, **只升不降**
  - 降级: 升级以后就不再降
- 节约内存, 但长度太长了以后效率低, 特别是在中间插入元素的时候

应用场景: 元素为整数的hash

## 2. 所有操作

[所有操作](http://redisdoc.com/set/index.html)(**都是原子操作**)

基本操作
- `SADD key member [member ...]`
    - **返回**: 结果个数
- `SISMEMBER key member`
    - **返回**: 0|1
- `SPOP key [count]`: 随机POP若干元素
    - **返回**: 被POP的元素列表
- `SRANDMEMBER key [count]`:
    - **返回**: 随机选取的元素|元素列表
- `SREM key member [member ...]`
    - **返回**: 成功移除的个数
- `SCARD key`: 基数
- `SMEMBERS key`: 所有成员
    - **返回**: 成员列表
- `SSCAN`: 迭代器

多集合操作 & 集合计算
- `SMOVE source destination member`: 移动
    - **返回**: 成功从source移除的个数(与加入destination的结果无关)
- `SINTER key [key ...]`: 
    - **返回**: 交集
- `SINTERSTORE destination key [key ...]`
    - **返回**: 交集成员个数
- `SUNION key [key ...]`
    - **返回**: 并集
- `SUNIONSTORE destination key [key ...]`
    - **返回**: 并集成员个数
- `SDIFF key [key ...]`
    - **返回**: 差集(key1 - key2)
- `SDIFFSTORE destination key [key ...]`
    - **返回**: 差集成员个数