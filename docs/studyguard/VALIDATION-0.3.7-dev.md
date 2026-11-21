# StudyGuard 0.3.7-dev 模拟器验收

2026 年 11 月 21 日。安装包为 `io.studyguard.app`，版本号 16。验收使用 Android 16 / API 36 官方模拟器，测试任务均为合成数据。

## 45 分钟纸书学习

在 60 分钟的「纸书锁屏验收」任务中选择 45 分钟，启动监督后让模拟器熄屏。从 16:53:21 到 17:38:38（北京时间）连续观察 45 分 17 秒。每约 30 秒读取电源状态和持久化会话，共 91 次。采样均为熄屏或正在进入休眠，所有采样来自同一次会话。

| 核对项 | 实际结果 |
| --- | --- |
| 有效时长 | 会话和有效分段之和均为 2,700,000 毫秒，恰好 45 分钟 |
| 监督连续性 | 记录中有 0 次监督中断。到时自动结束本段计时 |
| 进度提醒 | 第 30 分钟生成进度检查，Android 通知管理器收到进度提醒（ID 7202） |
| 任务状态 | 达标时任务仍待确认，唤醒后可选「下次继续」或「任务已完成」 |
| 分段续学 | 选择「下次继续」，同一任务再次选中时自动带入剩余 15 分钟 |

[锁屏采样](evidence/0.3.7-dev/lock-samples.jsonl) · [进度提醒](evidence/0.3.7-dev/reminder.json) · [唤醒后的任务确认](evidence/0.3.7-dev/03-choice-after-lock.png) · [续学时长](evidence/0.3.7-dev/04-remaining-time.png)

## Release 操作结果

| 场景 | 实际结果 |
| --- | --- |
| 从 0.3.6-dev 覆盖升级 | 三条任务的编号、标题、日期、计划时长和完成状态均保留 |
| 首次开启监督权限 | 在学习页选择任务与 45 分钟目标，主按钮进入系统设置。授权前会话数为 0，返回后保留关联与时长。测试驱动通过 ADB 授予权限 |
| 同科目、同时长 | 两条阅读任务均为 60 分钟，学习页展示各自标题，启动后会话关联到所选任务 |
| 补做旧任务 | 从计划页选「全部日期」，直接从昨天的任务进入学习。学习 1 分钟后重启应用，确认窗口仍显示该任务。选择「任务已完成」后仅该任务打卡 |
| 按科目查监督事件 | 数据含一条数学受限访问和随后 200 条英语检查，数学筛选显示访问 1 次，明细显示对应的数学事件 |

[授权返回](evidence/0.3.7-dev/02-permission-return.png) · [往期任务](evidence/0.3.7-dev/05-overdue-task-selected.png) · [重启后确认](evidence/0.3.7-dev/06-choice-after-restart.png) · [科目计数](evidence/0.3.7-dev/07-subject-count.png) · [科目明细](evidence/0.3.7-dev/08-subject-event.png)

## 构建与兼容性

[184 项 JVM 单元测试](evidence/0.3.7-dev/standard-tests.json)和 38 项 Android 16 设备回归全部通过。Debug、AndroidTest 和 R8 Release 均构建成功。Lint 报告 0 个错误、31 个警告和 10 条提示。[设备回归原始输出](evidence/0.3.7-dev/instrumentation.txt)

Release 安装包沿用此前的开发签名，通过 APK Signature Scheme v2 校验。文件 SHA-256 为 `d30f601b666ea92395c39da2b1bc8f5bad70b9cfb8508a59124c1ff2b82891e9`。

实体手机的电池消耗、厂商后台策略和其他 Android 版本仍待实测。
