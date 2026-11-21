# StudyGuard 修改说明

日期：2026-11-10，构建环境修订于2026-11-12。上游 karOS555/FocusForcePlus，提交 `f2987bc61d98bf8db42304929ed38bcba8090838`。
最初开发分支 `studyguard/development`。2026-11-20 整理为独立仓库 [vanvan-program/StudyGuard](https://github.com/vanvan-program/StudyGuard)，公开分支为 `main`。以下旧版记录按对应日期和版本理解。
原始LICENSE、已有版权和归属文件保留；原README移至UPSTREAM_README.md。

## 0.3.7-dev（2026-11-21）

- 学习任务可以分段累计时长，结束一段后可选「下次继续」或「任务已完成」。待确认状态在重启后保留。
- 缺少使用情况访问权限时，学习页保留已填内容并引导授权。今日任务显示标题，也能从计划页补做往期任务。
- 科目筛选使用同一批记录计算次数和展示明细。Android 16 模拟器锁屏实测记录 45 分钟有效学习，监督中断 0 次。[验收报告](docs/studyguard/VALIDATION-0.3.7-dev.md)

0.3.6-dev（2026-11-21，公开开发预览版）：
- 学习任务独立保存标题、完成状态和时间，不再创建或修改原版待办；原版待办的删除、自动清理及备份恢复不再级联删除学习任务。
- Room 12→13 显式迁移旧学习任务，保留任务编号、标题、计划、完成时间与会话关联，仅移除旧学习任务表明确引用的待办镜像。任务自增序列继续保留，避免历史会话指向后来复用编号的新任务。
- 新版学习备份只含 StudyGuard 数据；兼容 schema11/schema12 的原始校验和，验证旧待办与任务关系后转换为独立学习任务，不向原版待办导入任何记录。恢复时继续重映射会话关联。
- 修正两处日历设备测试的语义节点定位；新建/删除任务测试先收起输入法、等待成功提示浮层消失、滚动至删除按钮，并等待任务确实消失，避免只点击而未验证结果。补充空任务表迁移后编号不复用、重复完成不改写完成时间的设备回归。
- 183 项 JVM 单元测试、Android 16 完整 37 项设备回归通过；Release 的提前结束打卡、强停恢复关联、恢复后到时自动完成三个实际流程通过。
- 验证结果及覆盖安装证据见 [本版验证报告](docs/studyguard/VALIDATION-0.3.6-dev.md)，安装包与对应提交的源码归档见 [GitHub Release](https://github.com/vanvan-program/StudyGuard/releases/tag/v0.3.6-dev)。

0.3.5-dev（2026-11-20）：
- 修复提前结束时勾选「同时将任务标为已完成」却未打卡的问题。任务编号从持久化会话读取；结束请求包含完成任务选项，服务停止先于页面保存时也沿用同一请求；结束事件与打卡在同一事务中保存。
- 会话新增可空 `linkedTaskId`，Room 11→12 显式增加列，保留全部旧数据；启动、暂停、检查点、进程恢复与恢复计时沿用任务关联。旧会话没有可恢复的关联时保持空值，不按同科目猜测任务。
- JSON 备份记录任务关联，恢复后重新映射编号；已删除任务的历史关联在导出时归一化为空值。兼容 schema11 旧备份的原始校验和，并保留未知时长不补计、恢复备份不自动开始监督的行为。
- 新增 2 项 JVM 备份测试与 5 项设备回归测试，覆盖页面提前结束、服务停止竞争、数据库重开后的关联、旧版迁移及备份关联映射。182 项 JVM 测试与 Android 16 的 17 项持久化测试通过，验证结果见 [本版报告](docs/studyguard/VALIDATION-0.3.5-dev.md)。

0.2.1-dev补充：备份编码、解码与校验移到后台处理，避免大JSON处理占用界面线程；新增会话状态/结束时间/计时边界、日期与区间溢出、待办一对一关联及孤立待办校验，在数据库覆盖前拒绝非法备份。备份格式版本保持1，合法0.2.0-dev备份继续兼容，未修改Room表结构。
设置页与备份元数据使用系统读取的实际安装版本；打包脚本从Gradle读取版本，并在打包后逐文件比较源码归档与工作目录，避免手工维护多个版本号。
0.3.0-dev：新增独立的统计计算模块和22项纯逻辑测试；统计页支持今日/近7日/近30日与科目筛选、前周期对比、日均与达标日、点选日期柱状趋势、科目占比、统计口径说明及异常记录提示。学习页显示权限/增强状态，计划与任务编辑增加数字键盘和校验，宽屏使用导航栏。完整界面和设备UI测试仍未执行。
0.3.1-dev（2026-11-14）：
- 新增首次打开聚光灯引导（3 步：开始学习 → 权限与受限应用 → 统计入口）。全屏 70% 遮罩用 even-odd 路径挖圆角镂空，呼吸光晕独立图层；目标位置全部由 `onGloballyPositioned` 实测，不使用写死坐标，旋转、分屏、键盘、折叠屏与目标自身尺寸变化都会重排；步骤切换在同一层内 400ms 插值，气泡 120ms 淡出、160ms 淡入并有动画锁。完成或跳过写入 SharedPreferences（`study_onboarding` / `spotlight_v1`），设置页「数据与 AI」新增「重新播放新手引导」。动效关闭时自动降级，不改变流程与记录。新增 7 项气泡定位纯几何单元测试与 `StudyUiTest` 引导/选择器防竞态修复。
- 复查修复：引导层根布局漏写 `onSizeChanged`、`rootSize` 恒为 0 等 10 项问题及矮屏/横屏下滚动微调底栏预留。
- 性能与结构优化：引导动画值与缩放改为绘制阶段读取，锚点改 `snapshotFlow` 订阅；`StudyMonitorService` 迁至 `Dispatchers.Default` 并为进度检查增加互斥保护；`StudyRuntime.protectedPackages()` 增加 60 秒内存缓存；`StudyViewModel` 去除冗余流、事件展示改用 `recentEventsLimited(200)`、统计源增加 5 秒 `throttleLatest` 节流、应用列表改进入设置页按需加载；`StudyGuardScreen.kt` 按页面拆分为 `StudyLearningPage.kt`、`StudyPlanPage.kt`、`StudySettingsPage.kt` 与 `StudyComponents.kt`。
0.3.2-dev（2026-11-14）：
- 计划页日期选择全部改为日历弹窗点选，取消文本输入日期方式：新增 `StudyCalendar.kt`（`StudyDateField` 与 `StudyCalendarDialog`），替换目标设置中的「考试日期」、任务列表中的「按日期筛选」及「编辑学习任务」中的「日期」。
- 日历采用周一至周日固定 6×7 网格，支持点选年月快捷跳转、点击「今天」快速定位、任务日期圆点提示以及清除筛选查看「全部日期」；考试日期限制仅可选今天及之后日期。
- 新增 6 项日历网格与日期逻辑 JVM 单元测试（`StudyCalendarTest`），以及 `StudyCalendarUiTest` 与 `StudyUiTest` 计划页日历选择设备端用例。
0.3.3-dev（2026-11-15）：
- 新增 `study/core/StudyQuickHelpers.kt` 承载高频输入快捷项：科目解析（`,`/`，`/`、`/换行分隔，去重保序）与「今日待学科目优先、其次学习档案与历史任务」的快捷科目建议（上限 12 个），学习页与计划页共用。
- 学习页与任务编辑加入 15/25/45/60/90 分钟时长预设，会话中显示「还剩 X / 已达成目标」文案。
- 设置页受限应用选择器加入「短视频 / 社交 / 影音 / 游戏」四类常用预设，按包名前缀匹配，命中项批量写入受限列表。
- 新增 7 项 `StudyQuickHelpersTest`，测试总数由 173 项增至 180 项；Lint 0 error / 54 warning。未做设备端实装。
0.3.4-dev（2026-11-15）：
- 启用 R8 代码收缩（`isMinifyEnabled = true`）与资源收缩（`isShrinkResources = true`），并加 `localeFilters = [zh, zh-rCN, zh-rHK, zh-rTW, en]`（仅保留默认与中英资源，剔除其余 80+ 传递语言）；移除 14 个未引用模板图标与颜色资源，release 包内置 `assets/dexopt/baseline.prof` 与 `.profm`。
- `classes*.dex` 由 17 个裁至 1 个；`resources.arsc` 由 487,192 字节降至 25,224 字节；Release 包 2,491,225 字节，较 0.3.3-dev 的 debug 包减少 89.2%。
- `StudyGuardActivity`、`StudyMonitorService`、`AppBlockerAccessibilityService`、`AppDatabase(_Impl)`、`StudySummaryWorker`、`StudyWorkerEntryPoint`、`StudyBackupPayload` 等经 `seeds.txt`/`mapping.txt` 核验完整保留；Lint 0 error / 36 warning。
- 固定工作区签名密钥库路径，Release 与 Debug、androidTest 包同证书，可直接覆盖升级 `0.3.0-dev`～`0.3.3-dev` 保留数据。未做设备端实装。

|修改范围|内容|
|---|---|
|app/build.gradle.kts、gradle.properties|独立包名/版本、测试schema assets、中文路径预检；构建JVM采用COMPAT修复Windows测试参数文件编码，源码及测试进程显式UTF-8；保留SDK；release 启用 R8 代码收缩、资源收缩与 `localeFilters`，固定工作区签名密钥库|
|AndroidManifest.xml、values/strings.xml、drawable/ic_studyguard.xml|中文入口、内部学习服务/紧急退出、新图标；移除悬浮窗、全屏意图、USE_EXACT_ALARM；保留原任务可选SCHEDULE_EXACT_ALARM|
|xml/backup_rules.xml、xml/data_extraction_rules.xml|排除云备份与设备转移数据|
|data/db/AppDatabase.kt、di/DatabaseModule.kt|六张学习表及Room10→11迁移，不删除旧表|
|study/data|复用Todo的学习任务、持久化会话、分段账本、事件与总结；完整学习JSON备份与事务化覆盖恢复|
|study/backup|版本、长度、校验和、关联完整性校验；活动会话恢复时标记提前结束|
|study/core|单调时间规则、跨日拆分、打卡与有限期限制租约|
|study/ai|可替换 `StudyAiProvider` 接口 + 本地规则默认实现（`OfflineStudyAiProvider` 经 Hilt `@Binds` 注入）；`ResilientStudyAiProvider`（15 秒超时 + 失败回退）已实现并由 `StudyPlannerTest` 覆盖，但生产绑定尚未接入，待接入远程 provider 时启用；无远程服务|
|study/monitor|UsageEvents、诚实能力状态、手动前台会话、进度通知及延后日总结；60秒超时、按需续期且退出释放的CPU唤醒锁|
|study/ui|中文学习/计划/统计/设置与应用选择；直接可达的紧急解除|
|study/ui/StudySpotlight.kt、StudyGuardScreen.kt、StudyComponents.kt|首次打开聚光灯引导：实测锚点、圆角镂空、呼吸光晕、自适应气泡、步骤动画锁、完成标记与设置页重播入口|
|service/AppBlockerAccessibilityService.kt|仅有效学习租约期间拦截；旧独立规则不再执行；不读窗口内容|
|service/FocusForegroundService.kt|关闭不可退出行为；单调增量计时，观测缺口不补计，保留短会话退出历史|
|service/RoutineForegroundService.kt、ui/screens/routine/ActiveRoutineViewModel.kt|取消不可提前结束行为，保留原例行数据/流程|
|MainActivity.kt|保留工具入口，取消旧全屏/悬浮窗权限启动弹窗|
|src/test、src/androidTest、scripts|时间/租约/计划失败后备、迁移/持久化/UI测试；构建、schema与依赖证据脚本|

未实现：远程大模型、云Key配置、完成的系统和多品牌运行验收。学习数据完整导出/恢复已实现，设备端恢复验收尚未完成。
原版工具未全面汉化，旧统计不并入新学习账本。
