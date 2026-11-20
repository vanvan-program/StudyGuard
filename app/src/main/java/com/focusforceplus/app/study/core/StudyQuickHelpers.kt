package com.focusforceplus.app.study.core

/**
 * 解析逗号、顿号或换行分隔的学习科目，保持输入顺序并去重。
 */
internal fun parseStudySubjects(raw: String): List<String> =
    raw.split(',', '，', '、', '\n', '\r')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

/**
 * 汇总学习页可用的快捷科目：今日待学任务科目优先，其次学习档案科目与历史任务科目。
 */
internal fun suggestedStudySubjects(
    profileSubjects: String,
    pendingTodaySubjects: List<String>,
    allTaskSubjects: List<String>,
    limit: Int = 12,
): List<String> = buildList {
    addAll(pendingTodaySubjects)
    addAll(parseStudySubjects(profileSubjects))
    addAll(allTaskSubjects)
}.map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
    .take(limit)

internal val StudyDurationPresets: List<Int> = listOf(15, 25, 45, 60, 90)

internal fun remainingDurationLabel(effectiveMs: Long, targetMs: Long): String {
    val remaining = (targetMs - effectiveMs).coerceAtLeast(0L)
    return if (remaining == 0L) "已达成目标" else "还剩 ${studyDurationLabel(remaining)}"
}

internal data class AppCategoryPreset(
    val id: String,
    val label: String,
    val packagePrefixes: List<String>,
)

/**
 * 设置页受限应用选择器的常用娱乐分类预设（兼容国内常见包名与通用前缀）。
 */
internal object StudyBlockedPresets {
    val ALL = listOf(
        AppCategoryPreset(
            id = "short_video",
            label = "短视频",
            packagePrefixes = listOf(
                "com.ss.android.ugc",
                "com.smile.gifmaker",
                "com.kuaishou.nebula",
                "tv.danmaku.bili",
                "com.ss.android.article.video",
                "air.tv.douyu.android",
                "com.duowan.kiwi",
                "com.zhiliaoapp.musically",
                "com.google.android.youtube",
            ),
        ),
        AppCategoryPreset(
            id = "social",
            label = "社交",
            packagePrefixes = listOf(
                "com.tencent.mm",
                "com.tencent.mobileqq",
                "com.sina.weibo",
                "com.xingin.xhs",
                "com.zhihu.android",
                "com.douban.frodo",
                "com.tieba.baidu",
                "com.instagram",
                "com.facebook.katana",
                "com.twitter",
                "com.reddit",
                "org.telegram.messenger",
                "com.discord",
            ),
        ),
        AppCategoryPreset(
            id = "media",
            label = "影音",
            packagePrefixes = listOf(
                "com.qiyi.video",
                "com.tencent.qqlive",
                "com.youku.phone",
                "com.hunantv.imgo.activity",
                "com.netease.cloudmusic",
                "com.tencent.qqmusic",
                "com.kugou.android",
                "com.netflix",
                "com.spotify",
            ),
        ),
        AppCategoryPreset(
            id = "games",
            label = "游戏",
            packagePrefixes = listOf(
                "com.tencent.tmgp",
                "com.miHoYo",
                "com.HoYoverse",
                "com.netease.dwrg",
                "com.taptap",
                "com.hypergryph",
                "com.kurogame",
                "com.supercell",
                "com.mojang",
                "com.roblox",
            ),
        ),
    )

    fun matchPackages(preset: AppCategoryPreset, packageNames: Iterable<String>): Set<String> =
        packageNames.filterTo(linkedSetOf()) { pkg ->
            preset.packagePrefixes.any { prefix ->
                pkg == prefix || pkg.startsWith("$prefix.")
            }
        }
}

/**
 * 将已选或免限项置顶展示，同时保留各分组原有排序顺序。
 */
internal fun <T> sortByPinnedOrder(
    items: List<T>,
    pinnedKeys: Set<String>,
    key: (T) -> String,
): List<T> {
    if (pinnedKeys.isEmpty() || items.isEmpty()) return items
    val (pinned, rest) = items.partition { key(it) in pinnedKeys }
    return if (pinned.isEmpty()) items else pinned + rest
}
