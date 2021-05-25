# Chapter 17 TCP保活机制

半开连接: 一方突然宕机导致未通知到另一方, 另一方一直以为连接还在
- 解决方法: 保活计时器


保活计时器
- *不属于TCP规范的一部分, 但大部分实现都有*


保活机制: 当对端超过一段时间(`keepalive_time`)后无交互, 开始隔一段时间(`keepalive_intvl`)发一次保活请求, 直到发够一定次数(`keepalive_probes`)无响应就认为对端失去了连接
- 探测报文: 序号为对端上一个ACK-1, 以防影响后续报文
- 探测结果处理:
    - 有响应: 重置保活时间和计数值
        - 这一情况对应用程序来说是透明的
    - 探测端报`Reset`: 
        1. 对方重启造成端口已释放, 返回RST报文
        2. 己方达到`keepalive_probes`后, 自己重置连接, 同时向对方发RST(不保证对方收到)

linux相关参数:
- `net.ipv4.tcp_keepalive_time`: 默认7200s(2h)
- `net.ipv4.tcp_keepalive_probes`: 默认9
- `net.ipv4.tcp_keepalive_intvl`: 默认75s


相关攻击:
- 欺骗攻击: 冒充被探测端返回ACK给探测端, 使其维护不必要的资源