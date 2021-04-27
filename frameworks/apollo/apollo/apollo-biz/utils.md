# utils
ConfigChangeContentBuilder: 
* 生成一个Gson, 包含以下三种Item更新集合(List)及其更新时间:
    * createItems集合
    * updateItems集合: 同时有旧值与新值
    * deleteItems集合
* 用例:
    ```java
    ConfigChangeContentBuilder builder = new ConfigChangeContentBuilder();
    builder.updateItem(itemA);
    builder.deleteItem(itemB);
    builder.createItem(itemC);
    builder.build();
    ```
EntityManagerUtil: ???

ReleaseKeyGenerator: 
* 生成Release版本Key, 包含信息:
    * **时间戳**
    * appId
    * cluster
    * namespace
    * **HASH(机器IP(as int)+计数)**

ReleaseMessageKeyGenerator:
* 生成Release Message Key, 包含信息:
    * appId
    * cluster
    * namespace 
