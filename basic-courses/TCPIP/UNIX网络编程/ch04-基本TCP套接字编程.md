# Chapter 04 基本TCP套接字编程

原语:
- socket
- bind
- connect
- listen
- accept


socket
```cpp
int socket(         // 返回值: socket文件描述符
    int family,     // 地址族
    int type,       // socket类型: SOCK_STREAM | SOCK_DGRAM | SOCK_SEQPACKET | SOCK_RAW
    int protocol    // 协议: IPPROTO_TCP / IPPROTO_UDP / IPPROTO_SCTP
);
```

connect
```cpp
int connect(            // 0: 正常; ETIMEDOUT: 连接超时; ECONNREFUSED: 拒绝连接; 
                        //         EHOSTUNREACH | ENETUNREACH: 不可达
    int sockfd,         // socket函数返回的fd
    const struct sockaddr *servaddr,
                        // socket地址
    socklen_t addrlen   // 地址长度
);
```

bind
```cpp
int bind(
    int sockfd,
    const struct sockaddr * myaddr,
    socklen_t addrlen
);
```

listen: 使服务端从CLOSED变成ESTABLISHED状态
```cpp
int listen(         // 
    int sockfd, 
    int backlog     
);
```

参考:
- [TCP/IP协议中backlog参数](https://www.cnblogs.com/Orgliny/p/5780796.html)
- [浅谈tcp socket的backlog参数](https://blog.csdn.net/qq_16399991/article/details/109389060)

队列:
- `net.ipv4.tcp_max_sync_backlog`: SYN队列长度
    - 默认1000
- `net.ipv4.somaxconn`: 处于`ESTABLISH`状态, 但未被`accept()`的队列
    - 默认128. 实际采用的长度为`min(backlog, somaxconn)` (backlog为listen()参数中传的)

accept
```cpp
int accept(                   // 已连接 socket fd
    int sockfd,               // 监听 socket fd
    struct sockaddr *cliaddr, // "值-结果"参数, 客户端地址. 可以为空指针
    socklen_t *addrlen        // "值-结果"参数.            可以为空指针
)
```
