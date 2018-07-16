### 25.1.2 了解授权

java 规范:
* java.security.Principal: 存在安全上下文中
    * 持有用户标识,
    * 存储用户被授权执行的操作

spring security 基础
接口: org.springframework.security.core
* Authentication: 
    * 扩展principal:
        * getIdentity: 用户标识
        * isAuthenticated: 
        * getCredentials
        * getAuthority:
            * 返回GrantedAuthority: 角色/活动
* GramtedAuthority:
    * 用户权限
* AuthenticationProvider: 认证服务提供者
    * 认证或者拒绝认证等

授权方法:
* 全局方法安全注解
* 定义方法拦截规则
* 定义URL拦截规则(不推荐)

