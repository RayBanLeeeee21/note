

Closable:
* 操作:
    * void close() throws IOException;
* 特点:
    * 用来释放资源
    * 对于已关闭资源, close()操作不做任何操作

Flushable:
* 操作:
    * void flush() throws IOException;


java.lang.Appenadble:
* 操作:
    * Appendable append(CharSequence cs) throws IOException;
    * Appendable append(CharSequence cs, int start, int end) throws IOException;
    * Appendable append(char c) throws IOException;
* 特点:
    * 针对char
    * 可操作char和CharSequence
    * 线程安全由实现类保证


java.lang.Readable: 
* 操作:
    * int read(java.nio.CharBuffer cb) throws IOException;
* 特点:
    * 针对char
    * 可操作java.nio.CharBuffer


java.lang.CharSequence:
* 操作:
    * int length();
    * char charAt(int index);
    * CharSequence subSequence(int start, int end);
    * String toString();
    * IntStream chars();        // [未看]
    * IntStream codePoints();   // [未看]
* 特点:
    * **CharSequence未定义equals与hashCode, 不能用在set或者map的key**
    * 只读