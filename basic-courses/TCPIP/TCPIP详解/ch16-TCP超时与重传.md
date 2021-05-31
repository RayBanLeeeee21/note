# Chapter 16 超时与重传

重传相关配置:
- `net.ipv4.tcp_retries1`: 默认3. 达到该阈值后, 要向IP层传递"消极建议"(重新评估路径等).
- `net.ipv4.tcp_retries2`: 默认15. 达到该阈值后, 断开连接.
- `net.ipv4.tcp_syn_retiries`: 重发SYN的重试次数
- `net.ipv4.tcp_synack_retiries`: 重发SYN-ACK的重试次数


RTT估计方法:
- 经典方法-指数加权移动平均(EWMA)/衰减均值
    - $\overline{RTT} \leftarrow \alpha \cdot \overline{RTT}  + (1 - \alpha) \cdot RTT_s$
    - $RTO = min(ubound, max(lbound, \overline{RTT} \cdot \beta))$
- 标准方法-考虑平均偏差的EWMA
    - $\overline{RTT} \leftarrow \alpha \cdot \overline{RTT}  + (1 - \alpha) \cdot RTT_s$
    - $\overline{V_{_{rtt}}} \leftarrow \alpha \cdot \overline{V_{_{rtt}}}  + (1 - \beta) \cdot |RTT_s - \overline{RTT}|$
    - $RTO = \overline{RTT} + 4 \cdot \overline{V_{_{rtt}}}$

#### 14.3.2.3 重传二义性与Karn算法

Karn算法:
- 二义性问题: 发生重传时, 无法确定ACK回复哪一次
- 基本思想
    1. 重传报文的采样值不能用来估计RTO
    2. 不能简单将重传时的采样值丢弃, 否则会损失有用信息
- 做法:
    1. 发生重传时不使用采样值来更新RTO
    2. **二进制指数退避**: 将之前的RTO乘以一个退避系数(2, 4, 8...), 作为当前的RTO

#### 14.3.2.4 带时间戳选项的RTT测量

TSOPT
- 数据结构参考[上一章](ch13-TCP连接管理.md#1334时间戳选项与防回绕序列号)
- *如何避免二义性问题?*
    - 由接收端保证:
        - 记录自己上一次的ACK
        - 发送端下个报文到达时, 如果SEQ与上一个ACK一致, 则可以读取TSOPT.tsVar, 复制到TSOPT.tsEcr后返回
- 参数: `net.ipv4.tcp_timestamps`

## 14.5 快速重传

当接收端连续dupthresh次重复ACK同一个序号时, 发送端直接重发指定的部分
- [SACK选项](ch13-TCP连接管理.md#1332选择确认选项): 可以指明要重发的部分
- 同时会触发拥塞控制机制中的**快恢复**