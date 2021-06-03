## Paxos

- Paxos
    - [分布式算法：Paxos及Multi-Paxos](https://zhuanlan.zhihu.com/p/341122718)
    - [分布式系统Paxos算法](https://www.jdon.com/artichect/paxos.html)
    - [Paxos算法详解](https://zhuanlan.zhihu.com/p/31780743)
    - [Paxos共识算法详解](https://segmentfault.com/a/1190000018844326)
    - [半小时学会什么是分布式一致性算法——Paxos](https://blog.csdn.net/westbrookliu/article/details/99713365)


### Basic-Paxos
1. Prepare阶段
    a. Proposer - 发起 
    -   ```py
        preResults = broadcast(prepare(n, val)) # 发起广播
        ```
    b. Acceptors - 确认
    -   ```py
        if n > minProposal
            minProposal = n                             # 承诺不再接受 <= n 的提议
            return (acceptedProposal, acceptedValue)    # 返回自己知道的最新值
        else 
            return reject(minProposal)                  # 拒绝并告知Proposar最近的proposal
        ```
2. Accept阶段
    a. Proposer - 通知
    -   ```py
        if preResults.size() >= ceil(nodeCount/2)
            val = maxAcceptedValueOrElse(preResults, value)  # 只有当acceptor返回的
            broadcast(accept(n, val))                        # 广播Accept消息
        else 
            retry()                                          # 竞争失败重试
        ```
    b. Acceptors - 确认
    -   ```py
        if (n >= minProposal)
            acceptedProposal = minProposal = n
            acceptedValue = val
            return minProposal
        ```
    c. Proposer - 接收
    -   ```py
        if (acceptedCount < ceil(nodeCount/2))
            retry()                                         # 失败重试
        ```

## Raft

- Raft
    - [分布式系统的Raft算法](https://www.jdon.com/artichect/raft.html)
    - [寻找一种易于理解的一致性算法](https://github.com/maemual/raft-zh_cn/blob/master/raft-zh_cn.md)
    - [深入剖析共识性算法 Raft](https://mp.weixin.qq.com/s/GhI7RYBdsrqlkU9o9CLEAg)
    - [Raft算法详解](https://zhuanlan.zhihu.com/p/32052223)
    - [图解Raft：应该是最容易理解的分布式一致性算法](https://www.jianshu.com/p/5b25b019eebb)
    - [CoreOS 实战：剖析 etcd](https://www.infoq.cn/article/coreos-analyse-etcd/)
- ZAB
    - 
- [实例详解ZooKeeper ZAB协议、分布式锁与领导选举](https://dbaplus.cn/news-141-1875-1.html)
- [Zab协议详解](https://blog.csdn.net/liuchang19950703/article/details/111406622)