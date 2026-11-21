package com.focusforceplus.app.study

import com.focusforceplus.app.study.backup.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class StudyBackupCodecTest {
    private fun payload() = StudyBackupPayload(
        profile = BackupProfile(
            goal = "完成复习", subjects = "数学,英语", examDay = "2026-12-20",
            dailyMinutes = 120, progressPercent = 35, checkMinutes = 30,
            blockedPackages = "game", allowedPackages = "book",
        ),
        tasks = listOf(BackupTask(3, "数学练习", "数学", "2026-11-12", 60, "OFFLINE_RULES", true, 1_000, 61_000)),
        sessions = listOf(BackupSession(
            id = "session-a", subject = "数学", mode = "PAPER", targetMs = 3_600_000,
            state = "EARLY_END", startedWall = 1_000, lastWall = 61_000, lastElapsed = 61_000,
            effectiveMs = 60_000, endedWall = 61_000, endReason = "测试",
            blockedPackages = "game", allowedPackages = "book", checkMinutes = 30,
            lastCheckEffectiveMs = 0,
        )),
        segments = listOf(BackupSegment(
            1, "session-a", "数学", "2026-11-12", 1_000, 60_000, "USAGE_EVENTS_ONLY",
        )),
        events = listOf(BackupEvent(1, "session-a", "STARTED", 1_000, "用户手动开始")),
        summaries = listOf(BackupSummary("2026-11-12", "有效学习 1 分钟", 62_000, "OFFLINE_RULES")),
    )

    @Test fun completeBackupRoundTrips() {
        val source = payload()
        val encoded = StudyBackupCodec.encode(source, 100_000, "test")
        assertEquals(source, StudyBackupCodec.decode(encoded))
        assertFalse(encoded.contains("\"todos\""))
        assertFalse(encoded.contains("\"todoId\""))
    }

    @Test fun modifiedContentIsRejected() {
        val encoded = StudyBackupCodec.encode(payload(), 100_000, "0.2.0-dev")
        assertThrows(IllegalArgumentException::class.java) {
            StudyBackupCodec.decode(encoded.replace("数学练习", "英语练习"))
        }
    }

    @Test fun incompatibleSchemaIsRejectedBeforeImport() {
        val encoded = StudyBackupCodec.encode(payload(), 100_000, "0.2.0-dev")
        assertThrows(IllegalArgumentException::class.java) {
            StudyBackupCodec.decode(encoded.replace("\"schemaVersion\": 13", "\"schemaVersion\": 10"))
        }
    }

    @Test fun blankTaskTitleIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StudyBackupCodec.encode(payload().copy(tasks = listOf(
                payload().tasks.single().copy(title = " "),
            )), 100_000, "0.2.0-dev")
        }
    }

    @Test fun activeSessionIsClosedWhenRestored() {
        val normalized = StudyBackupCodec.normalizeForRestore(
            payload().copy(sessions = listOf(payload().sessions.single().copy(
                state = "RUNNING", endedWall = null, endReason = "",
            ))),
            500_000,
        )
        assertEquals("EARLY_END", normalized.sessions.single().state)
        assertEquals(500_000L, normalized.sessions.single().endedWall)
        assertTrue(normalized.sessions.single().endReason.contains("备份恢复"))
    }

    @Test fun duplicateSessionIdsAreRejected() {
        val duplicate = payload().let { it.copy(sessions = it.sessions + it.sessions) }
        assertThrows(IllegalArgumentException::class.java) {
            StudyBackupCodec.encode(duplicate, 100_000, "0.2.0-dev")
        }
    }

    private fun assertRejected(source: StudyBackupPayload) {
        assertThrows(IllegalArgumentException::class.java) {
            StudyBackupCodec.encode(source, 100_000, "test")
        }
    }

    @Test fun unsupportedSchemaCannotBeExported() {
        assertRejected(payload().copy(schemaVersion = 99))
    }

    @Test fun unknownSessionStateIsRejected() {
        val source = payload()
        assertRejected(source.copy(sessions = listOf(source.sessions.single().copy(state = "UNKNOWN"))))
    }

    @Test fun blankSessionIdIsRejected() {
        assertRejected(StudyBackupPayload(sessions = listOf(payload().sessions.single().copy(id = " "))))
    }

    @Test fun creditedTimeBeyondTargetIsRejected() {
        val source = payload()
        assertRejected(source.copy(sessions = listOf(source.sessions.single().copy(effectiveMs = 3_600_001))))
    }

    @Test fun checkTimeBeyondCreditedTimeIsRejected() {
        val source = payload()
        assertRejected(source.copy(sessions = listOf(source.sessions.single().copy(lastCheckEffectiveMs = 60_001))))
    }

    @Test fun negativeSessionTimestampIsRejected() {
        val source = payload()
        assertRejected(source.copy(sessions = listOf(source.sessions.single().copy(lastElapsed = -1))))
    }

    @Test fun endedSessionNeedsEndTimestamp() {
        val source = payload()
        assertRejected(source.copy(sessions = listOf(source.sessions.single().copy(endedWall = null))))
    }

    @Test fun activeSessionCannotHaveEndTimestamp() {
        val source = payload()
        assertRejected(source.copy(sessions = listOf(source.sessions.single().copy(state = "RUNNING"))))
    }

    @Test fun invalidSegmentDayIsRejected() {
        val source = payload()
        assertRejected(source.copy(segments = listOf(source.segments.single().copy(day = "2026-02-30"))))
    }

    @Test fun overflowingSegmentEndIsRejected() {
        val source = payload()
        assertRejected(source.copy(segments = listOf(source.segments.single().copy(startedWall = Long.MAX_VALUE))))
    }

    @Test fun duplicateTaskIdIsRejected() {
        val source = payload()
        assertRejected(source.copy(tasks = source.tasks + source.tasks.single().copy(title = "另一项")))
    }

    @Test fun taskLinksRoundTripAndMissingTasksAreRejected() {
        val source = payload().let { it.copy(sessions = listOf(it.sessions.single().copy(linkedTaskId = 3))) }
        assertEquals(source, StudyBackupCodec.decode(StudyBackupCodec.encode(source, 100_000, "test")))
        assertRejected(source.copy(sessions = listOf(source.sessions.single().copy(linkedTaskId = 999))))
    }

    @Test fun actualSchemaElevenBackupRetainsItsOriginalChecksum() {
        val text = javaClass.getResource("/studyguard-schema11-backup.json")!!.readText()
        val restored = StudyBackupCodec.decode(text)
        assertEquals(13, restored.schemaVersion)
        assertEquals(2, restored.sessions.size)
        assertEquals(1, restored.tasks.size)
        assertEquals("QAReviewTask", restored.tasks.single().title)
        assertTrue(restored.sessions.all { it.linkedTaskId == null })
    }

    @Test fun schemaTwelveBackupPreservesLinkedTaskCompletionAndChecksum() {
        val text = javaClass.getResource("/studyguard-schema12-backup.json")!!.readText()
        val restored = StudyBackupCodec.decode(text)
        val task = restored.tasks.single()
        assertEquals(13, restored.schemaVersion)
        assertEquals("旧版已完成学习任务", task.title)
        assertTrue(task.isCompleted)
        assertEquals(1_000L, task.createdAt)
        assertEquals(61_000L, task.completedAt)
        assertEquals(task.id, restored.sessions.single().linkedTaskId)
        assertThrows(IllegalArgumentException::class.java) {
            StudyBackupCodec.decode(text.replace("旧版已完成学习任务", "篡改标题"))
        }
    }

    @Test fun legacyBackupsWithMissingExtraOrDuplicateTodoLinksAreRejected() {
        val json = Json { encodeDefaults = true }
        val original = json.parseToJsonElement(javaClass.getResource("/studyguard-schema12-backup.json")!!.readText()).jsonObject
        val payload = original.getValue("payload").jsonObject
        val todos = payload.getValue("todos").jsonArray
        val task = payload.getValue("tasks").jsonArray.single().jsonObject
        val invalidPayloads = listOf(
            JsonObject(payload + ("todos" to JsonArray(emptyList()))),
            JsonObject(payload + ("todos" to JsonArray(todos + JsonObject(todos.single().jsonObject + ("id" to JsonPrimitive(43)))))),
            JsonObject(payload + ("tasks" to JsonArray(listOf(task, JsonObject(task + ("id" to JsonPrimitive(8))))))),
        )
        for (invalid in invalidPayloads) {
            val canonical = json.encodeToString(JsonObject.serializer(), invalid)
            val checksum = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val signed = json.encodeToString(JsonObject.serializer(), JsonObject(original + mapOf(
                "payload" to invalid, "sha256" to JsonPrimitive(checksum),
            )))
            val error = assertThrows(IllegalArgumentException::class.java) { StudyBackupCodec.decode(signed) }
            assertTrue(error.message == "备份学习任务与旧待办的关联不完整" || error.message == "多个学习任务关联同一待办")
        }
    }

    @Test fun everyActiveStateRestoresAsEndedAndLeavesEndedRecordsUnchanged() {
        for (state in listOf("RUNNING", "PAUSED", "INTERRUPTED")) {
            val source = payload()
            val active = source.sessions.single().copy(state = state, endedWall = null)
            val restored = StudyBackupCodec.normalizeForRestore(source.copy(sessions = listOf(active)), 100_000)
            assertEquals("EARLY_END", restored.sessions.single().state)
            assertEquals(100_000L, restored.sessions.single().endedWall)
        }
        assertEquals(payload(), StudyBackupCodec.normalizeForRestore(payload(), 100_000))
    }
}
