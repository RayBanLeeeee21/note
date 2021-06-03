# AbstractConnPool

## 相关类

`PoolEntry`: 带过期时间的连接池Entry
- 属性: 
    - id: Enntry ID
    - route: Entry键-路由信息
    - conn: Entry值-连接
    - updated: 更新时间
    - expiry: 下一次过期时间
    - isExpired(): expiry - updated
- 行为:
    - 关闭


`CPoolEntry`: 
- 属性:
    - routeComplete: 路由是否完成


`PoolStats`: 池状态
- `leased`: 租出的连接数
- `pending`: 待处理的请求数
- `available`: 可用连接数
- `max`: 最大可以有多少个连接


`CPool`: 连接池实现类, 继承了`AbstractConnPool`, 明确了类型参数T具体为`HttpRoute`


### RouteSpecificPool

`RouteSpecificPool`: 某个路由信息的池, 存了该路由信息的所有连接 (后续称为子连接池)
- 属性:
    - `route`: 路由信息
    - `LinkedList<E> available`: 可用的连接Entry
    - `LinkedList<Future<E>> pending`: 等待处理的请求(拿连接Entry的请求)
    - `Set<E> leased`: 已被借走的连接
    - `state`: 存的状态值
- 行为:
    - `updateExpiry()`: 更新超时时间
- 特点:
    - 该类未实现线程安全, 线程安全由`AbstractConnPool`保证

`getFree()`: 从available里找一个state匹配的返回, 找不到则返回一个state为空的
```java
    public E getFree(final Object state) {
        if (!this.available.isEmpty()) {
            if (state != null) {
                final Iterator<E> it = this.available.iterator();
                while (it.hasNext()) {
                    final E entry = it.next();
                    if (state.equals(entry.getState())) {
                        it.remove();
                        this.leased.add(entry);
                        return entry;
                    }
                }
            }
            final Iterator<E> it = this.available.iterator();
            while (it.hasNext()) {
                final E entry = it.next();
                if (entry.getState() == null) {
                    it.remove();
                    this.leased.add(entry);
                    return entry;
                }
            }
        }
        return null;
    }
```




## `AbstractConnPool`实现

`AbstractConnPool`: 抽象连接池
- 属性: 
    - `isShutDown`
    - `defaultMaxPerRoute`: 最多保存几个route的连接
    - `maxTotal`: 最大可保存几个连接
    - `validateAfterInactivity`: 拿连接失活后多久开始验证
- 工具: 
    - `lock`
    - `condition`
- 组件:
    - `ConnFactory`: 创建连接用
- 组合关系:
    - `Map routeToPool`: 不同route的连接
        - `Set lease`: 已出租的连接
        - `LinkedList available`: 可用连接 
        - `LinkedList pending`: 等处理请求 
        - `HashMap maxPerRoute`: 每个route最多几个连接 

#### shutdown()

```java
    public void shutdown() throws IOException {

        // 这里的compare & set不是原子的, 不安全, 要由外面保证安全
        if (this.isShutDown) return ;
        this.isShutDown = true;

        this.lock.lock();
        try {
            for (final E entry: this.available) {
                entry.close();
            }
            for (final E entry: this.leased) {
                entry.close();
            }
            for (final RouteSpecificPool<T, C, E> pool: this.routeToPool.values()) {
                pool.shutdown();
            }
            this.routeToPool.clear();
            this.leased.clear();
            this.available.clear();
        } finally {
            this.lock.unlock();
        }
    }
```

#### getPool()

总结: 无则创建, 有则get
```java
    private RouteSpecificPool<T, C, E> getPool(final T route) {
        RouteSpecificPool<T, C, E> pool = this.routeToPool.get(route);
        if (pool == null) {
            pool = new RouteSpecificPool<T, C, E>(route) {

                @Override
                protected E createEntry(final C conn) {
                    return AbstractConnPool.this.createEntry(route, conn);
                }

            };
            this.routeToPool.put(route, pool);
        }
        return pool;
    }
```


#### 租用 & 阻塞等待 & 释放

lease()
- 过程简述:
    1. 给future上锁 (防止其它线程也调用同一个future的get())
    2.  循环尝试
        1.  阻塞获取连接
        2.  检查连接是否失效, 未失效则返回
    3. 
