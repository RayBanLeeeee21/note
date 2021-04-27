本文档主要作为学习apollo时的辅助, 为apollo的一些关键功能的代码实现添加注释, 方便阅读理解.

目录:
* 启动apollo:
    * [apollo项目调试启动过程&踩坑记录](./apollo/apollo-project-startup.html)
* 配置实时更新
    * [apollo-biz模块: DatabaseMessageSender与ReleaseMessageScanner](./apollo/apollo-biz/message.html)
    * [apollo-adminservice模块: 灰度规则实时更新](./apollo/apollo-adminservice/grayReleaseRule.GrayReleaseRulesHolder.html)
* configservice端配置获取
    * [apollo-configservice模块: ConfigController](./apollo/apollo-configservice/controller.ConfigController.html)
    * [apollo-configservice模块: ConfigService系列-从不同数据中心获取](./apollo/apollo-configservice/controller.ConfigService系列.html)
* Long Poll (重点)
    * [apollo-configservice模块: NotificationControllerV2-接收用户长轮询请求](./apollo/apollo-configservice/controller.NotificationControllerV2.html)
    * [apollo-client模块: RemoteConfigLongPollService-长轮询](./apollo/apollo-client/internals/RemoteConfigLongPollService.html)
* client端配置获取
    * [apollo-client模块: ConfigRepository-提供config&通知config更新](./apollo/apollo-client/internals/ConfigRepository.html)
    * [apollo-client模块: RemoteConfigRepository-从configservice远程获取config](./apollo/apollo-client/internals/RemoteConfigRepository.html)
    * [apollo-client模块: LocalConfigFileRepository-fallback机制实现](./apollo/apollo-client/internals/LocalConfigFileRepository.html)
    * [apollo-client模块: Config系列-从ConfigRepository获取config&多优先级properties提供](./apollo/apollo-client/internals/Config系列.html)
    * [apollo-client模块: ConfigService-Config及ConfigRepository管理](./apollo/apollo-client/ConfigService.html)
* 业务逻辑
    * [apollo-adminservice模块: Controllers](./apollo/apollo-adminservice/controllers.html)