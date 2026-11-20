# 参与 StudyGuard

欢迎提交可复现的问题、设备测试结果和改进建议。一般问题使用 [Issues](https://github.com/vanvan-program/StudyGuard/issues)，敏感问题见 [SECURITY.md](SECURITY.md)。反馈时说明应用版本、Android 版本、设备型号和操作步骤，并移除日志中的个人信息。

## 本地开发

使用 JDK 21、Android SDK Platform 36.1 和项目 Gradle Wrapper。构建与运行方法见 [README](README.md)。最低运行版本 Android 8.0/API 26。

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --max-workers=1
```

`app/src/main/java/com/focusforceplus/app/study/` 为新增学习模块，包含 `data`、`core`、`backup`、`ai`、`monitor` 和 `ui`。原 `data/`、`service/`、`ui/` 等保留上游任务及工具。应用包名为 `io.studyguard.app`；源码命名空间保留 `com.focusforceplus.app`。

## 修改约定

- Kotlin、Compose、Room、Hilt；沿用现有模块与数据流。
- 新学习界面优先使用中文，界面约定见 [DESIGN.md](DESIGN.md)。
- 不读取无障碍窗口内容，不阻止系统设置、通信、权限撤销、紧急退出和卸载。
- 当前版本保持离线；新增远程能力应先讨论数据处理、用户授权与故障行为。
- 不把未知区间补计为有效学习时间；数据库升级保留数据，禁止破坏性迁移兜底。
- 不提交本机路径配置、签名私钥、个人备份或构建缓存。测试备份必须使用合成数据。
- 保留 GPLv3、上游归属和第三方许可说明。

从 `main` 建立修改分支，提交集中且说明清楚的 PR。提供适当的测试结果；数据库、计时、后台与权限修改需要相应设备验证，明确区分模拟器与真机结果。未验证的环境应如实注明。
