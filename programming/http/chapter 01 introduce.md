# chapter 01 HTTP概述

* HTTP使用TCP协议实现(可靠性)


## 1.3 资源
Web资源 (保存在Web服务器中)
* 静态: 文本, html等
* 动态: 程序等

MIME (Multipurpose Internet Main Extension)
* e.g.
    * text/{html, plain}
    * image/{jpeg, gif}
    * video/{quicktime}
    * ...

## 1.4 事务
URI
* URI (Uniform Resource Identifier)
    * URN (Uniform Resource Name): 试验阶段
    * URL (Uniform Resource Locator)
        
URL
* 组成
    * 方案: http
    * 服务器地址: {域名, IP}:80
    * 资源
* 方法: get, post, put, delete, head, trace, options
* 状态码: 200, 302, 404...


## 1.5 报文 
报文:
* 编码: 纯文本
* 组成:
    * 起始行
    * 首部
    * 主体

request报文:
* 起始行:
    * 方法: GET, POST等
    * 资源路径: /tools.html
    * 协议版本: HTTP/1.0, HTTP/1.1
* 首部:
    * Host: www.joes-hardware.com
    * Accept: text/html, text/plain, image/gif
    * Accept-language: en, zh
    * User-agent
* (主体): 只有**POST**和**PUT**方法有主体

response报文
* 起始行
    * 协议版本: HTTP/1.0
    * 状态码: 200
    * 原因短语: OK
* 首部:
    * Content-type: text/html
    * Content-length: 100
    * Server
    * Date
    * Last-modified

## 1.6 连接

基本连接步骤:
1. 从URL中解析出主机名
2. 域名解析成IP
3. 有端口号的话, 从URL解析端口号, 否则默认80
4. 建立TCP连接
5. 客户端发request报文
6. 服务器回response报文
7. 断开连接

## Web的结构组件

代理(HTTP代理服务器): 处于客户端和服务器之间, 代表用户访问服务器, 并可以对请求和响应进行过滤
缓存: 特殊的HTTP代理服务器, 对常用文档保存起来
网关: 负责协议的转换, 如HTTP转FTP等
HTTP隧道: 在HTTP连接上转发非HTTP数据, 如SSL
* SSL(Secure Socket Layer)
用户Agent代理: 代表用户发起HTTP请求的客户端程序, 如Web浏览器
* Web浏览器
* 爬虫