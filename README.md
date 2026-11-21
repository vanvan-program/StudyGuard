# StudyGuard

面向自主学习的 Android 应用：把学习任务、专注计时、应用使用提醒和学习统计放在一起。支持纸书与手机学习模式，数据默认保存在本机，无账号、无广告、不申请联网权限。

**[下载安装包](https://github.com/vanvan-program/StudyGuard/releases/tag/v0.3.6-dev)** · [使用指南](docs/studyguard/使用与安装指南.md) · [验证结果](docs/studyguard/VALIDATION-0.3.6-dev.md) · [问题反馈](https://github.com/vanvan-program/StudyGuard/issues)

当前公开开发预览版为 **0.3.6-dev**，最低支持 Android 8.0。计划与总结使用本地规则，尚未接入在线大模型；本版设备验收覆盖 Android 16 官方模拟器，其他 Android 版本、厂商真机与长期后台运行仍待验证。

本版将学习任务与原版待办独立保存，修复原版恢复、清理及删除待办会误删学习任务的问题；支持升级迁移和旧版学习备份。183 项 JVM 单测与 Android 16 的完整 37 项设备测试通过，Release 的三个任务关联流程实测通过。日常试用下载 `StudyGuard-0.3.6-dev-release.apk`，修复内容、测试与签名见 [0.3.6-dev 验证报告](docs/studyguard/VALIDATION-0.3.6-dev.md)。

## 可以做什么

- **学习计时**：手动开始、暂停和恢复；纸书模式允许锁屏，手机模式只累计本应用及所选学习应用中的时间。
- **计划与任务**：创建、编辑、改期和完成任务；生成未来最多 7 天的本地规则草案，通过日历选择日期。
- **任务关联**：从任务开始学习，达到目标时自动完成；提前结束可选择同时完成任务；应用进程中断后仍保留关联任务。
- **减少分心**：自主选择受限娱乐应用与学习白名单。基础模式监测并提醒，可自愿开启无障碍增强；始终放行通信、桌面、系统设置与卸载。
- **进度与统计**：按有效学习时间触发进度检查，查看科目时长、近 7/30 日趋势、打卡与本地日总结。
- **数据备份**：在设置中导出学习数据为 JSON，确认后覆盖恢复；校验备份格式、关联关系和校验和。
- **随时退出**：提前结束无需填写原因，紧急解除无需密码或等待。

计时以可确认的观察区间为准。进程中断、长观测缺口和明显时钟跳变不会补计未知时间；记录时长不能证明认真学习。普通应用的应用限制能力受系统约束。

## 安装与更新

在 [0.3.6-dev Release](https://github.com/vanvan-program/StudyGuard/releases/tag/v0.3.6-dev) 下载 `StudyGuard-0.3.6-dev-release.apk`，用手机文件管理器打开并按系统提示安装。`debug` 和 `androidTest` 包面向开发测试，日常试用选 `release`。

包名为 `io.studyguard.app`，不会覆盖上游 FocusForcePlus。本版沿用已有 StudyGuard 开发证书，可覆盖同签名旧开发版；证书及安装包校验值见验证报告和 Release 附件中的 `SHA256SUMS.txt`。自行构建的包通常签名不同，更新前请先保存备份。

## 权限与数据

使用情况访问用于识别前台应用及计时；通知用于显示运行状态和提醒；可选无障碍服务仅读取窗口包名，不读取文字、输入和页面内容。原版工具保留可选的精确闹钟与勿扰访问。权限按需开启，可随时撤销。

应用未声明 `INTERNET`，不接入云服务或分析 SDK，系统云备份和设备转移排除应用数据。用户主动导出的 JSON **未加密**；如保存到云盘，文件可能由所选文档提供方同步。卸载会删除应用内部数据。详细说明见 [隐私政策](PRIVACY.md)。

## 从源码构建

使用 **JDK 21**、Android SDK Platform **36.1** 和项目自带 Gradle Wrapper；Android Studio 打开仓库根目录即可。首次同步需要下载依赖。工程使用 Gradle 9.3.1、AGP 9.1.1、Kotlin 2.2.10，minSdk 26、targetSdk 36。

```sh
git clone https://github.com/vanvan-program/StudyGuard.git
cd StudyGuard
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --max-workers=1
```

Windows 可使用 `gradlew.bat`，或现有 `scripts/Build-StudyGuard.ps1`。必要时通过本机 `local.properties` 配置 SDK 路径，该文件不提交。Windows 中文路径编码说明见 [BUILD-ENCODING.md](docs/studyguard/BUILD-ENCODING.md)。

Release 构建启用 R8 和资源收缩。自行签名时设置 `KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`；没有签名配置和本地兼容密钥时生成未签名 Release。仓库不含私钥。GitHub 的手动构建流程只生成 Debug 测试包，不自动发布 Release。

## 0.3.6-dev 验证结果

2026-11-21 对本次学习任务隔离与升级修复的验证：

| 项目 | 结果 |
| --- | --- |
| JVM 单元测试 | 183 项通过，0 失败、0 错误、0 跳过 |
| Android 16 完整设备回归 | 37 项通过，含 22 项数据持久化与迁移用例 |
| Release 实际操作 | 提前结束完成任务、强停恢复关联、恢复后达到目标自动完成均通过 |
| 数据升级 | Room 10→13、11→13、12→13 迁移及旧版覆盖安装数据保留验证通过 |
| 构建 | Debug、AndroidTest 与 R8 Release 成功 |
| Lint Debug | 0 errors、31 warnings、10 hints |

详细证据见 [验证报告](docs/studyguard/VALIDATION-0.3.6-dev.md)，包括日历测试修正与删除任务结果验证。历史版本的结果保留在 [0.3.5-dev 报告](docs/studyguard/VALIDATION-0.3.5-dev.md)。厂商真机、长期后台和耗电仍待验收。

## 来源与许可证

本项目基于 [karOS555/FocusForcePlus](https://github.com/karOS555/FocusForcePlus) 二次开发，固定上游提交 [`f2987bc61d98bf8db42304929ed38bcba8090838`](https://github.com/karOS555/FocusForcePlus/commit/f2987bc61d98bf8db42304929ed38bcba8090838)，保留上游版权及 GPLv3 [LICENSE](LICENSE)。原始说明保存在 [UPSTREAM_README.md](UPSTREAM_README.md)。

StudyGuard 的修改记录见 [CHANGES_STUDYGUARD.md](CHANGES_STUDYGUARD.md)，第三方归属与依赖许可证证据见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。上游历史网站与商店素材保留作为来源参考，见 [文档说明](docs/README.md)。