- 源码
    ```java
    @Override
    public Future<E> lease(final T route, final Object state, final FutureCallback<E> callback) {
        Args.notNull(route, "Route");
        Asserts.check(!this.isShutDown, "Connection pool shut down");

        return new Future<E>() {

            private volatile boolean cancelled;
            private volatile boolean done;
            private volatile E entry;

            @Override
            public boolean cancel(final boolean mayInterruptIfRunning) {
                cancelled = true;
                lock.lock();
                try {
                    condition.signalAll();
                } finally {
                    lock.unlock();
                }
                synchronized (this) {
                    final boolean result = !done;
                    done = true;
                    if (callback != null) {
                        callback.cancelled();
                    }
                    return result;
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }

            @Override
            public boolean isDone() {
                return done;
            }

            @Override
            public E get() throws InterruptedException, ExecutionException {
                try {
                    return get(0L, TimeUnit.MILLISECONDS);
                } catch (TimeoutException ex) {
                    throw new ExecutionException(ex);
                }
            }

            @Override
            public E get(final long timeout, final TimeUnit tunit) throws InterruptedException, ExecutionException, TimeoutException {
                if (entry != null) {
                    return entry;
                }
                synchronized (this) {
                    try {

                        // 循环阻塞等待获取连接 (防止拿到的连接失效或者超时)
                        for (;;) {

                            final E leasedEntry = getPoolEntryBlocking(route, state, timeout, tunit, this);

                            // 拿到连接后做检查
                            //      如果设定了失活超时
                            //      则先判断是否失活超时
                            //      超时则需要判断连接是否有效
                            //      失效就要关闭连接并释放回Pool
                            if (validateAfterInactivity > 0)  { 
                                if (leasedEntry.getUpdated() + validateAfterInactivity <= System.currentTimeMillis()) {
                                    if (!validate(leasedEntry)) {
                                        leasedEntry.close();
                                        release(leasedEntry, false); // false表示不可再重用
                                        continue;
                                    }
                                }
                            }

                            // 拿到结果要存放到Future中
                            entry = leasedEntry;
                            done = true;
                            onLease(entry); // 钩子

                            // 执行成功回调
                            if (callback != null) {
                                callback.completed(entry);
                            }
                            return entry;
                        }
                    } catch (IOException ex) {

                        // 执行失败回调
                        done = true;
                        if (callback != null) {
                            callback.failed(ex);
                        }
                        throw new ExecutionException(ex);
                    }
                }
            }

        };
    }
    ```

getPoolEntryBlocking()
- 过程简述: 
    1. 对连接池上锁
    2. 循环尝试:
        1. 循环尝试从子池拿空闲连接 (循环是为了防止拿到超时关闭的)
        2. 拿不到则尝试创建连接
        3. 没有创建空间则入队, 阻塞等待其它线程释放连接, 并让出锁. 
        4. 被唤醒后, 出队, 判断未超时, 则进入下一轮循环继续
    3. 对连接池解锁
