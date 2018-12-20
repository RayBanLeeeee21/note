定义
* 存储过程SP: 一组由SQL服务器直接存储和执行的定制过程或函数
* 触发器: 在insert / update / delete 命令**之前**/**之后**对SQL命令或者SP的自动调用

存储过程
* 优点: 
    * 速度快
    * 安全
    * 减少代码冗余度
* 缺点:
    * 移植性差: 不同数据库的SP可能不兼容

触发器:
* 语法:
    * 
    ```sql
    create trigger `triggerName` before insert on `user` for each row [sp];
    create trigger `triggerName` before update on `user` for each row [sp];
    create trigger `triggerName` before delete on `user` for each row [sp];
    create trigger `triggerName` after  insert on `user` for each row [sp];
    create trigger `triggerName` after  update on `user` for each row [sp];
    create trigger `triggerName` after  delete on `user` for each row [sp]; 
    ```
    * e.g.
    ```
    delimiter $$
    create trigger `t1`
    before insert
    on `user` for each row 
    begin
        set NEW.age = NEW.age+1;
    end$$
    delimiter ;
    ```
* OLD和NEW
    * OLD不能被写, 只能在 after/before update/delete时被读取
    * NEW只能在before insert/update时被写, 在before/after insert/update时被读