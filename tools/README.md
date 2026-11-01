# 独立逻辑测试

常规 Android 原生回归检查位于 app/src/test，通过 ./gradlew :app:testDebugUnitTest 执行。

本目录还包含不需要真实蓝牙硬件的 Java 状态、协议与连接模拟测试。在 Windows PowerShell 设置 JAVA_HOME 为 JDK 17 后运行：

```powershell
.\tools\test_java_protocol.ps1
.\tools\test_panel_state.ps1
.\tools\test_panel_interactions.ps1
.\tools\test_widgets.ps1
.\tools\controller-tests\run.ps1
.\tools\session-tests\run.ps1
```

可选：安装 Node.js 后运行 node tools/test_protocol_vectors.mjs，验证补充协议向量。

测试里的蓝牙设备与连接由本地桩实现，不包含真实设备地址或签名信息。
