# chapter 02 URL与资源


## 2.1-2.3
URL组件: 
* 方案: http
* 用户名: lee
* 密码: password
* 主机: www.joes-hardware.com
* 端口: 80
* 路径: /default;a=1/index.html
    * 参数(每个**路径段**都可以有): ;a=1
* 查询: ?b=1
    * 查询字符串以&分隔
* 片段: #tool
    * 浏览器获得整个页面以后, 将片段展示给用户
* e.g.:
    * http://lee:password@www.joes-hardware.com/default;a=1/index.html?b=1#tool\

相对URL
* 基础URL:
    * 由\<base\>定义
    * 由当前资源路径决定

## 2.4 字符转义
略

## 2.5 方案
方案:
* http: 
    * 无用户名/密码
    *   ```http://<host>:<port>/<path>?<query>#<frag>```
* https:
    * 使用了SSL
    * 默认端口为443
    *   ```http://<host>:<port>/<path>?<query>#<frag>```
* ftp:
    *   ```ftp://<user>:<password>@<host>:<port>/<path>;<params>```