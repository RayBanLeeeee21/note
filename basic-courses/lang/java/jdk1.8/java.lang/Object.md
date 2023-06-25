
// todo 补充方法特点

相关话题
- [`java.lang.Class`](./Class.md)
- [`java.lang.Thread`](./Thread.md)
- java.lang.String
- java.util.Map

行为
- 类信息:
    - `getClass()`
- 实例信息
    - `equals()`: 
        - 自反性, 对称性, 传递性, 一致性
        - *应当尽量基于final字段计算*
    - `hashCode()`: 
        - 只要`equals()`结果没变, `hashCode()`结果就不变
        - *应当尽量基于final字段计算*
    - `clone()`: 默认不实现, 实现应该满足
        - `x != x.clone()`
        - `x.equals(x.clone())`
        - `x.hashCode() == x.clone().hashCode()`
- 线程相关
    - `wait()`
    - `wait(long)`
    - `wait(long, int)`
    - `notify()`
    - `notifyAll()`
- String相关
    - `toString()`
- Jvm相关:
    - `finalized()`
    
相关问题:
- Object有哪些方法