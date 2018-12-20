## 方法调用
调用方式
* 编译时确定
    * invokestatic
        * static的方法(private\public)
    * invokespecial
        * <init>
        * private实例方法
        * 父类方法
* 运行时确定
    * invokevirtual
    * invokeinterface
    * invokedynamic

重载/重写
* 先验
    ```
    Human man = new Man();
    ```
    * 静态类型: Human为man的静态类型
    * 实际类型: Man为man的实际类型
* 重载:
    * 实现: 静态分派: 类中有同名函数时, 根据参数的**静态类型**来选择执行
* 重写:
    * 实现: 动态分派: 类中重写了父类的同名函数时, 根据参数的**动态类型**来选择执行(invokevirtual和invokeinterface)
        1. 根据invokevirtual指令的被操作对象, 找到对象的实际类型
        2. 如果该类中能找到匹配的方法, 则用该方法执行, 否则3
        3. 从下往上在父类中寻找匹配的方法, 否则4
        4. 抛出AbstractMethodError
* 方法解析的两个阶段
    * 静态分派(多分派): 根据invoke对象以及参数的静态类型来选择方法
    * 动态分派(单分派): 参数类型已确定, 根据invoke对象的实际类型来选择方法
