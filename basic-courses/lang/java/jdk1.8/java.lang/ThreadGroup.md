# ThreadGroup

ThreadGroup: 一组具有共同点的线程

属性
- namn: 命名
- parent: 
    - 父级Group, 最高层为null
- maxPriority: 组内最大优先级. 组内线程不能超过这个值
- destroyed: 是否已销毁. 销毁后以下行为会报`IllegalThreadStateException`
    - 添加新线程
    - 添加子线程组
    - 重复销毁
- daemon: 当组内线程都执行完后, group自动销毁
    - 如果再添加会报`IllegalThreadStateException`
