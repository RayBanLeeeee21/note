# Chapter 06 代理

代理: 处于服务器与客户端之间, 使用HTTP协议(可能会进行版本的转换等)
网关: 进行协议的转换

代理的应用:
* 儿童过滤器
* 文档访问控制: **集中式代理服务器**上可以对所有访问控制功能进行配置
* 安全防火墙
* Web缓存
* 反向代理
* 内容路由器
* 转码器
* 匿名者: 在对客户端与服务器都透明的情况下, 从HTTP报文中删除身份特性

代理部署位置
* 出口代理: 放在LAN的出口处, 控制LAN与Internet的流量.
    * 提供针对公司外部恶意黑客的防火墙
    * 充分利用网络带宽, 降低通信费用
* 访问(入口)代理: 放在ISP(Internet Service Provider)访问点上
    * 可使用缓存来提高用户的下载速度
* 反向代理: 作为Web的替代物(对用户透明)
    * 提高安全性
    * 可放在慢速服务器前, 用缓存来提高速度
* 网络交换代理: 放在Internet对等交换点
    * 利用缓存减少拥塞

代理层级结构: 靠近服务器的为**父级(上级)**代理, 靠近客户端的为**子级(下级)**代理

动态选择代理:
* 负载均衡: 子代理通过父代理的工作负载级别来选择一个父代理
* 地理位置附近的代理
* 协议/类型路由: 某些特定Accept首部的请求发给特定的父代理处理
* 基于订阅的路由: 服务发布者为高性能服务付费了, 它们的URI会被转到大型缓存或压缩引擎中, 以提高性能

HTTP流量流向代理的方法:
* 客户端手工配置
* 修改网络: 用可以监视HTTP流量的交换设备和路由设备, 对HTTP请求进行拦截
* 修改DNS: 让域名对应代理的IP地址
* 修改Web服务器: Web服务器回复重定向响应, 重定向到代理

## 6.5 代理的相关问题

发送给代理的URI: 
* 完整URI: 对于客户端可见的代理, 客户端发送的HTTP请求头中要包含完整的URI
* Host首部: 对于一个物理主机上的多个虚拟主机Web服务器, 会要求用户的请求中带有Host首部
* 部分URI: 反向代理和拦截代理对于客户端来说都是透明的, 可以接收部分URI

通用的代理应当对**显式的代理请求**提供**完整URI**的支持, 对**Web服务器请求**提供**部分URI**或者**Host首部**的支持
* 处理逻辑:
    * 如果是完整URI, 优先使用URI中的主机
    * 否则如果有Host首部, 则使用Host首部
    * 否则:
        * 对于反向代理, 使用主机的IP:端口来确定
        * 对于拦截代理, 
            * 如果有主机的IP:端口信息, 则通过IP:端口来确定
            * 否则返回错误报文

代理应该尽可能少对URI进行修改

### 6.6.1 Via首部

Via首部:
* 部件: **路标**, 以逗号分隔
    * [协议 /] 版本 主机[:端口] [描述性注释] // 括号中为可选项
* e.g.: 
    * Via: 1.1 proxy-62.irenes-isp.net, 1.0 cache.joes-hardware.com
    * Via: FTP/1.0 proxy.irenes-isp.net (Traffic-Server/5.0.1-17882 [cMs f ])
    * Via: 1.1 cache.joes-hardware.com, 1.1 proxy.irenes-isp.net

Via首部功能:
* 记录转发路径
* 标记请求/响应链上的协议
* **诊断路由循环**: 在Via首部中插入特定与自身相关的信息, 在收到的请求的Via首部检查首部是否有该信息

Via的请求和响应路径: 一般请求路径与响应的路径是相同的, 而**请求和响应的Via首部中路标的顺序相反**

Via和网关: 网关可以通过Via首部来记录协议转换的过程

**Server首部**: Server首部用来记录原始服务器, 代理不可修改

Via的隐私安全问题: 可能泄露防火墙后面主机的拓扑结构
* 代理可以用假名来代替主机名
* 对于需要隐藏内部拓扑结构的组织, 可以以整个组织为整体作为一个主机, 用一个路标 (要求协议一致)

Trace方法与Via
* e.g.
    * 请求
    ```http
    TRACE /index.html HTTP/1.1
    Host: www.joes-hardware.com
    Accept: text/html
    Via: 1.1 proxy.irenes-isp.net, 1.1 p1127.att.net, 1.1 cache.joes-hardware.com
    X-Magic-CDN-Thingy: 134-AF-0003
    Cookie: access-isp="Irene's ISP, California"
    Client-ip: 209.134.49.32
    ```
    响应
    ```http
    HTTP/1.1 200 OK
    Content-Type: message/http
    Content-Length:269
    Via: 1.1 cache.joes-hadware.com, 1.1 p1127.att.net, 1.1 proxy.irenes-isp.net
    ============================================================================
    TRACE /index.html HTTP/1.1
    Host: www.joes-hardware.com
    Accept: text/html
    Via: 1.1 proxy.irenes-isp.net, 1.1 p1127.att.net, 1.1 cache.joes-hardware.com
    X-Magic-CDN-Thingy: 134-AF-0003
    Cookie: access-isp="Irene's ISP, California"
    Client-ip: 209.134.49.32
    ============================================================================
    ```

Max-Forward首部: 记录最大跳数
* 针对**TRACE**或**OPTIONS**请求
* **代理**和**网关**都支持
* 每一跳后, 都要减1
* 只要收到请求的Max-Forward为0, 不管接收者是不是原始服务器, 都要回复响应报文

代理认证:
* 机制: 
    * 需要认证时, 代理响应**407 Proxy Authentization Required**状态码
    * 响应中带有Proxy-Authenticate首部, 说明认证方法
    * 用户则发带有Proxy-Authentization首部的请求, 认证通过则往下传递
* 问题: 多级代理认证不能正常工作
* e.g.
    * 响应
    ```http
    HTTP/1.1 407 Proxy Authentization Required
    Proxy-Authenticate: Basic realm="Secure Stuff"
    ```
    * 请求
    ```http
    GET http://server.com/secret.jpg HTTP/1.1
    Proxy-Authentization: Basic YnJpOmZvbw== 
    ```

**扩展首部**: 对于带有不支持首部的请求, 代理应该尽可能转发