# chapter 03 HTTP报文

## 3.2 报文组成部分

起始行:
* 请求行
    * ```<method> <url-path> <version>```
* 响应行
    * ```<version> <status> <reason-phase>```
* 方法:
    * GET
    * POST: 有主体
    * PUT: 有主体
    * HEAD
    * TRACE: 追踪报文
    * DELETE
    * OPTIONS: 获取可行的操作
* 状态码:
    * 1xx: 信息提示
    * 2xx: 成功
    * 3xx: 重定向
    * 4xx: 客户端错误
    * 5xx: 服务器错误
* 版本号(HTTP/x.y):
    * 向后兼容
    * 比较(大于): x0 > x1 || (x0 == x1 && y0 > y1) 

### // 3.2.3 首部

首部:
* 通用首部
* 请求首部
* 响应首部
* 实体首部: 
    * Content-length
    * Content-type
* 扩展首部: http未定义(用户定义)

首部延续行: 首部可以分行, 多行的首部第二行起需要缩进(至少一个空格或缩进)

HTTP/0.9特点:
* 无首部
* 请求只有方法和URL
* 响应只有实体

## 3.3 方法

如果一个服务器要兼容HTTP/1.1, 那只要提供GET和HEAD方法

安全方法: head和get这两种不对资源进行修改的方法

有实体的方法: PUT, POST

方法语义:
* GET: 请求获取URL对应的资源
* HEAD: 与GET类似, 但服务器只发响应头和首部, 无实体
* PUT: 用户上传/更新URL对应的资源
* POST: 向服务器输入数据, 一般可用来提交表单
* TRACE: ?
    * 无实体
* OPTIONS: 请求服务器告知其支持的方法
    * 响应通过Allow首部来告知客户端支持的方法
  

## 3.4 状态码

1xx: 信息性状态码 (**HTTP/1.1特性**)
* 100 Continue: 请求客户端继续
    * 场景: 客户端发实体之前先问服务端是否接收实体
        * 请求
            * 客户端发 **Expect: 100 Continue**请求的前提是请求有实体
            * 客户端要做好被拒绝的准备, 如超时直接发送
        * 响应
            * 用100 Continue或者错误状态码来响应 Expect: 100 Continue请求, 永远不向非 100 Continue 请求发 100 Continue 响应
            * 如果服务端在决定发 100 Continue时已经收到实体, 则可以跳过 100 Continue 响应
            * 如果服务端决定拒绝 100 Continue请求 ?
        * 代理:
            * 代理收到客户端的 100 Continue 请求时
                * 如果知道**服务器不兼容HTTP/1.1**, 则直接向客户端响应 417 Expectation Failed
                * 否则(服务器兼容HTTP/1.1或者不知道), 向服务器发 100 Continue 请求
            * 代理替 HTTP/1.0 以下客户端发 100 Continue 请求时, 处理 100 Continue 响应时必须对用户透明


2xx: 成功状态码
* 200 OK
* 201 Created: 资源已被创建(如PUT请求等)
* 202 Accepted: 告知用户请求被接收, 但不一定处理
* 203 Non-Authoritative Information: 返回资源, 但资源可能并非来自源服务器
* 204 No Content: 没有实体
* 205 Reset Content: 告知浏览器清除HTML中的**表单内容**
* 206 Patial Content: ??

//3xx: 重定向状态码

//4xx: 

//5xx

## 3.5 首部
通用首部:
* Date: Tue, 23 Oct 2018 12:06:00 GMT
请求首部:
* Accept: */*
* Accept-Language: zh-CN
* Accept-Encoding: gzip
* Cookie
* Host
* User-Agent
响应首部:
* Server
实体首部: 描述实体的首部
* Content-Type: text/html
* Content-Length:1024
扩展首部: HTTP规范未定义, 用户定义

// 未完成 

