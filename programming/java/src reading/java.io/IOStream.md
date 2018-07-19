InputStream (抽象)
* 接口: Closable
* 功能:
    * 核心:
        * **abstract** int read() throws IOException;// 返回单个char, 以int的形式返回(-1表示读到尾部)
        * int read(byte b[]) throws IOException;
        * int read(byte b[], int off, int len) throws IOException;
    * 其它:
        * int skip(long) //skipBuffer尺寸为2048, 超过则要分段skip
        * 不支持mark
* 特点:
    * skipBuffer尺寸为2048, 超过则要分段skip

OutputStream (抽象)
* 接口: Closeable, Flushable
* 功能:
    * 核心:
        * **abstract** void write() throws IOException; 
        * void write(byte b[], int off, int len) throws IOException;
        * void write(byte b[]) throws IOException;
    * 其它
        * Closeable接口方法
        * Flushable接口方法