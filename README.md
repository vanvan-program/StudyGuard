<div align="center">

简体中文 | [繁體中文](README.zh-TW.md) | [English](README.en.md)

# StudyGuard

**面向自主学习的离线 Android 学习监督、专注计时与任务计划应用**

[![Release](https://img.shields.io/github/v/release/vanvan-program/StudyGuard?include_prereleases&style=flat-square)](https://github.com/vanvan-program/StudyGuard/releases)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?style=flat-square)](#安装与更新)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10%20%7C%20Compose-7F52FF?style=flat-square)](#技术栈与从源码构建)
[![License](https://img.shields.io/badge/License-GPLv3-blue?style=flat-square)](LICENSE)

[下载安装包（v0.3.5-dev）](https://github.com/vanvan-program/StudyGuard/releases/tag/v0.3.5-dev) · [使用与安装指南](docs/studyguard/使用与安装指南.md) · [更新日志](CHANGELOG.md) · [验证报告](docs/studyguard/VALIDATION-0.3.5-dev.md) · [隐私政策](PRIVACY.md) · [问题反馈](https://github.com/vanvan-program/StudyGuard/issues)

</div>

---

## 项目定位

StudyGuard 将**学习任务计划、专注计时、应用使用提醒与学习统计**整合在一款原生 Android 应用中。支持**纸书学习**与**手机学习**双模式，数据默认保存在本机，**无账号、无广告、不申请联网权限**。

> [!NOTE]
> 当前版本为 **0.3.5-dev**（支持 Android 8.0 及以上），属于开发预览版：计划草案与日总结采用本地规则，尚未接入在线大模型；任务关联修复已在 Android 16 官方模拟器完成验证，厂商真机与长期后台耗电表现仍待验证。

## 核心功能

- **双模式学习计时**：支持手动开始、暂停与恢复。**纸书模式**允许锁屏持续计时；**手机模式**仅累计本应用及自选学习应用白名单内的有效时间。
- **计划与任务管理**：支持创建、编辑、改期与完成学习任务；可基于本地规则生成未来最多 7 天的计划草案，支持通过日历选择日期。
- **任务与计时联动**：可直接从任务发起专注计时，达成目标时长自动完成任务；提前结束也可选择同步标记完成；进程被系统中断后仍保留任务关联。
- **减少分心提醒**：自主配置受限娱乐应用与学习白名单。基础模式通过系统使用情况监测提醒，也可自愿开启无障碍增强；始终放行通信、桌面、系统设置与卸载界面。
- **学习进度与统计**：按有效学习时间触发进度检查，提供科目时长、近 7/30 日趋势、学习打卡与本地日总结。
- **JSON 本地备份与恢复**：在设置中导出完整学习数据为 JSON，恢复时校验格式、关联关系与校验和，确认后覆盖恢复。
- **随时自由退出**：提前结束无需填写原因，紧急解除无需密码或倒计时等待。

> 计时以可确认的观察区间为准。进程中断、长观测缺口与明显时钟跳变不会补计未知时间；记录时长仅反映应用使用/计时状态，普通应用的应用限制能力受 Android 系统机制约束。

## 安装与更新

1. 前往 [GitHub Releases](https://github.com/vanvan-program/StudyGuard/releases) 下载 `StudyGuard-0.3.5-dev-release.apk`（`debug` 与 `androidTest` 包仅用于开发测试，日常试用请选择 `release`）。
2. 在手机文件管理器中打开 APK，按系统提示授权并安装。
3. 应用包名为 `io.studyguard.app`，**不会覆盖上游 FocusForcePlus**。本版沿用 StudyGuard 开发证书，可直接覆盖同签名旧开发版；证书与安装包校验值见验证报告与 Release 中的 `SHA256SUMS.txt`。自行构建的包签名不同，升级前请先在设置中导出备份。

## 权限与隐私边界

| 权限 | 用途 | 是否必需 |
| :--- | :--- | :--- |
| 使用情况访问 | 识别前台应用并计算手机模式有效学习时间 | 按需开启 |
| 通知权限 | 显示计时运行状态与分心提醒 | 按需开启 |
| 无障碍服务 | 仅读取窗口包名以增强提醒，**不读取文字、输入或页面内容** | 可选 |
| 精确闹钟 / 勿扰访问 | 沿用原版工具的可选提醒辅助能力 | 可选 |

- 应用 **未声明 `INTERNET` 权限**，不接入云服务或第三方统计分析 SDK，并排除系统云备份与设备转移。
- 用户主动导出的 JSON 备份文件**未加密**；若保存至云盘目录，可能由对应文档提供方同步。卸载会删除应用内部数据，详见 [隐私政策](PRIVACY.md)。

## 技术栈与从源码构建

- **技术栈**：Kotlin `2.2.10` · Jetpack Compose · Material 3 · Room · Gradle `9.3.1` · AGP `9.1.1`
- **环境要求**：JDK `21` · Android SDK Platform `36.1`（`minSdk 26` / `targetSdk 36`）

```sh
git clone https://github.com/vanvan-program/StudyGuard.git
cd StudyGuard
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --max-workers=1
```

- Windows 可使用 `gradlew.bat` 或 `scripts/Build-StudyGuard.ps1`；如遇中文路径编码问题请见 [BUILD-ENCODING.md](docs/studyguard/BUILD-ENCODING.md)。
- Release 构建启用 R8 与资源收缩；自行签名可通过环境变量配置 `KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`（仓库不含私钥）。

### 当前验证摘要（0.3.5-dev）

| 验证项 | 结果 |
| :--- | :--- |
| JVM 单元测试 | 182 项通过（0 失败 / 0 跳过） |
| Android 16 `StudyPersistenceTest` | 17 项通过 |
| Release 端到端实测 | 提前结束完成任务、强停恢复关联、达标自动完成均通过 |
| 数据库迁移 | Room 10→12、11→12 及 0.3.4→0.3.5 保留数据验证通过 |
| Lint Debug | 0 errors / 31 warnings / 10 hints |

完整验证证据与已知待处理项见 [VALIDATION-0.3.5-dev.md](docs/studyguard/VALIDATION-0.3.5-dev.md)。

## 来源与开源协议

- 本项目基于 [karOS555/FocusForcePlus](https://github.com/karOS555/FocusForcePlus)（固定上游提交 [`f2987bc`](https://github.com/karOS555/FocusForcePlus/commit/f2987bc61d98bf8db42304929ed38bcba8090838)）二次开发，保留上游版权及 **[GNU GPLv3](LICENSE)** 协议。
- 相关文档：[上游原始说明](UPSTREAM_README.md) · [StudyGuard 修改记录](CHANGES_STUDYGUARD.md) · [第三方组件与许可证声明](THIRD_PARTY_NOTICES.md) · [文档目录](docs/README.md)。
