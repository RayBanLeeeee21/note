接口:
* java.lang.Readable: 
    * int read(java.nio.CharBuffer cb) throws IOException;
* Closable:
    * void close() throws IOException;
* Appenadble:
    * Appendable append(CharSequence cs) throws IOException;
    * Appendable append(CharSequence cs, int start, int end) throws IOException;
    * Appendable append(char c) throws IOException;
* Flushable:
    * Flushable 接口方法
* CharSequence:
    * int length();
    * char charAt(int index);
    * CharSequence subSequence(int start, int end);
    * String toString();
    * IntStream chars();        // [未看]
    * IntStream codePoints();   // [未看]
    * **CharSequence未定义equals与hashCode, 不能用在set或者map的key**
    * 只读


Reader (抽象)
* 接口: Readable, Closable
* 功能:
    * 核心:
        * int read() throws IOException;// 返回单个char
        * int read(char cbuf[]) throws IOException;
        * **abstract** int read(char cbuf[], int off, int len) throws IOException;
    * 其它:
        * int skip(long) //skipBuffer尺寸为8192, 超过则要分段skip
        * 不支持mark
* 特点:
    * 带锁(阻塞)
    * skipBuffer尺寸为8192, 超过则要分段skip
    * 与InputStream区别: 为处理Unicode而设计

Writer (抽象)
* 接口: Appenable, Closable, Flushable
* 功能:
    * 核心:
        * void write(int c) 
        * void write(char cbuf[])
        * **abstract** void write(char cbuf[], int off, int len)
        * void write(String str)
        * void write(String str, int off, int len) //writeBuffer超出尺寸时临时分配缓存
    * 其它:
        * Appendable接口方法
* 特点:
    * 带锁(阻塞)
    * writeBuffer超出尺寸时临时分配缓存
    * 与OutputStream区别: 为处理Unicode而设计

InputStreamReader
* 继承: Reader    
* 功能:
    * 作用: byte stream 转 character stream
    * 可选解码
    * 可定解码器 (java.nio.charset.CharsetDecoder)
* 特点:
    * 建议用BufferedReader包装提高效率
    * **代理模式**实现: 目标对象为StreamDecoder


OuputStreamWriter
* 继承: Writer 
* 功能:
    * 作用: character stream 转 byte stream
    * 可选编码
    * 可定编码器 (java.nio.charset.CharsetEncoder)
* 特点:
    * 建议用BufferWriter包装提高效率
    * **代理模式**实现: 目标对象为StreamEncoder

PrintWriter
* 继承: Writer
* 功能:
    * AutoFlush
    * checkError 
    * print等
    * println等
    * printf等 (format实现)
    * format等
* 特点:
    * 格式化输出
    * 除构造器外不抛异常. ensureOpen抛异常后都被捕捉, 用户通过checkError检查, 但不能清除错误标志
    * 分行符与系统相关
    * AutoFlush动作由write()触发 (PrintStream中由换行符触发)
    * 输出到文件时, AutoFlush功能关闭
    * 构造器参数类型与处理:
        * Writer: 直接赋给out
        * OutputStream: BufferedWriter -> OutputStreamWriter -> OutPutStream
        * File/fileName: BufferedWriter -> OutputStreamWriter -> FileOutputStream
            // OutputStream 实际被包装成BufferedWriter

FileReader
* 继承: InputStreamReader
* 特点:
    * 作用: 以char为单位读文件
    * 实现: super(new FileInputStream(file)); // 连接FileInputStream与InputStreamReader
