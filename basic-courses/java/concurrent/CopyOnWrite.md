## CopyOnWrite
特点:
* 读读/读改(写增删)不互斥, 改改互斥

方法:
* get
    ```java
    // 如果get的时候表长度已变, 直接通过ArrayOutOfBoundException通知调用者
    // 因此调用时需要注意捕捉ArrayOutOfBoundException
    private E get(Object[] a, int index) {
        return (E) a[index];
    }

    public E get(int index) {
        return get(getArray(), index);
    }
    ```
* set
    ```java
    public E set(int index, E element) {
        // 上锁
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            // 取表
            Object[] elements = getArray();
            E oldValue = get(elements, index);

            //如果新值与旧值不是同一个对象则更新, 然后替换旧表
            if (oldValue != element) {
                int len = elements.length;
                Object[] newElements = Arrays.copyOf(elements, len);
                newElements[index] = element;
                setArray(newElements);
            } else {
                // Not quite a no-op; ensures volatile write semantics
                setArray(elements);
            }
            return oldValue;
        } finally {
            lock.unlock();
        }
    }
    ```
* add
    ```java
    public boolean add(E e) {
        // 上锁
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            // 取表
            Object[] elements = getArray();
            int len = elements.length;
            // 将旧表的元素与新元素加到新表
            Object[] newElements = Arrays.copyOf(elements, len + 1);
            newElements[len] = e;
            // 替换旧表
            setArray(newElements);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void add(int index, E element) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            if (index > len || index < 0)
                throw new IndexOutOfBoundsException("Index: "+index+
                                                    ", Size: "+len);
            Object[] newElements;
            int numMoved = len - index;
            if (numMoved == 0)
                newElements = Arrays.copyOf(elements, len + 1);
            else {
                newElements = new Object[len + 1];
                System.arraycopy(elements, 0, newElements, 0, index);
                System.arraycopy(elements, index, newElements, index + 1,
                                 numMoved);
            }
            newElements[index] = element;
            setArray(newElements);
        } finally {
            lock.unlock();
        }
    }
    ```
* remove
    ```java
    /**
        按index查找时, 直接上锁即可
    */
    public E remove(int index) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            Object[] elements = getArray();
            int len = elements.length;
            E oldValue = get(elements, index);
            int numMoved = len - index - 1;
            if (numMoved == 0)
                setArray(Arrays.copyOf(elements, len - 1));
            else {
                Object[] newElements = new Object[len - 1];
                System.arraycopy(elements, 0, newElements, 0, index);
                System.arraycopy(elements, index + 1, newElements, index,
                                 numMoved);
                setArray(newElements);
            }
            return oldValue;
        } finally {
            lock.unlock();
        }
    }


    /**
        按object查找时要先检查对象是否不存在, 不存在才上锁
    */
    public boolean remove(Object o) {
        // 快速判断是否不存在, 不存在则不用上锁, 直接返回
        Object[] snapshot = getArray();
        int index = indexOf(o, snapshot, 0, snapshot.length);
        // 否则上锁重新查找
        return (index < 0) ? false : remove(o, snapshot, index);
    }

    private boolean remove(Object o, Object[] snapshot, int index) {
        // 上锁
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            // 上锁后表可能被更新, 如果已经更新,那要在新表中重新查找一遍
            Object[] current = getArray();
            int len = current.length;
            if (snapshot != current) findIndex: {
                int prefix = Math.min(index, len);
                for (int i = 0; i < prefix; i++) {
                    if (current[i] != snapshot[i] && eq(o, current[i])) {
                        index = i;
                        break findIndex;
                    }
                }
                if (index >= len)
                    return false;
                if (current[index] == o)
                    break findIndex;
                index = indexOf(o, current, index, len);
                if (index < 0)
                    return false;
            }
            // 删除object, 并替换表
            Object[] newElements = new Object[len - 1];
            System.arraycopy(current, 0, newElements, 0, index);
            System.arraycopy(current, index + 1,
                             newElements, index,
                             len - index - 1);
            setArray(newElements);
            return true;
        } finally {
            lock.unlock();
        }
    }
    ```