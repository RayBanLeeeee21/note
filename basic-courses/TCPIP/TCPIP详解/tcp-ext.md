TCP中的四种计时器
- 重传计时器(Retransmission Timer)
- 持续计时器(Persistent Timer)
- 保活计时器(KeepAlive Timer)
- 时间等待计时器(Time_wait Timer)


Socket选项:
- `SO_KEEPALIVE`: 过完这段时间后开始发探测报文
    - 相关配置:
        - `net.ipv4.tcp_keepalive_time`: 过完这段时间后开始探测
        - `net.ipv4.tcp_keepalive_intvl`: 探测间隔
        - `net.ipv4.tcp_keepalive_probes`: 探测次数
- `SO_LINGER`: 决定关闭方式
    - `linger.l_onoff=0`: 关闭linger, 以四次挥手的方式关闭, 并且需要TIME_WAIT
    - `linger.l_onoff=1, linger.l_linger=0`: 直接重置
    - `linger.l_onoff=1, linger.l_linger>0`: 等待一段时间, 如果没发完数据就重置, 并且丢弃数据
- `SO_REUSEADDR`
    - 允许在上一个连接关闭后, 处于TIME_WAIT时再次建立连接
    - 允许服务端程序绑定到同一IP:PORT(UDP)
- `TCP_NODELAY`: 禁用Nagle算法

