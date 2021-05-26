# Chapter 13 TCP连接管理


三次握手/四次挥手
- Q: 为什么要三次握手(而不是二次)
    - A: 
        - 建立连接时, 双方都必须确认以下2个条件. 只有收到对方的ACK时, 才能确认并进入`ESTABLISHED`
            1. 条件1-自己的报文能发给对方 
            2. 条件2-对方能收到自己的报文并返回
        - **SYN攻击**: 如果服务端收到SYN直接进入`ESTABLISHED`, 就会建立很多连接, 占用很多资源, 甚至无法对正常的客户端提供服务
- Q: 为什么要四次挥手


同时打开
<br/>

同时关闭
<br/>

### 13.3 TCP选项

选项: 
- 标志: 选项标志
- 长度: 包括整个选项(即`标志+长度+值`). 
    - `EOL`(0)和`NOP`(1)只有一个标志字段, 没有长度和值
    - 其它标志通过长度来决定
- 值: 取决于选项

#### 13.3.1 最大段选项

MSS(Max Segment Size): 最大报文段
- 结构(伪代码): 
    ```cpp
    typedef struct {
        uint8_t flag = 2;
        uint8_t length = 4; // 整个结构大小为4byte
        uint16_t mss;
    } MssOpt;
    ```
- 取值:
    - 典型值-1460: 加上TCP头部和IPv4头部后刚好达到链路层MTU(1500)
    - 典型值-534: 加上TCP头部和IPv4头部后为576 byte, 是所有主机都必须支持的IP数据报大小
    - 特殊值-65536: 与IPv6超长数据报一起表示不限大小
- 与窗口大小的区别: 窗口大小反映的是缓冲区大小, MSS指一个报文的数据部分的最大长度

#### 13.3.2 选择确认选项

SACK
- 结构(伪代码): 
    ```cpp
    typedef struct {
        uint8_t flag = 3;
        uint8_t length;             // length = (sack块个数 * 8 + 2) byte
        struct SackBlock * blocks;  // sack块数组
    } SackOpt;

    typedef struct {                // 长度为8 byte
        uint32_t from;
        uint32_t to;
    } SackBlock;
    ```

#### 13.3.3 窗口缩放选项

WSCALE/WSOPT
- 

#### 13.2.3 初始序列号:

初始序列号:
- 问题:
    - 如果不是随机
        - 当前连接可能会被上一次关掉的4元组相同的连接的重复报文影响
        - 容易受到欺骗攻击
- 生成方法: 半随机(随机+hash)
    - linux: 
        - 前8位: 保密的序列号, 会随时间增加
        - 后24位: 对时钟和4元组做加密hash: `hash(time, ipPort1, ipPort2)`
            - 也起到SYN-cookie的作用



#### 13.5.2 TIME_WAIT状态

`TIME_WAIT`状态
- `TIME_WAIT`(2MSL)作用:
    - 以防被动关闭端未收到主动端的最后一个ack. 
        - 在2MSL重复收到被动关闭端的FIN时, 可以重发最后一个ACK
    - 以防下一个socket建立之前, 当前socket的被动关闭端在关闭之前发过来的报文都过期掉
- 静默时间(MSL): 刚启动后, 创建连接必须等待MSL, 以防止上次宕机时有连接处于`TIME_WAIT`


`SO_REUSEADDR`选项:
- 对于TCP
    - 即使前一个socket处于TIME_WAIT状态, 依然可以开同一个端口给这一socket
    - 允许多个进程的socket绑定到同一端口(但IP必须不同)
    - 允许同一进程的多个socket绑定到同一端口(但IP必须不同)
- 对于UDP
    - 多播时允许多个socket绑定到同一个ip:port(相当于SO_REUSEPORT)




RST的作用
- 拒绝连接: 客户端连到一个未开启的端口时, 服务端机器返回RST报文
    - RST报文也要带ACK序号, 并且在窗口范围内, 以防RST攻击
- 终止释放(SO_LINGER)
    - SO_LINGER:
        ```cpp
        struct linger{
            int l_onoff;
            int l_linger;
        }
        ```
        1. `l_onoff=0`: 四次挥手关闭
        2. `l_onoff!=0, l_linger=0`: 立即RST关闭
        3. `l_onoff!=0, l_linger>0`: 在指定时间内发送完成则四次挥手, 否则RST
            - 时间单位取决于实现, linux中为`s`


半开连接: 一方宕机后, 另一方以为还在连接状态, 直到向另一方发消息被RST
- 解决方法: 保活计时器

# 13.7.4 进入连接队列
参考:
- [TCP/IP协议中backlog参数](https://www.cnblogs.com/Orgliny/p/5780796.html)
- [浅谈tcp socket的backlog参数](https://blog.csdn.net/qq_16399991/article/details/109389060)

队列:
- `net.ipv4.tcp_max_sync_backlog`: SYN队列长度
    - 默认1000
- `net.ipv4.somaxconn`: 处于`ESTABLISH`状态, 但未被`accept()`的队列
    - 默认128. 实际采用的长度为`min(backlog, somaxconn)` (backlog为listen()参数中传的)



## 13.8 与TCP连接管理相关的攻击

**SYN洪泛**: 攻击方以不同的IP:port发大量SYN请求
- SYN cookie: SYN请求到达时, 先不分配存储资源, 通过4元组等信息生成cookie存在初始序号中. 收到ACK后, 通过序号获取cookie, 验证成功再分配存储资源

序列号攻击-TCP劫持: 在创建连接时向其中一方发送使其状态发生错乱的报文, 使两边失去同步, 然后再开始向其中一方注入新的流量

欺骗攻击-RST:
- 解决方法:
    - 要求RST报文要有特殊的序号
    - 要求时间戳有特定的数值
    - 使用其它形式的cookie

