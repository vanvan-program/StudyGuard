# StudyGuard 安全政策

当前开发版仅对最新公开版本维护修复。报告前请检查最新版本和已有说明。

## 报告问题

安全漏洞请使用本仓库 [Security → Report a vulnerability](https://github.com/vanvan-program/StudyGuard/security/advisories/new) 私下报告，说明受影响版本、设备与 Android 版本、复现步骤和影响。不要在公开 Issue 中上传私钥、个人备份或尚未修复漏洞的完整利用细节。

普通功能问题可使用 [Issues](https://github.com/vanvan-program/StudyGuard/issues)。

## 范围与边界

关注的安全问题包括：其他应用越权读取本机数据、备份恢复绕过完整性检查、外部输入导致崩溃或未经用户操作启动监督。

当前应用没有账号、服务器或联网权限。导出的 JSON 备份未加密，文件的后续同步由用户选择的文档提供方处理。

StudyGuard 是用户可自主退出的学习工具。紧急解除、系统设置、撤销权限、强停和卸载都是预期行为，不能将这些正常退出路径视为漏洞。普通应用也不能保证系统级封锁其他应用。
