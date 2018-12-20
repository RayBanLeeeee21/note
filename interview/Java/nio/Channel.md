## ServerSocketChannel

父类/父接口
* Channel
    * 行为特点:
        * 可关闭
        * 可判断是否被关闭
    * src
        ```java
        public interface Channel extends Closeable {
            public boolean isOpen();
            public void close() throws IOException;
        }
        ```
* NetworkChannel
    * 属性:
        * SocketAddress
        * SocketOption
    * src
    ```java
    public interface NetworkChannel
        extends Channel {
        NetworkChannel bind(SocketAddress local) throws IOException;
        SocketAddress getLocalAddress() throws IOException;

        <T> NetworkChannel setOption(SocketOption<T> name, T value) throws IOException;
        <T> T getOption(SocketOption<T> name) throws IOException;
        Set<SocketOption<?>> supportedOptions();
    }
    ```