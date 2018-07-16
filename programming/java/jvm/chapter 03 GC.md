# Chapter 03 垃圾收集器与内存分配策略

## 3.2 对象已死吗(垃圾回收算法)

### 3.2.1 引用计数算法 
引用计数算法
* 缺点 : 
    * 循环引用
* jvm没有用该算法

### 3.2.2 可达性分析算法

可达性分析算法
* 从GC roots对象作为起始点, 从这些节点开始向下搜索
* GC roots对象 
    * 虚拟机栈中
        * 本地变量表引用的对象
    * 方法区中
        * 类静态属性引用的对象
        * 常量引用的对象
    * 本地方法栈
        * JNI(Java Native Invocation)引用的对象

### 3.2.3 再谈引用

引用类型
1. 强引用(Strong Reference) : 
    * 从不回收
2. 软引用(Soft Reference) : 
    * 内存不足时回收
    * 可用于实现缓存
    * 通过SoftReference类实现 
3. 弱引用(Week Reference) : 
    * gc运行时回收(不管内存是否足够)
    * 可用于实现缓存
    * 通过WeakReference类实现 
4. 虚引用(Phantom Reference) : 
    * 回收时机不确定(任何时候都可能被回收)
    * 通过PhantomReference类实现
### 3.2.4 生存还是死亡 (对象自救)
* 虚拟机使用一个**低优先级**的Finalyzer线程来执行F-Queue中的对象的finalize()方法
    * 当对象被标记, 并且**有必要执行finalize()方法**时(未执行过finalize()方法, 且finalize()方法被覆写)
    * **finalize只能被执行一次**
    * 对象可以在finalize()方法中将自己赋给一个类变量或者对象的成员变量, 使其不会被回收
* 不建议用finalize来关闭外部资源, 建议用**try(resource)**

### 3.2.5 回收方法区
永久代(方法区)的垃圾收集内容 :
* 废弃常量
    * 判断方法 : 如 : 某String常量没有对应的引用, 则可以回收
* 无用的类
    * "无用的类"判断条件 : 
        1. 该类的所有实例被回收(没有实例)
        2. 加载该类的ClassLoader被回收
        3. 类对应的java.lang.Class对象没有在任何地方被引用
    * 在频繁定义ClassLoader的场景都需要虚拟机具备类卸载功能:
        * 反射
        * 动态代理
        * CGLib等ByteCode框架
        * 动态生成JSP
        * OSGi
        * ...

## 3.3 垃圾收集算法 
### 3.3.1 Mark-Sweep算法 
Mark-Sweep算法 :
* 特点 : 
    * 效率低
    * 空间不连续
### 3.3.2 复制算法 
复制算法 : 
* 将内存分为两块, 一次只用一块
* 回收时对一块进行Mark-Sweep后, 将剩余对象复制到另一块.
* 特点 : 
    * 内存利用率不高

复制算法2 : 
* 空间分块为 Survivor1:Eden:Survivor2 = 1:8:1, 平时只使用Eden分配内存.
* 回收方法:  
    1. 将Eden与保存有对象的Survivor进行Mark-Sweep
    2. 然后将存活对象复制到另一个Survivor
* 老年代用于作Survivor的分配担保(即Survivor空间不够时, 上一次新生代的对象进行老年代)

### 3.3.3 Mark-Compact算法
Mark-Compact算法:
* 标记
* 移动到一端
* 清除

### 3.3.4 分代收集算法 
分代收集算法 :
* 把Java堆分为**新生代**和**老年代**
    * 新生代使用Copying算法
    * 老年代使用Mark-Sweep/Mark-Compact算法

## 3.4 HotSpot算法实现 
### 3.4.1 枚举根结点
GCRoot节点 : 
* 全局性的引用(**方法区**中的**常量**和**静态变量**)
* 执行上下文(**栈帧**中的**本地变量表**)

可达性分析对时间的敏感性 : 
* 变量数据多(方法区可能就有几百兆)
* 分析工作必须在一个能确保**一致性**的快照中进行 : 分析时对象引用关系不能改变
    * 必须停止所有线程

准确式GC : 虚拟机可以直接得知哪些地方存放着对象引用, 而不用检查内存中所有的地址, 从而**快速完成GC Roots枚举**
* 准确式内存管理 : 虚拟机可以准确知道内存中某个位置的数据类型
    * HotSpot通过OopMap的数据结构来记录内存特定偏移位置的数据类型 :
        * **类加载完成**时会记录
        * **JIT编译**过程中也会在特定位置记下栈与寄存器中哪些位置是引用
            

### 3.4.2 安全点

问题[OopMap] : 可能导致引用关系变化(以致改变OopMap内容)的指令很多, 如果每条指令都生成OopMap, 成本会很高
* 安全点: 
    * 定义 : 在安全点中, 线程的一些状态可以被确定
        * 可以记录OopMap的状态，从而确定GC Root的信息，使JVM可以安全的进行一些操作，比如开始GC
    * 选定原则 :
        * 不能太多 : 太多会增大运行时的负荷
        * 不能太少 : 太少让GC等待过久
        * 一般选择位置
            1. 循环末尾
            2. 方法返回前 / 调用方法的call指令末尾
            3. 可能抛异常的位置
    * 问题[安全点] : 如何让线程都跑到安全点 
        * 抢先式中断(很少) : GC发生时, JVM把所有线程中断. 如果有线程未跑到安全点, 则让这些线程跑到安全点再GC
        * 主动式中断 : 让线程在安全点主动检查有无发生中断(即轮询点与安全点重合)

### 3.4.3 安全区域
问题[主动式抢断] : 主动抢断时如果有线程在Sleep状态或者Blocked状态?
* Safe Region :
    * 定义 : Safe Point的扩展版
    * 算法 : 
        * 线程进入Safe Region时, 标识自己已进入Safe Region. GC时, JVM不用管标识为进入Safe Region的线程
        * 线程离开Safe Region时, 检查是否完成了根节点枚举(或整个GC过程), 如未完成则要等待其完成

## 3.5 垃圾收集器(实现)

### 3.5.1 Serial
Serial(串行)收集器:
* 适用范围: Client 模式默认**新生代**收集器
    * 停顿可接受
* **复制算法**
* 特点: 
    * 单线程
    * Stop The World
    

### 3.5.2 ParNew 收集器
ParNew(Parallel New): 并行版Serial
* 适用范围: Server 模式首选**新生代**收集器
* **复制算法**
* 特点:
    * 多线程
    * Stop The World

### 3.5.3 Parallel Scavenge收集器
Parallel Scavenge收集器: "吞吐量"优先收集器
* **新生代**收集器
* 特点:
    * 精确控制吞吐量
* 参数: 
    * -XX:MaxGCPauseMillis: 最大收集时间
    * -XX:GCTimeRatio: 吞吐量
    * -XX:UseAdaptiveSizePolicy

### 3.5.4 Serial Old 收集器
Serial Old 收集器: Serial的老年代版本
* **老年代**收集器
* **Mark-Compact**算法

### 3.5.5 Parallel Old 收集器
Parallel Old 收集器: Parallel Old的老年代版本
* **老年代**收集器
* **Mark-Compact**算法
* 特点: 
    * 并行
* 可以与Parallel Scavenge配合

### 3.5.6 CMS收集器
CMS(Concurrent Mark Sweep)收集器: 以最短回收停顿时间为目标
*
