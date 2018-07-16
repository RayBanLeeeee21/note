# Chapter 01 Java多线程技能

## 1.2 使用多线程

### // 1.2.1 继承Thread类

* 线程可以通过继承Thread类来实现 
* 执行start()的顺序不等于线程执行的实际顺序
    * start()的调用表示通知"线程规划器"线程来负责给定线程的执行
    * run()则是同步执行

### // 1.2.1 实现Runnable接口
* Runnable 避免了Thread类遇到的**Java只支持单根继承**的问题

