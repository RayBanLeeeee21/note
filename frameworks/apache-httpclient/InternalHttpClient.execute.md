## Request 

继承关系(自上而下): 
1. `HttpGet`
2. `HttpRequestBase`
3. `AbstractExecutionAwareRequest`
4. `AbstractHttpMessage`

`AbstractHttpMessage` & `HttpMessage`: 
- 属性:
    - HttpHeader
    - Http参数(不可变)(Deprecated)

`AbstractExecutionAwareRequest`:
- 带取消(Cancel)后回调的Request


`HttpRequestBase`:
- 属性:
    - 协议版本号
    - URI
    - RequestConfig
    - HTTP 方法

## HttpHost

HttpHost
- 属性:
    - schema
    - hostname
    - ip
    - port

## HttpRoute

HttpRoute: 请求路由
- 属性:
    - `targetHoset`
    - `localAddress`
    - `List<HttpHost>proxyChain`: 代理服务器链
    - `TunnelType`
        - PLAIN: 拿host或第一个proxy建立连接
        - TUNNELLED: Tunnelled routes are established by connecting to the first proxy and tunnelling through all proxies to the target ???
    - `LayerType`
    - `secure`: 是否支持SSL

## InternalHttpClient.execute()

`InternalHttpClient.execute()`
```java
    /**
     *  该方法转换必要参数, 传给调用链执行. 参数包括:
     *  - 路由信息
     *  - wrapper: 将请求包成wrapper
     *  - Http上下文
     *  - 回调
     */ 
    @Override
    protected CloseableHttpResponse doExecute(
            final HttpHost target,
            final HttpRequest request,
            final HttpContext context) throws IOException, ClientProtocolException {
        Args.notNull(request, "HTTP request");

        // 检查有没回调
        HttpExecutionAware execAware = null;
        if (request instanceof HttpExecutionAware) {
            execAware = (HttpExecutionAware) request;
        }

        try {

            // 用warper包装原来的请求, 防止修改原请求
            final HttpRequestWrapper wrapper = HttpRequestWrapper.wrap(request, target);

            // 创建Http上下文
            final HttpClientContext localcontext = HttpClientContext.adapt(
                    context != null ? context : new BasicHttpContext());

            // 取请求配置, 并做一些转换
            RequestConfig config = null;
            if (request instanceof Configurable) {
                config = ((Configurable) request).getConfig();
            }
            if (config == null) {
                final HttpParams params = request.getParams();
                if (params instanceof HttpParamsNames) {
                    if (!((HttpParamsNames) params).getNames().isEmpty()) {
                        config = HttpClientParamConfig.getRequestConfig(params, this.defaultConfig);
                    }
                } else {
                    config = HttpClientParamConfig.getRequestConfig(params, this.defaultConfig);
                }
            }

            // 将请求配置加入到Http上下文
            if (config != null) {
                localcontext.setRequestConfig(config);
            }

            // 初始化Http上下文的一些默认参数
            setupContext(localcontext);

            // 解析目标(从url解析host)
            final HttpRoute route = determineRoute(target, wrapper, localcontext);

            // 丢到执行执行链中执行
            return this.execChain.execute(
                route,          // 路由
                wrapper,        // 请求
                localcontext,   // Http上下文
                execAware       // 执行回调
            );
        } catch (final HttpException httpException) {
            throw new ClientProtocolException(httpException);
        }
    }
```

## 执行器链

1. RedirectExec: 实现重定向
2. RedirectExec: 实现重试


## RedirectExec

1. 重定向执行器
2. 重试执行器
3. 协议执行器

`RedirectExec`: 实现重定向功能
- 参考`DefaultRedirectStrategy`
- 参考HTTP权威指南中的**重定向功能**
- 该执行器只关注跟重定向相关的header

`RetryExec`: 实现重试
- 重试条件:
    - 次数未达上限
    - 不包含某些不可尝试条件的异常
    - 请求未中止
    - 请求幂等
    - 其它

`ProtocolExec`: 
- 根据Route信息重写URI, 重新确定target
- 用拦截器链(`ImmutableHttpProcessor`)处理请求和响应
- 用`MainClientExec`发送

`MainClientExec`: 实现通信功能
- 组件:
    - HttpRequestExecutor
    - HttpClientConnectionManager
    - proxyHttpProcessor
    - HttpAuthenticator
    - UserTokenHandler
    - HttpRouteDirector 
- 属性:
    - 连接重用策略
    - 连接保活(keepAlive)策略
    - target认证策略
    - proxy认证策略

