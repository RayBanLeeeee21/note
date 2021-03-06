# chapter 04 TCP

## 4.1

Web浏览器与Web服务器连接:
1. 从URL中解析主机名
2. 通过DNS协议解析主机IP地址
3. 从URL解析端口
4. 与服务器建立TCP连接
5. 客户端发送HTTP请求
6. 服务器发送HTTP响应
7. 关闭连接

HTTPS在HTTP与TCP之间插入一个TSL/SSL层

### 4.1.3 TCP连接
TCP连接由<源IP, 源端口, 目的IP, 目的端口>来唯一地标识

## 4.2 对TCP性能的考虑

HTTP事务的时延:
* DNS解析时间: 大概十几秒, 但缓存机制可以加快效率
* 连接时间: 在多个HTTP事务并发时, 时延会增加
* 请求发送时间
* 服务器处理时间
* 响应发送时间

具体时延:
* TCP三次握手连接: 解决方法: 重用现存连接
* TCP慢启动
* 延迟确认: TCP在发送的数据中捎带之前的ACK; 收到多个分组时才回一个ACK
* Nagle: 当每个分组数据比较少时, 先把数据缓存起来, 直到收到下一个ACK或者超时才发送
    * 超时等待会造成时延
    * 可通过设置参数TCP_NODELAY来禁止Nagle算法, 但要保证大多数情况下是大分组
    * 可能与延迟确认发生冲突, 造成死锁(发送端等待ACK, 而接收端等待分组)
* TIME_WAIT累积与端口耗尽:
    * 关闭发起端会在2MSL的时间内保存刚关闭的IP地址与端口号, 此时源端口不可用
    * 通常不会有问题, 但在做**基准测试**时有影响
        * 一台客户端与一台服务器做测试时, 连接率不超过 可用端口总数/2MSL
        * 可通过**增加测试客户端**, **虚拟IP地址**的方法提高连接率


**逐跳首部**: 逐跳首部不能被转发, 只属于这一跳
* Connection
* Proxy-Authenticate
* Proxy-Connection
* Transfer-Encoding
* Upgrade
* ...

Connection首部:
* Connection在HTTP/1.1中是**逐跳首部**, 不是**端到端首部**
* 连接标签:
    * close: 收到该响应后, 关闭连接. HTTP/1.0的默认选项, HTTP/1.1需显式使用
    * keep-alive: 该响应后继续保持连接. HTTP/1.0中3显式使用, HTTP/1.1中默认选项
    * 首部: 指出属于这一跳的首部. web应用程序发给下一跳时, 要将这一跳Connection中的首部删掉


### 4.3.2 串行事务处理时延
串行HTTP事务处理: 每个HTTP事务(连接-请求-响应组合)都单独, 串行处理
* 缺点: 
    * 连接时延与慢启动时间都叠加起来
    * html加载完成时, 图片等资源还没加载好
    * 有些浏览器无法知道对象(图片等)的尺寸, 而页面的布局与对象的尺寸相关
* 解决方法:
    * 并行连接: 允许服务器打开多个连接, 并行加载资源
    * 持久连接: 在一个事务处理完不立即关闭TCP连接, 而是等到客户端/服务器发 Connection: close 请求/响应
    * 管道化连接: 

并行连接
* 优点: 
    * 在带宽足够的前提下, 时延可重叠, 提高加载速度 
    * 充分利用带宽
    * 对象可以同时加载出来, 与串行处理相比, 提升用户体验
        * **渐进式图片加载**: 先加载主要轮廓, 再加载细节部分, 提升用户体验
* 缺点:
    * 在带宽不够的时候, 造成连接之间对带宽的竞争, 反而降低速度
    * 多个连接占用更多内存资源, 限制服务器的性能
  
持久连接
* 原理: **站点局部性**: 对一个服务器发出了第一个请求的客户端可能在后续继续发请求
* 优点:
    * TCP连接重用, 减少建立连接和关闭连接的开销
* 缺点:
    * 可能造成空闲连接, 甚至耗尽端口资源

Keep-Alive首部
* 只有在存在 **Connection: Keep-Alive** 时能使用
* 参数:
    * timeout: 估计的最大连接时间, 但不能保证
    * max: 估计的最大事务数, 但不能保证

HTTP/1.0的Connection相关规则:
* Keep-Alive不是Connection的默认值, 只要请求/响应中没有Connection: Keep-Alive首部, 下个响应之前连接就会关闭
* 只有在无需检测到连接关闭就能确定实体长度的前提下, 可以使用Keep-Alive
    * 要有正常的Content-Lenght与Content-Type, 或者用分块传输编码的方式实现了编码
* 代理和网关应当在下一跳时删除这一跳中的Connection和Keep-Alive首部
* 严格来说, 不应当和不确定是否支持Keep-Alive的代理建立Keep-Alive连接, 防止哑代理
* 从技术上说, 对于有Connection首部的HTTP/1.1请求都应当忽略Connection首部, 因为可能不支持Keep-Alive
* 如果请求是幂等的, 客户端要做好在未完全接收实体时被关闭, 然后重复发送请求的准备

哑代理: 代理不支持Connection: Keep-Alive却盲目转发, 导致代理认为连接已关闭, 而客户端和服务器还在等待
* 解决方法: 
    * Proxy-Connection首部: 
        * 支持Keep-Alive的代理收到Proxy-Connection首部时, 用Connection代替, 
        * 哑代理则直接转发Proxy-Connection
        * 服务端收到Proxy-Connection时知道是哑代理转发的, 直接忽略
    * 新问题: 哑代理与支持Keep-Alive的代理同时存在时, 依然会有哑代理的问题

HTTP/1.1持久连接
* 默认持久连接, 用Connection: close来显式表示关闭连接, 认为无Connection首部的都是持久连接
* 带有Connection: close的一定会关闭, 但不带Connection不代表一定不会关闭, 可能因为其它原因(如长度不匹配), 导致关闭
* 只有实体部分与Content-Length匹配时才能持久连接
* **HTTP/1.1代表服务器不应该与HTTP/1.0客户端连接**
* HTTP/1.1任何时候可以关闭连接, 但对于幂等的请求, 请求方都要做好连接被关闭后重发请求的准备
* 一个客户端最多和一个服务器/代理连接两个持久连接, 防止过载

## 4.6 管道化连接

持久化连接消除**TCP连接时延**, 管道化连接消除**传输时延**

管道化连接的规则:
* 前提是必须支持持久连接
* 响应的顺序必须与请求的顺序一致, 失序后无法匹配
* 请求方要做好连接被关闭时, 重发幂等的请求的准备
* 非幂等的请求(如POST)不能用管道化连接, 因为无法重发

Content-Length与截尾操作
* 客户端或代理收到一个响应的实体与Content-Length不匹配或者无Content-Length时, 应该质疑其长度
* 接收端为缓存代理时, 不应当缓存长度不匹配的响应, 而是原封不动地转发