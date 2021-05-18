# Chapter 02 string对象

## 1. 对象编码

string对象编码:
- raw(SDS)
- embstr
- int

### 1.1 SDS

参考:
- 源码: sds.c sds.h
- [Redis深入浅出——字符串和SDS](https://blog.csdn.net/qq193423571/article/details/81637075)



数据结构
- 旧版 (<=2.8.9)
    ```c++
    struct sdshdr{
        int len;     // 长度
        int free;    // 空余空间
        char buf[];
    }
    ```
- 新版: 按照字符长度, 其表示长度的类型不同, 分为5种类型. 
    - 代码
        ```c++
        struct __attribute__ ((__packed__)) sdshdr5 {
            unsigned char flags; /* 3 lsb of type, and 5 msb of string length */
            char buf[];
        };
        struct __attribute__ ((__packed__)) sdshdr8 {
            uint8_t len; /* used */
            uint8_t alloc; /* excluding the header and null terminator */
            unsigned char flags; /* 3 lsb of type, 5 unused bits */
            char buf[];
        };
        struct __attribute__ ((__packed__)) sdshdr16 {
            uint16_t len; /* used */
            uint16_t alloc; /* excluding the header and null terminator */
            unsigned char flags; /* 3 lsb of type, 5 unused bits */
            char buf[];
        };
        struct __attribute__ ((__packed__)) sdshdr32 {
            uint32_t len; /* used */
            uint32_t alloc; /* excluding the header and null terminator */
            unsigned char flags; /* 3 lsb of type, 5 unused bits */
            char buf[];
        };
        struct __attribute__ ((__packed__)) sdshdr64 {
            uint64_t len; /* used */
            uint64_t alloc; /* excluding the header and null terminator */
            unsigned char flags; /* 3 lsb of type, 5 unused bits */
            char buf[];
        };
        ```
    - 类型
        - `sdshdr5`: 只有一个flags标志, 其中后5位用来表示长度
        - `sdshdr8`: 用`uint8_t`表示长度
        - `sdshdr16`: 用`uint16_t`表示长度
        - `sdshdr32`: 用`uint32_t`表示长度
        - `sdshdr64`: 用`uint64_t`表示长度
    - `flags`: 前3位用来区分SDS的具体类型, 后3位在`sdshdr5`中表示长度
    - `alloc`: 已分配的大小. 旧版存`free`, 新版通过`alloc`-`len`得到空闲长度

特点
1. 保存'\0', 但不计入len, 因此可重用一部分的C函数.
2. 保留字符串的长度:
    - 获取长度时, 复杂度为o(1)
    - 修改前判断长度, 不会造成溢出
    - 字符串中可以包含任何字符 (包括'\0') 
3. 减少重分配:
    - 预分配(v2.9): 需要增加空间时
        - 如果新len小于1MB, 预留长度与最新len相等
        - 如果新len大于1MB, 预留长度为1MB
    - 惰性空间释放
4. 对二进制安全: SDS不是以'\0'来判断结尾, 而是存了长度. 因此也可以直接在sds中存字节序列


#### 1.1.3 应用场景
- string对象的编码之一
- redis源码中所有非字面量的字符串
    - AOF缓冲区
    - 客户端输入缓冲区


### 1.2 embstr

#### 1.1.1 数据结构

embstr的数据结构与`sdshdr8`一致, 即可以表示的最大长度为`1<<8`(实际最大长度为44)
- embstr只是数据的顺序与`sdshdr8`一致, 在源码中没有对应的struct, 而是通过`malloc`分配
- embstr直接连在`redisObject`的后面
    - 可以很好地利用局部性原理, 避免指针跳转
    - 最大长度44, 与`redisObject`长度加起来刚好64
- embstr**不可变**

应用场景:
- 小的不可变字符串
    - `APPEND`会生成新的`raw`对象, 即使新对象不超过44字节(假定该变量后面还会变)
- 保存浮点类型: 浮点数默认保存成`embstr`
    - 对`embstr`|`raw`进行`INCRBYFLOAT`, 会尝试转成`long double`, 结果为`embstr`


## 2. string 的所有操作

[所有操作](http://redisdoc.com/string/index.html)(**都是原子操作**)

get & set
- `SET key value [EX seconds] [PX ms] [NX|XX]`
    - EX: 过期时间
    - PX: 过期时间(ms)
    - NX|XX: 不存在|存在时才操作
    - **返回**: 新值
- `GET key`
    - **返回**: 值, 类型不对报错
- `MSET k1 v1 k2 v2 ...`: "Multi-SET"
    - **返回**: 新值组成的列表
- `MGET k1 k2 ...`: "Multi-GET"
    - **返回**: 多个值组成的列表
- `GETSET key value`: 设置新值
    - **返回**: 旧值

修改
- `APPEND key value`: 追加新内容到字符串后
    - **返回**: 新长度
    - 新结果一定编码为`raw`
子串
- `SETRANGE key offset value`: 将value覆盖到从offset开始的位置
    - **返回**: 新长度
- `[GETRANGE|SUBSTR] key start end`: **返回**: 范围为`[start, end]`的子串


数字类型
- `[INCR|DECR] key`
    - 为数字类型的值加|减1, 无不存在则默认原来是0, 即新值为1|-1; 原来非数字类型则报错
    - **返回**: 新值
    - 新结点一定编码为int
- `[INCRBY|DECRBY] key value`
    - 与INCR|DECR类似, 但可指定增加值

与分布式多进程协作相关的操作
- `SETNX`: CAS(key, null, newVal)
- `GETSET`: 更新为新值并拿到上个旧值
- `INCR`: 原子自增