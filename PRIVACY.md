# 隐私说明

应用通过本地蓝牙与已配对的 OPPO Enco Free4 通信，不上传个人数据。发布 APK 没有 INTERNET 权限，也不集成账号登录、广告、行为分析、云同步或远程服务器。

## Android 权限

| 权限 | 用途 |
| --- | --- |
| BLUETOOTH_CONNECT | 读取已配对设备并建立本地蓝牙控制通道 |
| FOREGROUND_SERVICE | 在添加桌面小部件后维持后台同步服务 |
| FOREGROUND_SERVICE_CONNECTED_DEVICE | 将上述服务声明为已连接设备类型 |

应用不声明 POST_NOTIFICATIONS；Android 仍要求前台服务提交内部通知，系统可能显示“运行中应用”。

## 本地数据

SharedPreferences 保存最近确认的模式、同步暂停状态等本地偏好。数据留在设备上，卸载应用时删除。应用不执行蓝牙抓包，也不上传蓝牙地址或系统日志。

## 构建与社区平台

Gradle 构建需要从依赖仓库下载工具与测试依赖，这与 APK 的运行行为不同。GitHub 的访问、下载与 Issue 由 GitHub 自身的隐私政策管理；不要在公开反馈里附带未脱敏的日志或截图。
