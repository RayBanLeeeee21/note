# InheritableThreadLocal

参考
- [ThreadLocal](./ThreadLocal.md)
- [Thread](./Thread.md)

`InheritableThreadLocal`
- 作用: 该类继承自`ThreadLocal`
    - 子线程创建时, 会把父线程存的`inheritableThreadLocals`都复制一份, 放到`inheritableThreadLocals`中
    - 子线程不对某个`InheritableThreadLocal`做修改直接访问的话, 就访问到父线程设置的值
    - 子线程可以对`InheritableThreadLocal`的值做修改, 修改完对其它子线程或者父线程均不可见


## 实现

- 线程`inheritableThreadLocals`初始化
    ```java
        private void init(ThreadGroup g, Runnable target, String name,
                        long stackSize, AccessControlContext acc,
                        boolean inheritThreadLocals) {
            // ...

            // 把父线程的 inheritableThreadLocals 复制一份, 作为自己的
            if (inheritThreadLocals && parent.inheritableThreadLocals != null)
                this.inheritableThreadLocals =
                    ThreadLocal.createInheritedMap(parent.inheritableThreadLocals);

            // ...
        }

        /**  
            ThreadLocal.createInheritedMap(ThreadLocalMap parentMap) 
        */
        static ThreadLocalMap createInheritedMap(ThreadLocalMap parentMap) {
            return new ThreadLocalMap(parentMap);
        }

        // 遍历 parentMap 的结点, 复制到this.table
        private ThreadLocalMap(ThreadLocalMap parentMap) {
            Entry[] parentTable = parentMap.table;
            int len = parentTable.length;
            setThreshold(len);
            table = new Entry[len];

            for (int j = 0; j < len; j++) {
                Entry e = parentTable[j];
                if (e != null) {
                    @SuppressWarnings("unchecked")
                    ThreadLocal<Object> key = (ThreadLocal<Object>) e.get();
                    if (key != null) {
                        Object value = key.childValue(e.value);     // 初始为父线程设置的值
                        Entry c = new Entry(key, value);
                        int h = key.threadLocalHashCode & (len - 1);
                        while (table[h] != null)
                            h = nextIndex(h, len);
                        table[h] = c;
                        size++;
                    }
                }
            }
        }

        /**  
        InheritableThreadLocal.childValue(T parentValue)
        可以通过定义子类自定义这个方法, 根据父线程的值去初始化子线程的值
        */
        protected T childValue(T parentValue) {
            return parentValue;
        }
    ```

- set()
    ```java
        /**
            直接用ThreadLocal.set()
        */
        public void set(T value) {
            Thread t = Thread.currentThread();
            ThreadLocalMap map = getMap(t);     // 这里被覆写, 改成用 t.inheritableThreadLocals
            if (map != null)
                map.set(this, value);
            else
                createMap(t, value);
        }

        /**
            InheritableThreadLocal.getMap(Thread t)
        */
        ThreadLocalMap getMap(Thread t) {
        return t.inheritableThreadLocals;
        }
    ```
    `map.set()`参考[ThreadLocal](./ThreadLocal.md), 这里不赘述

- get()
    ```java
        /**
            直接用ThreadLocal.get()
        */
        public T get() {
            Thread t = Thread.currentThread();
            ThreadLocalMap map = getMap(t);     // 这里被覆写, 改成用 t.inheritableThreadLocals
            if (map != null) {
                ThreadLocalMap.Entry e = map.getEntry(this);
                if (e != null) {
                    @SuppressWarnings("unchecked")
                    T result = (T)e.value;
                    return result;
                }
            }
            return setInitialValue();
        }
    ```