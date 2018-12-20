
## InputStream / OutputStream 

特点:
* read/write阻塞
* 处理单元为字节

抽象接口:



* InputStream 
    * 接口: Closable
    * 操作:
        *   ```Java 
            int available() throws IOException;     //要求覆写 
            long skip(long n) throws IOException;

            int read() throws IOException;          //强制实现
            int read(byte b[]) throws IOException;
            int read(byte b[], int off, int len) throws IOException;

            boolean markSupported();                        // 可选实现
            synchronized void mark(int readlimit);          // 可选实现
            synchronized void reset() throws IOException;   // 可选实现
            // Cloesable接口方法
            ```
* OutputStream
    * 接口: Closeable, Flushable
    * 操作:
        *   ```Java 
            void write() throws IOException;          //强制实现
            void write(byte b[]) throws IOException;
            void write(byte b[], int off, int len) throws IOException;
            // Cloesable接口方法
            // Flushable接口方法                
            ```
* 依赖接口
    *   ```Java 
        // Cloesable接口方法
            void close() throws IOException;
        // Flushable接口方法 
            void flush() throws IOException;               
        ```