方法：
* *[Collection]*: 
    * int size();
    * boolean isEmpty();
    * boolean contains(Object o);
    * Iterator<E> iterator(); 
    * Object[] toArray();
    * <T> T[] toArray(T[] a);
    * boolean add(E e);
    * boolean remove(Object o);
    * boolean containsAll(Collection<?> c);
    * boolean addAll(Collection<? extends E> c);
    * boolean removeAll(Collection<?> c);
    * default boolean removeIf(Predicate<? super E> filter);
    * boolean retainAll(Collection<?> c);
    * void clear();
    * default Spliterator<E> spliterator();
    * default Stream<E> stream();
    * default Stream<E> parallelStream();


* *[Queue]*
    * boolean add(E e);   *[Collection]*
    * boolean offer(E e); // 类似add
    * 
    * E remove(); // remove(and retrieve), Exception *[Collection]*
    * E poll();   // remove(and retrieve), remove, null
    * 
    * E element;  // retrieve, Exception
    * E peek();   // retrieve, null

* *[Deque]*
    * void addFirst(E e);
    * void addLast(E e);
    * 
    * boolean offerFirst(E e);
    * boolean offerLast(E e);
    * 
    * E removeFirst();
    * E removeLast();
    * 
    * E pollFirst();
    * E pollLast();
    * 
    * E getFirst();
    * E getLast();
    * 
    * E peekFirst();
    * E peekLast();
    * //*for queue*
    * boolean removeFirstOccurrence(Object o);
    * boolean removeLastOccurrence(Object o);
    * // for queue
    * boolean add(E e);     // addLast *[Queue, Collection]*
    * boolean offer(E e);   // offerLast *[Queue]*
    * 
    * E remove(); // removeFirst *[Queue, Collection]*
    * E poll();   // pollFirst   *[Queue]*
    * 
    * E element;  // getFirst    *[Queue]*
    * E peek();   // peekFirst   *[Queue]*
    * // *for stack*
    * void push(E e);   //addFirst
    * E pop();          //removeFirst