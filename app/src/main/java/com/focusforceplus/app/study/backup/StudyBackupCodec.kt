package com.focusforceplus.app.study.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.security.MessageDigest
import java.time.LocalDate

@Serializable
data class StudyBackupPayload(
    val schemaVersion: Int = StudyBackupCodec.SCHEMA_VERSION,
    val profile: BackupProfile? = null,
    val tasks: List<BackupTask> = emptyList(),
    val sessions: List<BackupSession> = emptyList(),
    val segments: List<BackupSegment> = emptyList(),
    val events: List<BackupEvent> = emptyList(),
    val summaries: List<BackupSummary> = emptyList(),
)

@Serializable
private data class StudyBackupEnvelope<T>(
    val format: String = StudyBackupCodec.FORMAT,
    val formatVersion: Int = StudyBackupCodec.FORMAT_VERSION,
    val exportedWall: Long,
    val appVersion: String,
    val payload: T,
    val sha256: String,
)

@Serializable
data class BackupProfile(
    val id: Int = 1,
    val goal: String,
    val subjects: String,
    val examDay: String,
    val dailyMinutes: Int,
    val progressPercent: Int,
    val checkMinutes: Int,
    val blockedPackages: String,
    val allowedPackages: String,
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val exemptPackages: String = "",
)

@Serializable
data class BackupTask(
    val id: Long,
    val title: String,
    val subject: String,
    val day: String,
    val plannedMinutes: Int,
    val source: String,
    val isCompleted: Boolean,
    val createdAt: Long,
    val completedAt: Long? = null,
)

// Preserve the field order and defaults used to sign backups from schemas 11 and 12.
@Serializable
private data class LegacyStudyBackupPayload(
    val schemaVersion: Int = 12,
    val profile: BackupProfile? = null,
    val todos: List<LegacyBackupTodo> = emptyList(),
    val tasks: List<LegacyBackupTask> = emptyList(),
    val sessions: List<BackupSession> = emptyList(),
    val segments: List<BackupSegment> = emptyList(),
    val events: List<BackupEvent> = emptyList(),
    val summaries: List<BackupSummary> = emptyList(),
)

@Serializable
private data class LegacyBackupTodo(
    val id: Long,
    val title: String,
    val description: String? = null,
    val dueDateTime: Long? = null,
    val priority: Int,
    val isCompleted: Boolean,
    val isRecurring: Boolean,
    val recurringPattern: String? = null,
    val snoozeCount: Int,
    val maxSnoozeCount: Int,
    val rescheduleCount: Int,
    val maxRescheduleCount: Int,
    val createdAt: Long,
    val completedAt: Long? = null,
    val postponedTo: Long? = null,
    val checklistJson: String? = null,
)

@Serializable
private data class LegacyBackupTask(
    val id: Long,
    val todoId: Long,
    val subject: String,
    val day: String,
    val plannedMinutes: Int,
    val source: String,
)

@Serializable
data class BackupSession(
    val id: String,
    val subject: String,
    val mode: String,
    val targetMs: Long,
    val state: String,
    val startedWall: Long,
    val lastWall: Long,
    val lastElapsed: Long,
    val effectiveMs: Long,
    val endedWall: Long? = null,
    val endReason: String,
    val blockedPackages: String,
    val allowedPackages: String,
    val checkMinutes: Int,
    val lastCheckEffectiveMs: Long,
    // Omit null so schema-11 backups retain their original canonical checksum.
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val linkedTaskId: Long? = null,
)

@Serializable
data class BackupSegment(
    val id: Long,
    val sessionId: String,
    val subject: String,
    val day: String,
    val startedWall: Long,
    val durationMs: Long,
    val capability: String,
)

@Serializable
data class BackupEvent(
    val id: Long,
    val sessionId: String,
    val kind: String,
    val atWall: Long,
    val detail: String,
)

@Serializable
data class BackupSummary(
    val day: String,
    val content: String,
    val generatedWall: Long,
    val source: String,
)

