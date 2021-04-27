本文档主要作为学习apollo时的辅助, 为apollo的一些关键功能的代码实现添加注释, 方便阅读理解.

目录:
* 启动apollo:
    * [apollo项目调试启动过程&踩坑记录](./apollo/apollo-project-startup.md)
* 配置实时更新
    * [apollo-biz模块: DatabaseMessageSender与ReleaseMessageScanner](./apollo/apollo-biz/message.md)
    * [apollo-adminservice模块: 灰度规则实时更新](./apollo/apollo-adminservice/grayReleaseRule.GrayReleaseRulesHolder.md)
* configservice端配置获取
    * [apollo-configservice模块: ConfigController](./apollo/apollo-configservice/controller.ConfigController.md)
    * [apollo-configservice模块: ConfigService系列-从不同数据中心获取](./apollo/apollo-configservice/controller.ConfigService系列.md)
* Long Poll (重点)
    * [apollo-configservice模块: NotificationControllerV2-接收用户长轮询请求](./apollo/apollo-configservice/controller.NotificationControllerV2.md)
    * [apollo-client模块: RemoteConfigLongPollService-长轮询](./apollo/apollo-client/internals/RemoteConfigLongPollService.md)
* client端配置获取
    * [apollo-client模块: ConfigRepository-提供config&通知config更新](./apollo/apollo-client/internals/ConfigRepository.md)
    * [apollo-client模块: RemoteConfigRepository-从configservice远程获取config](./apollo/apollo-client/internals/RemoteConfigRepository.md)
    * [apollo-client模块: LocalConfigFileRepository-fallback机制实现](./apollo/apollo-client/internals/LocalConfigFileRepository.md)
    * [apollo-client模块: Config系列-从ConfigRepository获取config&多优先级properties提供](./apollo/apollo-client/internals/Config系列.md)
    * [apollo-client模块: ConfigService-Config及ConfigRepository管理](./apollo/apollo-client/ConfigService.md)
* 业务逻辑
    * [apollo-adminservice模块: Controllers](./apollo/apollo-adminservice/controllers.md)