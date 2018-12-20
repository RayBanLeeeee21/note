## 13.2

### <c>13.2.1<c/> Java语言中的线程安全
Java操作共享数据的类型:
* 不可变:
    ```Java
    final
    String 
    java.lang.Number
    BigInteger, BigDecimal
    ```
* 绝对线程安全: 无
* 相对线程安全 / 线程安全:
    ```Java
    Vector
    HashTabable
    ```
* 线程兼容: 对象本身不是线程安全, 但是可以通过调用端来实现线程安全
* 线程对立: 无论调用端是否采取措施, 都无法在多线程环境中并发使用
    ```Java
    Thread.suspend()
    Thread.resume()
    ```

### <c>13.2.2<c/> 线程安全的实现方法