data class StudyBackupSummary(
    val tasks: Int,
    val sessions: Int,
    val segments: Int,
    val events: Int,
    val summaries: Int,
)

data class VerifiedStudyBackup(val payload: StudyBackupPayload, val exportedWall: Long, val appVersion: String)

object StudyBackupCodec {
    const val FORMAT = "StudyGuardBackup"
    const val FORMAT_VERSION = 1
    const val SCHEMA_VERSION = 14
    const val MAX_BYTES = 25 * 1024 * 1024
    private val activeStates = setOf("RUNNING", "PAUSED", "INTERRUPTED")
    private val endedStates = setOf("COMPLETED", "EARLY_END", "EMERGENCY_END")

    private val compact = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }
    private val readable = Json(compact) {
        prettyPrint = true
    }

    fun encode(payload: StudyBackupPayload, exportedWall: Long, appVersion: String): String {
        validate(payload)
        val checksum = sha256(compact.encodeToString(StudyBackupPayload.serializer(), payload))
        return readable.encodeToString(
            StudyBackupEnvelope.serializer(StudyBackupPayload.serializer()),
            StudyBackupEnvelope(exportedWall = exportedWall, appVersion = appVersion, payload = payload, sha256 = checksum),
        )
    }

    fun decode(text: String): StudyBackupPayload = inspect(text).payload

    fun inspect(text: String): VerifiedStudyBackup {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份文件超过 25 MB 限制" }
        val envelope = parseBackup {
            // Keep the typed field order for checksum verification, including for old backups.
            compact.decodeFromString(StudyBackupEnvelope.serializer(JsonObject.serializer()), text)
        }
        require(envelope.format == FORMAT) { "备份格式标记不匹配" }
        require(envelope.formatVersion == FORMAT_VERSION) { "不支持的备份格式版本" }
        val payload = when ((envelope.payload["schemaVersion"] as? JsonPrimitive)?.intOrNull) {
            13, SCHEMA_VERSION -> {
                val current = parseBackup {
                    compact.decodeFromJsonElement(StudyBackupPayload.serializer(), envelope.payload)
                }
                verifyChecksum(compact.encodeToString(StudyBackupPayload.serializer(), current), envelope.sha256)
                if (current.schemaVersion == 13) current.copy(schemaVersion = SCHEMA_VERSION, profile = current.profile?.migrateExemptions()) else current
            }
            11, 12 -> {
                val legacy = parseBackup {
                    compact.decodeFromJsonElement(LegacyStudyBackupPayload.serializer(), envelope.payload)
                }
                verifyChecksum(compact.encodeToString(LegacyStudyBackupPayload.serializer(), legacy), envelope.sha256)
                legacy.toCurrent()
            }
            else -> throw IllegalArgumentException("备份数据库版本不兼容")
        }
        validate(payload)
        return VerifiedStudyBackup(payload, envelope.exportedWall, envelope.appVersion)
    }

    private fun BackupProfile.migrateExemptions() = copy(exemptPackages = allowedPackages, allowedPackages = "")

    private inline fun <T> parseBackup(read: () -> T): T = try {
        read()
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("不是有效的 StudyGuard 备份文件")
    }

    private fun verifyChecksum(canonicalPayload: String, checksum: String) {
        require(MessageDigest.isEqual(sha256(canonicalPayload).toByteArray(), checksum.toByteArray())) {
            "备份校验失败：内容可能被修改或损坏"
        }
    }

    private fun LegacyStudyBackupPayload.toCurrent(): StudyBackupPayload {
        val todosById = todos.associateBy { it.id }
        val linkedTodoIds = tasks.map { it.todoId }.toSet()
        require(todosById.size == todos.size && todosById.keys.all { it > 0 }) { "备份待办标识无效" }
        require(linkedTodoIds.size == tasks.size) { "多个学习任务关联同一待办" }
        require(todosById.keys == linkedTodoIds) { "备份学习任务与旧待办的关联不完整" }
        return StudyBackupPayload(
            profile = profile?.migrateExemptions(),
            tasks = tasks.map { task ->
                val todo = todosById.getValue(task.todoId)
                BackupTask(
                    task.id, todo.title, task.subject, task.day, task.plannedMinutes, task.source,
                    todo.isCompleted, todo.createdAt, todo.completedAt,
                )
            },
            sessions = sessions,
            segments = segments,
            events = events,
            summaries = summaries,
        )
    }

    fun normalizeForRestore(payload: StudyBackupPayload, nowWall: Long): StudyBackupPayload {
        validate(payload)
        return payload.copy(sessions = payload.sessions.map { session ->
            if (session.state in activeStates) {
                session.copy(
                    state = "EARLY_END",
                    endedWall = nowWall,
                    endReason = "备份恢复：原会话未结束，已标记提前结束",
                )
            } else session
        })
    }

    private fun validate(payload: StudyBackupPayload) {
        require(payload.schemaVersion == SCHEMA_VERSION) { "备份数据库版本不兼容" }
        require(payload.profile == null || payload.profile.id == 1) { "用户资料标识无效" }
        payload.profile?.let {
            require(it.goal.length <= 500 && it.subjects.length <= 720) { "用户资料文本过长" }
            require(it.dailyMinutes in 1..720 && it.progressPercent in 0..100) { "用户资料数值无效" }
            require(it.checkMinutes in 1..180) { "检查间隔无效" }
            require(validDate(it.examDay)) { "考试日期无效" }
        }
        require(payload.tasks.map { it.id }.toSet().size == payload.tasks.size) { "备份学习任务标识重复" }
        require(payload.sessions.map { it.id }.toSet().size == payload.sessions.size) { "备份会话标识重复" }
        require(payload.summaries.map { it.day }.toSet().size == payload.summaries.size) { "备份总结日期重复" }
        val sessionIds = payload.sessions.map { it.id }.toSet()
        val taskIds = payload.tasks.map { it.id }.toSet()
        require(payload.sessions.all { it.linkedTaskId == null || it.linkedTaskId in taskIds }) {
            "学习会话缺少对应任务"
        }
        require(payload.segments.all { it.sessionId in sessionIds }) { "学习分段缺少对应会话" }
        require(payload.events.all { it.sessionId in sessionIds }) { "学习事件缺少对应会话" }
        require(payload.tasks.all {
            it.id > 0 && it.title.isNotBlank() && it.title.length <= 500 &&
                it.plannedMinutes in 1..720 && it.subject.isNotBlank() && validDate(it.day)
        }) { "备份学习任务字段无效" }
        require(payload.summaries.all { validDate(it.day) }) { "备份总结日期无效" }
        require(payload.sessions.all {
            it.id.isNotBlank() && it.subject.isNotBlank() &&
                it.targetMs > 0 && it.effectiveMs in 0..it.targetMs &&
                it.lastCheckEffectiveMs in 0..it.effectiveMs &&
                it.startedWall >= 0 && it.lastWall >= 0 && it.lastElapsed >= 0 &&
                (it.endedWall == null || it.endedWall >= 0) &&
                it.mode in setOf("PAPER", "PHONE") && it.checkMinutes in 1..180 &&
                it.state in activeStates + endedStates &&
                (if (it.state in activeStates) it.endedWall == null else it.endedWall != null)
        }) { "备份会话字段无效" }
        require(payload.segments.all {
            it.durationMs > 0 && it.startedWall >= 0 && validDate(it.day) &&
                it.subject.isNotBlank() && it.durationMs <= Long.MAX_VALUE - it.startedWall
        }) { "备份分段字段无效" }
        require(payload.events.all { it.atWall >= 0 && it.kind.isNotBlank() }) { "备份事件字段无效" }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun validDate(value: String): Boolean =
        runCatching { LocalDate.parse(value) }.isSuccess
}
