These tests compile the production EncoController and EncoProtocol against minimal
test-only Android/Bluetooth substitutes. They exercise lifecycle races, cancellation,
bounded retry, stale callbacks, write errors and delayed initialization without hardware.
The substitutes are never included in the APK. Run using run.ps1 with JDK 17 available.
They do not replace Android-device/RFCOMM compatibility testing.


1.8.6：新增旧写任务阻塞后再次连接、连接忽略关闭后的自动重试、
创建套接字阶段超时、该阶段重试次数上限、迟到超时不影响健康连接，
合计 14 个场景。新增场景使用可控阻塞替身，不依赖真实蓝牙驱动。
