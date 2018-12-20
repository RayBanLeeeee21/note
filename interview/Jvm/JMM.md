MESI协议:
* 可共存状态:
    * I+E/S
    * I+M
* 状态转移:
    * Modified: 缓存行与内存不一致, 数据独占
        * Local Read: 从本缓存行读数据, 状态不变
        * Local Write: 向本缓存行写数据, 状态不变
        * Remote Read: 在其它缓存行读之前写入内存, 与其它缓存行一起变成S
        * Remote Writer: 在其它缓存行写之前写入内存, 写的缓存行变为E, 其它S/本缓存行变I
    * Exclusive: 缓存行与内存一致, 数据独占
        * Local Read: 从本缓存行读, 状态不变
        * Local Write: 写入本缓存行, 状态变为M
        * Remote Read: 与其它缓存行一起变成S
        * Remote Write: 本缓存行变为I, 写缓存行变为E
    * Shared:
        * Local Read: 从本缓存行读, 状态不变
        * Local Write: 写入本缓存行, 状态变为M; 其它S缓存行变为I
        * Remote Read: 状态不变
        * Remote Write: 状态变为I
    * Invalid:
        * Local Read: 从内存读入数据, 并写入缓存行, 状态变成E;
            * 如有M缓存行, 先触发其写入内存, M缓存行变为S
            * 如有E/S缓存行, E/S缓存行与本缓存行变为S
        * Local Write: 从内存读入数据, 然后将处理器数据写入缓存, 状态变成M
            * 如有M缓存行, 先触发其写入内存, M缓存行变成I
            * 如有E/S缓存行, 则E/S缓存行变成I
        * Remote read: 与自己无关
        * Remote write: 与自己无关




