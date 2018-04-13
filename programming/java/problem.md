* main函数是否一定要为static:
    * JVM会查找类中的public static void main(String[] args)，如果找不到该方法就抛出错误NoSuchMethodError:main 程序终止