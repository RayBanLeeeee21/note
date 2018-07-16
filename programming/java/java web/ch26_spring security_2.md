## 26.1选择配置认证提供者
* spring security通过一系列过滤器实现

spring security 内建AuthenticationProvider实现
* com.springframework.security.authentication.dao.DaoAuthenticationProvider
    * 靠UserDetailsService从数据库获取UserDetials对象
    * UserDetails保存用户名, 密码, GrantedAuthority, 用户是否被启用, 过期或锁定

* InMemoryUserDetailsManager


### 26.1.1 配置 DaoAuthenticationProvider

先验
* Spring的ServletContainerIntitializer负责发现所有的WebApplicationInitializer并实例化它们

spring security 配置
* java配置: 继承 AbstractSecurityWebApplicationInitializer
* xml配置: 添加security过滤器链
    *   ```xml
        <filter>
            <filter-name>springSecurityFilterChain</filter-name>
            <filter-class>
                org.springframework.web.filter.DelegatingFilterProxy
            <filter-class>
        </filter>
        ```
