## chapter 07

继承关系:
* Throwable
    * Error
    * Exception
        * IOException
        * RuntimeException
        
Error
* VirtualMachineError
    * OutOfMemoryError
    * StackOverflowError

IOException:
* EOFException
* FileNotFoundException

RuntimeException
* ArrithmeticException
* ClassNotFoundException
* NullPointerException
* ArrayIndexOutOfBoundsException
* ClassCastException


异常类型:
* 非受查异常: 编译期不检查这类异常的throws声明/catch块
    * Error: 系统的内部错误
    * RuntimeException
* 受查异常: 需要用户声明throws/catch块的异常


子类覆写父类方法时 
* 可以不抛出异常(自己捕捉)
* 不能抛出父类声明的异常的超类
* 不能抛出超出父类声明的异常
    * e.g
    ```java
    // ...
    AClass aclazz = new SubClass();
    aclass.throwAnException();      // 此处编译器只检查EOFException而不检查IOException
    //...

    public static class AClass {
		public void throwAnException() throws EOFException{
			throw new EOFException();
		}
	}

	public static class SubClass extends AClass{
		@Override
		public void throwAnException() throws IOException{
			throw new FileNotFoundException();
		};
	}
    ```

异常抑制:
* **try块**抛出的异常可能被**finally块**中的异常抑制, 并且会**丢失**
* **try(resource)块**抛出的异常会抑制**resource.close()抛出的异常**, 但可以找到被抑制的异常