- 源码
    ```java

    /**
     *  循环{尝试获取已有连接->尝试创建连接->阻塞等待释放的过程}
     */
    private E getPoolEntryBlocking(
                final T route, final Object state,
                final long timeout, final TimeUnit tunit,
                final Future<E> future) throws IOException, InterruptedException, TimeoutException {

            Date deadline = null;
            if (timeout > 0) {
                deadline = new Date (System.currentTimeMillis() + tunit.toMillis(timeout));
            }

            // 锁住整个连接池
            this.lock.lock();
            try {

                final RouteSpecificPool<T, C, E> pool = getPool(route);
                E entry;
                for (;;) {
                    Asserts.check(!this.isShutDown, "Connection pool shut down");

                    // 循环从子池中找可用连接
                    for (;;) {
                        entry = pool.getFree(state);
                        if (entry == null) {
                            break;
                        }

                        // 如果过期了就关闭
                        if (entry.isExpired(System.currentTimeMillis())) {
                            entry.close();
                        }

                        // 关闭掉的要从子池的leased集中去掉, 并从连接池的avalable中去掉
                        if (entry.isClosed()) {
                            this.available.remove(entry);
                            pool.free(entry, false);
                        } else {
                            break;
                        }
                    }

                    // 获取成功, 则将连接从连接池的available挪到leased
                        // 子池在getFree()时已经挪过了
                    if (entry != null) {
                        this.available.remove(entry);
                        this.leased.add(entry);
                        onReuse(entry);     // 钩子
                        return entry;
                    }
                    // 前面未拿到, 说明需要创建新的连接
                    

                    // 检查有没有超出子池的连接数限制
                        // 如果超出, 则计算超出数excess, 从子池的available中取excess个最老的连接关掉
                    final int maxPerRoute = getMax(route);
                    final int excess = Math.max(0, pool.getAllocatedCount() + 1 - maxPerRoute);
                    if (excess > 0) {
                        for (int i = 0; i < excess; i++) {
                            final E lastUsed = pool.getLastUsed();
                            if (lastUsed == null) {
                                break;
                            }
                            lastUsed.close();
                            this.available.remove(lastUsed);
                            pool.remove(lastUsed);
                        }
                    }
                    

                    if (pool.getAllocatedCount() < maxPerRoute) {

                        // 检查有没超出连接池的连接数限制
                            // 要求: 租用数(totalUsed) + 空闲数(totalAvailable) + 1(新连接) <= maxTotal
                            // 否则要从连接池取一个清理掉
                        final int totalUsed = this.leased.size();
                        final int freeCapacity = Math.max(this.maxTotal - totalUsed, 0);
                        if (freeCapacity > 0) {
                            final int totalAvailable = this.available.size();   // available中肯定没有适用于route的连接, 否则从子池就拿到了
                            if (totalAvailable > freeCapacity - 1) {
                                if (!this.available.isEmpty()) {
                                    final E lastUsed = this.available.removeLast();
                                    lastUsed.close();
                                    final RouteSpecificPool<T, C, E> otherpool = getPool(lastUsed.getRoute());
                                    otherpool.remove(lastUsed);
                                }
                            }

                            // 清理后创建连接并返回
                            final C conn = this.connFactory.create(route);
                            entry = pool.add(conn);
                            this.leased.add(entry);
                            return entry;
                        }
                    }

                    // 既没拿到已有的连接, 又没法创建连接, 后面就只能等待
                    boolean success = false;
                    try {

                        // 休眠前检查是否取消, 取消则抛中断异常
                        if (future.isCancelled()) {
                            throw new InterruptedException("Operation interrupted");
                        }

                        // 入队
                        pool.queue(future);
                        this.pending.add(future);

                        // 超时等待, 等新的连接被释放, 并将连接池的锁让给其它线程
                            // 被唤醒后跳出try, 要检查一下超时
                        if (deadline != null) {
                            success = this.condition.awaitUntil(deadline);
                        } else {
                            this.condition.await(); 
                            success = true;
                        }

                        // 休眠后检查是否取消, 取消则抛中断异常
                        if (future.isCancelled()) {
                            throw new InterruptedException("Operation interrupted");
                        }
                    } finally {

                        // 出队
                        pool.unqueue(future);
                        this.pending.remove(future);
                    }
                    
                    // 如果到超时都还没成功, 就失败, 跳出去抛TimoutException
                    if (!success && (deadline != null && deadline.getTime() <= System.currentTimeMillis())) {
                        break;
                    }
                }
                throw new TimeoutException("Timeout waiting for connection");
            } finally {
                this.lock.unlock();
            }
        }
    ```



release()
1. 对连接池上锁
2. 将连接放回两层池子, 或者关闭
3. 通知其它线程有连接/空间释放出现
4. 对连接池解锁

```java
    @Override
    public void release(final E entry, final boolean reusable) {
        this.lock.lock();
        try {
            if (this.leased.remove(entry)) {

                // 放回对应的子池
                final RouteSpecificPool<T, C, E> pool = getPool(entry.getRoute());
                pool.free(entry, reusable);

                // 连接可重用 & 非shutdown -> 放回连接池
                    // else -> "关闭"连接
                if (reusable && !this.isShutDown) {
                    this.available.addFirst(entry);
                } else {
                    entry.close();
                }

                // 钩子
                onRelease(entry);

                // 释放完连接以后, 要通知其它future, 有连接可以用了
                Future<E> future = pool.nextPending();
                if (future != null) {
                    this.pending.remove(future);
                } else {
                    future = this.pending.poll();
                }
                if (future != null) {
                    this.condition.signalAll();
                }
            }
        } finally {
            this.lock.unlock();
        }
    }
```

