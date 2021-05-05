# Chapter 17 TCP保活机制

相关参数:
- `net.ipv4.tcp_keepalive_time`: 默认7200s
- `net.ipv4.tcp_keepalive_probes`: 默认9
- `net.ipv4.tcp_keepalive_intvl`: 默认75s

保活机制: 当对端超过一段时间(keepalive_time)后无交互, 开始隔一段时间(keepalive_intvl)发一次保活请求, 直到发够一定次数(keepalive_probes)后就认为对端失去了连接
- 探测报文: 序号为对端上一个ACK-1, 以防影响后续报文
- 探测结果:
    1. 对方有响应: 重置保活时间值
        - 这一情况对应用程序来说是透明的
    2. 对方崩溃或网络不可达: 探测超时, 然后报Connection Timeout
    3. 对方重启造成端口已释放: 对方返回RST报文, 本端报Connection Reset