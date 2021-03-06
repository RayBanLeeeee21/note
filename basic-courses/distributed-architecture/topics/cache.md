## 缓存问题

缓存穿透: 用户不断请求不存在(不在定义域范围)的数据库, 使服务端不断访问数据库中是否存在
- 解决方法:
    - 从业务逻辑上过滤对于超出范围的值的访问
    - 将特殊值NULL也存到缓存
    - 布隆过滤器

缓存击穿: **单个**热点缓存数据过期的时候, 有很多请求在访问该值, 导致所有线程一起访问数据库
- 解决方法:
    - 热点数据永不过期
    - 用互斥锁来保证只有一个线程去数据库加载数据(**双检查锁实现**)


缓存雪崩: 缓存的数据在同一时间过期, 导致所有请求都去访问数据库
- 解决方法:
    - 热点数据永不过期
    - 分批过期(过期时间加随机偏差)
    - 将热点数据部署在不同的缓存数据库中

[布隆过滤器](https://www.cnblogs.com/liyulong1982/p/6013002.html): 通过k个hash函数将值映射到k个bit上, 置为1
- 特点: 布隆过滤器认为不存在的一定不存在, 认为存在的可能有误
- 优点:
    - 每个数据的存在与否都只用几bit存储
- 缺点:
    - 误报率随数据增加而增加
    - 无法删除
- m 和 k 的挑选公式(n 为数据量): $k=\frac{m}{n} \cdot ln(2)$


## 缓存一致性问题