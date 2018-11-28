# chapter 11 客户端识别与cookie机制

HTTP是**无状态**的请求/响应协议

用户识别机制:
* 承载用户信息的HTTP首部
* IP地址跟踪
* 用户登录认证
* 胖URL: 在URL中嵌入识别信息
* cookie


## 11.2 HTTP首部
基于首部识别的方法:
* 相关首部:
    * From: 用户E-mail地址
    * User-agent: 包括 **浏览器信息 (程序名称, 版本)** 及 **操作系统相关信息**
    * Referer: 用户从此页面跳转过来的
    * Authentization: 用户名与密码
    * Client-IP (扩展): 
    * X-Forwarded-For (扩展)
    * Cookie (扩展)
* From, User-agent, Referer不能实现可靠的识别

## 12.3 客户端IP地址
IP地址获取方法:
* 承载HTTP请求的TCP连接的IP数据报
* Clent-IP首部 (扩展)

无法识别的场景:
* 同一个机器上的多个用户
* 动态IP
* NAT转换
* 服务器实际连接到代理 (可通过Client-IP克服这个问题)

IP地址识别只适合在内网中使用

## 11.4 用户登录

相关首部:
* WWW-authenticate
* Authentization

过程:
1. 用户在不知情的情况下直接发送请求
2. 服务器回复**401 Login Required**响应, 并通过**WWW-Authenticate**首部说明需要的信息
3. 浏览器中提示登录框
4. 用户发带有**Authentization首部**的请求说明身份
5. 服务器返回资源
6. 后续的请求都带有Authentization首部

e.g.:
* 响应:
    ```http
    HTTP/1.1 401 Login Required
    WWW-Authenticate: Basic realm="Plumbing and Fixtures"
    ```
* 请求
    ```http
    GET /index.html HTTP/1.1
    Host: www.joes-hardware.com
    Authentization: Basic am910jRmdW4=
    ```

缺点: 不安全

## 11.5 胖URL
  
缺点: 
* URL复杂
* 复制时容易泄露个人信息: 
    * 比如一个用户粘贴URL给另一个人, 然后让他人无意登录他的主页
* 破坏缓存: 无法生成共享的URL缓存 (每个人都不同)
* 服务器负荷: 要在html中生成胖URL
* 逃逸口: 用户容易逃离胖URL会话
* 会话间非持久: 如果用户收藏的不是胖URL, 那再次访问时需要重新登录


## 11.6 cookie

cookie分类:
* 会话cookie: 关闭浏览器就清除
* 持久cookie: 关闭浏览后, 仍持久化在硬盘中

会话cookie与持久cookie区别:
* 会话cookie在浏览器关闭后就清除
* 会话cookie寿命短, 持久cookie寿命长
* 会话cookie带有**Discard首部**, 或者没有**Max-Age**或**Expires**首部

Set-Cookie: 告诉用户将cookie的某个key设为特定的值
* 请求中可带有多个Set-Cookie首部
* 除**键值对**外, 还有cookie的相关属性(path, domain等), 以";"分隔
* e.g.: Set-Cookie: id=1; domain=".baidu.com"

Cookie首部:
* Cookie首部可带多个键值对, 以";"分隔
* 请求中可以有多个Cookie首部

保存Cookie时的相关属性(Navigator):
* name
* value
* domain: cookie的域
* allh: 是否域中所有主机可访问 (TRUE/FALSE)
* path: 与cookie相关的资源路径
* secure: 是否在用SSL连接时使用
* expiration: 过期秒数
* ...

浏览器只向特定服务器发送其产生的cookie
* 发送太多降低性能
* 对其它服务器没用
* 发太多泄露隐私


Set-Cookie首部的属性:
* expires = Wednesday, 09-Nov-99 23:12:40 GMT
* domain = www.baidu.com
* path = /index
* secure : 只有在使用SSL时才发送cookie, 该属性没有值

处理缓存的建议
* 如果无法缓存, 需要标示出来
    * Cache-Control: no-cache="Set-Cookie"  表示不缓存Set-Cookie首部
    * Cache-Control: public 表示可缓存3

(未完成)