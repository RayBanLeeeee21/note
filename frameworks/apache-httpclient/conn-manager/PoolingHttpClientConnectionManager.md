# PoolingHttpClientConnectionManager

先验知识:
[AbstractConnPool](./AbstractConnPool.md)

## 相关类

头注释:
- 设计Connection Manager的目的
    - 作为HTTP连接工厂
    - 管理持久
    - 同步对于持有连接的访问请求, 保证一次只有一个线程使用一个连接
- 必须是线程安全的


`HttpClientConnectionManager`

## PoolingHttpClientConnectionManager实现


`PoolingHttpClientConnectionManager`
- 属性:
    - configData: 所有配置
    - isShutdown: 是否关机
- 组件: 
    - cpool: 连接池
    - connectionOperator: 