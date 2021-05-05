# Chapter 13 TCP连接管理


TIME_WAIT状态
- SO_REUSEADDR:
    - 对于TCP
        - 即使前一个socket处于TIME_WAIT状态, 依然可以开同一个端口给这一socket
        - 允许多个进程的socket绑定到同一端口(但IP必须不同)
        - 允许同一进程的多个socket绑定到同一端口(但IP必须不同)
    - 对于UDP
        - 多播时允许多个socket绑定到同一个ip:port(相当于SO_REUSEPORT)

2MSL作用:
- 以防被动关闭端未收到主动端的最后一个ack. 
    - 在2MSL重复收到被动关闭端的FIN时, 可以重发最后一个ACK
- 以防下一个socket建立之前, 当前socket的被动关闭端在关闭之前发过来的报文都过期掉


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
        2. `l_onoff!=0, l_linger=0`:


半开连接: 一方宕机后, 另一方以为还在连接状态, 直到向另一方发消息被RST
- 解决方法: 心跳    

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

SYN洪泛
- SYN cookie: SYN请求到达时, 先不分配内存, 通过4元组等信息生成cookie存在初始序号中. 收到ACK时验证cookie成功再分配内存

序列号攻击-TCP劫持: 在创建连接时向其中一方发送使其状态发生错乱的报文, 使两边失去同步, 然后再开始向其中一方注入新的流量

欺骗攻击-RST:
- 解决方法:
    - 要求RST报文要有特殊的序号
    - 要求时间戳有特定的数值
    - 使用其它形式的cookie

