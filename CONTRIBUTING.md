# 参与贡献

欢迎提交 Issue 和 Pull Request。先描述可复现的问题，再说明改动后的行为。

- 保持 Java 17、Android 14+ 与当前包名兼容。
- 修改后运行 ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug。
- 协议 / 连接 / 状态逻辑变更需运行对应 tools 测试，并尽可能在真机验证。
- 界面调整需同时检查 2×2 与 2×1 的尺寸、点击区域、电量和动画。
- 不要提交签名密钥、密码、访问令牌、个人蓝牙地址、原始抓包或未脱敏的系统日志。
- 发布 APK 的签名由维护者在本机保管；CI 仅构建调试版。
