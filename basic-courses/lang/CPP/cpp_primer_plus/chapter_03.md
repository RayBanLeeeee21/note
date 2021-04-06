# 3.1 命名空间的using声明

*[命名空间]*:
*   using用法:
    ```CPP
    using namespace std;
    using std::cout;
    ```
*  [命名空间] 头文件不应该包含using

# 3.2 标准库类型 string

*[string]*:
* 初始化: 
    ```CPP
    string s1 = "hiya"; //拷贝初始化
    string s2("hiya");  //直接初始化
    string s3(10, 'c'); //直接初始化
    ```
* 操作:
    ```CPP
    cin >> s1;          // 跳过' ', '\n'或'\t'
    getline(cin, s1);   // 只跳过'\n', '\t'与' '会被存到s1
    int a = s1.size();  // 忽略'\0'后的长度
    for (auto c: s1){   // 遍历
        cout << c << endl;
    }
    for (auto &c: s1){  // &表示引用
        c = 'a';
    }
    ```
* 
    * *[type]*s1.size()返回string::size_type为无符号整型数, 故不要与int混用
    * *[exp]* string的buffer大小为16的倍数
    * string与字符字面值或字符串字面值相加时, "+"两边至少要有一个string
    * **\***
        ```CPP
        #include <cctype>
        isalnum(c);
        isalpha(c);
        //...
        ```

## 3.3 标准库vector

*[vector]*: 
* 操作：
    ```CPP
    v1.push_back(a);
    ```
* *\**
    * [container] vector初始化时没必要设定大小

## 3.4 迭代器

*[iterator]*:
* 操作：
    ```CPP
    for (auto iter = s.begin(); iter!=s.end(); iter++){ //迭代器一般用!=来判断条件
        (*iter);
    }
    ```
* 迭代器运算
    * iter + n
    * iter - n
    * iter += n
    * iter -= n
    * iter1 - iter2 (差值为**different_type**类型)
    * \> \>= \< \<=
* const_iterator 与 iterator
    * iterator 可以读写
    * const_iterator只能读
    ```CPP
    string s1("abc");       auto iter1 = s1.begin(); // iterator
    const string s2("abc"); auto iter2 = s2.begin(); // const_iterator
    ```
* **\***
    * *[container]* 不能在使用迭代器的for循环内改变容器大小

## 3.5 数组
*[array]* 
* 初始化：
    * 在函数内部定义数组时，默认初始值不确定(debug版中为0xcccccccc)
    * 不能用auto定义
    * 字符数组可以用 char a[] = "abc"的形式初始化
    * **数据不能拷贝和赋值**
* 复杂数组声明：
    ```CPP
    int *ptr[10];  //大小为10的指针(int*)数组
    int (*ptr)[];  //指向大小为10的int数组的指针
    int *(*ptr)[]; //指向大小为10的(int*)数组的指针

    int &a[]10;    //错误， 不存在引用组成的数组
    int (&a)[10];  //对大小为10的int数组的引用
    ```
* **\***
    * 数组下标使用 **size_t** 类型 ，size_t被设计得够大以便能表示内存中任意对象的大小

*[pointer]* 
* 初始化：
    ```CPP
    string nums[] = {"1","2","3"};
    string *p1 = nums;
    string *p2 = &nums[0];
    auto p3(nums);
    ```
* **\***
    * 指针可以作为迭代器，注意尾后指针不能解引用
    * *[C++ 11]* begin和end函数可用来找数组的首元素指针和尾后元素指针
    * 指针的差的类型为 **ptrdiff_t**

*[string]* string 转 char* : const char * str = s.c_str();