package com.focusforceplus.app.study

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.focusforceplus.app.data.db.AppDatabase
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.study.ai.OfflineStudyAiProvider
import com.focusforceplus.app.study.data.*
import com.focusforceplus.app.study.monitor.StudyRuntime
import com.focusforceplus.app.study.ui.StudyViewModel
import com.focusforceplus.app.util.InstalledAppsHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StudyPersistenceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun memoryDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    private fun session(id: String = "test") = StudySession(
        id = id, subject = "数学", mode = "PAPER", targetMs = 60_000,
        state = "RUNNING", startedWall = 1_000, lastWall = 1_000, lastElapsed = 1_000,
    )

    @Test fun stoppingServiceAfterPausePreservesPausedStatus() = runBlocking {
        val database = memoryDatabase()
        try {
            val runtime = StudyRuntime(context)
            val repository = StudyRepository(database, runtime, OfflineStudyAiProvider())
            database.studyDao().saveSession(session())
            repository.pause()
            repository.interrupt("监督服务已停止，请手动恢复", "test")
            assertEquals("PAUSED", database.studyDao().currentSession()?.state)
            assertEquals("已暂停；本次限制已解除", runtime.status.value)
            assertEquals("PAUSED", database.studyDao().eventsOnce().single().kind)
        } finally { database.close() }
    }

    @Test fun migrationTenToTwelvePreservesOriginalTodos() = runBlocking {
        val name = "studyguard-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val schema = JSONObject(testContext.assets.open("com.focusforceplus.app.data.db.AppDatabase/10.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        val original = SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                original.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (offset in 0 until indices.length()) {
                    original.execSQL(indices.getJSONObject(offset).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            original.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            original.execSQL("INSERT OR REPLACE INTO room_master_table VALUES(42, ?)", arrayOf(schema.getString("identityHash")))
            original.execSQL("INSERT INTO todos (id,title,priority,isCompleted,isRecurring,snoozeCount,maxSnoozeCount,rescheduleCount,maxRescheduleCount,createdAt) VALUES (42,'保留旧任务',1,0,0,0,2,0,1,1000)")
            original.version = 10
        } finally { original.close() }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(StudyMigration, StudyTaskLinkMigration).build()
        try {
            assertEquals("保留旧任务", migrated.todoDao().getById(42)?.title)
            assertEquals(12, migrated.openHelper.writableDatabase.version)
            migrated.studyDao().saveProfile(StudyProfile(goal = "升级后可写入", examDay = "2026-12-10"))
            assertEquals("升级后可写入", migrated.studyDao().profileOnce()?.goal)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun updatingSessionCannotCascadeDeleteSegments() = runBlocking {
        val database = memoryDatabase()
        try {
            val dao = database.studyDao()
            val session = session()
            dao.saveSession(session)
            dao.insertSegments(listOf(StudySegment(
                sessionId = session.id, subject = session.subject, day = "2026-11-10",
                startedWall = 1_000, durationMs = 2_000, capability = "USAGE_EVENTS_ONLY",
            )))
            dao.saveSession(session.copy(effectiveMs = 2_000))
            dao.saveSession(session.copy(effectiveMs = 3_000, state = "EARLY_END"))
            assertEquals(1, dao.segments().first().size)
            assertEquals(2_000L, dao.segments().first().single().durationMs)
        } finally { database.close() }
    }

    @Test fun taskEditsReuseOriginalTodoAndDeletionCascades() = runBlocking {
        val database = memoryDatabase()
        try {
            val repository = StudyRepository(database, StudyRuntime(context), OfflineStudyAiProvider())
            repository.acceptPlan(listOf(com.focusforceplus.app.study.ai.PlannedTask("做题", "数学", "2026-11-10", 25)))
            val task = database.studyDao().tasks().first().single()
            repository.updateTask(task, "复习错题", "物理", "2026-11-11", 30)
            assertEquals("复习错题", database.todoDao().getById(task.task.todoId)?.title)
            assertEquals("2026-11-11", database.studyDao().tasks().first().single().task.day)
            repository.toggleTask(task)
            assertTrue(database.studyDao().tasks().first().single().isCompleted)
            repository.deleteTask(task)
            assertTrue(database.studyDao().tasks().first().isEmpty())
        } finally { database.close() }
    }

    @Test fun regeneratingPlanDoesNotOverwriteExistingTask() = runBlocking {
        val database = memoryDatabase()
        try {
            val repository = StudyRepository(database, StudyRuntime(context), OfflineStudyAiProvider())
            val planned = listOf(com.focusforceplus.app.study.ai.PlannedTask("原任务", "数学", "2026-11-10", 25))
            assertEquals(1, repository.acceptPlan(planned))
            assertEquals(0, repository.acceptPlan(planned.map { it.copy(title = "替换任务") }))
            assertEquals("原任务", database.studyDao().tasks().first().single().title)
        } finally { database.close() }
    }

    @Test fun emergencyEndRecordsSubMinuteSessionAndIsIdempotent() = runBlocking {
        val database = memoryDatabase()
        try {
            val runtime = StudyRuntime(context)
            val repository = StudyRepository(database, runtime, OfflineStudyAiProvider())
            val active = session()
            database.studyDao().saveSession(active)
            repository.finish("EMERGENCY_END", "", active.id)
            repository.finish("EMERGENCY_END", "", active.id)
            val stored = database.studyDao().sessions().first().single()
            assertEquals("EMERGENCY_END", stored.state)
            assertEquals(0, stored.effectiveMs)
            assertEquals(1, database.studyDao().recentEvents().first().size)
            assertNull(runtime.lease.value)
        } finally { database.close() }
    }

    @Test fun staleEndRequestCannotFinishNextSession() = runBlocking {
        val database = memoryDatabase()
        try {
            val repository = StudyRepository(database, StudyRuntime(context), OfflineStudyAiProvider())
            database.studyDao().saveSession(session("new"))
            assertNull(repository.finish("EARLY_END", "旧请求", "old"))
            assertEquals("RUNNING", database.studyDao().currentSession()?.state)
        } finally { database.close() }
    }

    @Test fun processRecoveryDoesNotCreditUnknownTime() = runBlocking {
        val database = memoryDatabase()
        try {
            val runtime = StudyRuntime(context)
            val repository = StudyRepository(database, runtime, OfflineStudyAiProvider())
            database.studyDao().saveSession(session().copy(effectiveMs = 1_500))
            repository.recover()
            val recovered = database.studyDao().currentSession()!!
            assertEquals("INTERRUPTED", recovered.state)
            assertEquals(1_500, recovered.effectiveMs)
            assertNull(runtime.lease.value)
        } finally { database.close() }
    }

    @Test fun profileAndTaskPersistAcrossDatabaseReopen() = runBlocking {
        val name = "studyguard-persistence-${UUID.randomUUID()}.db"
        var database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            database.studyDao().saveProfile(StudyProfile(goal = "持久化", examDay = "2026-12-10"))
            database.todoDao().insert(TodoEntity(title = "离线任务", createdAt = 1_000))
            database.close()
            database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            assertEquals("持久化", database.studyDao().profileOnce()?.goal)
            assertEquals("离线任务", database.todoDao().getAllOnce().single().title)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun backupRestoreReplacesStudyDataAndPreservesUnrelatedTodo() = runBlocking {
        val database = memoryDatabase()
        try {
            val repository = StudyRepository(database, StudyRuntime(context), OfflineStudyAiProvider())
            database.studyDao().saveProfile(StudyProfile(goal = "备份前目标", examDay = "2026-12-10"))
            repository.acceptPlan(listOf(
                com.focusforceplus.app.study.ai.PlannedTask("备份任务", "数学", "2026-11-12", 30),
            ))
            val unrelatedId = database.todoDao().insert(TodoEntity(title = "原版普通待办", createdAt = 1_000))
            val backup = repository.exportBackup("test")

            database.studyDao().saveProfile(StudyProfile(goal = "备份后目标", examDay = "2026-12-11"))
            repository.deleteTask(database.studyDao().tasks().first().single())
            val summary = repository.restoreBackup(backup)

            assertEquals("备份前目标", database.studyDao().profileOnce()?.goal)
            assertEquals("备份任务", database.studyDao().tasks().first().single().title)
            assertEquals(1, summary.tasks)
            assertEquals(0, summary.sessions)
            assertEquals("原版普通待办", database.todoDao().getById(unrelatedId)?.title)
        } finally { database.close() }
    }

    @Test fun manualAddTaskAllowsMultipleSameDaySubjectsAndSupportsToggle() = runBlocking {
        val database = memoryDatabase()
        try {
            val repository = StudyRepository(database, StudyRuntime(context), OfflineStudyAiProvider())
            val firstId = repository.addTask("上午刷题", "数学", "2026-11-15", 45)
            val secondId = repository.addTask("晚间复盘", "数学", "2026-11-15", 30)
            assertNotEquals(firstId, secondId)
            val tasks = database.studyDao().tasks().first()
            assertEquals(2, tasks.size)
            assertTrue(tasks.all { it.task.source == "MANUAL" })

            repository.completeTask(firstId)
            val updated = database.studyDao().tasks().first().associateBy { it.task.id }
            assertTrue(updated.getValue(firstId).isCompleted)
            assertFalse(updated.getValue(secondId).isCompleted)
        } finally { database.close() }
    }

    @Test fun finishLinkedTaskCompletionBehaviorMatchesCompletionKind() = runBlocking {
        val database = memoryDatabase()
        try {
            val runtime = StudyRuntime(context)
            val repository = StudyRepository(database, runtime, OfflineStudyAiProvider())
            val taskId = repository.addTask("背诵高频词汇", "英语", "2026-11-15", 25)

            // Emergency end should leave linked task incomplete.
            database.studyDao().saveSession(session("emergency").copy(linkedTaskId = taskId))
            repository.finish("EMERGENCY_END", "", "emergency")
            assertFalse(database.studyDao().tasks().first().single().isCompleted)
            assertNull(runtime.linkedTaskId.value)

            // Reached target completion (COMPLETED) auto-completes linked task.
            database.studyDao().saveSession(session("done").copy(effectiveMs = 60_000, linkedTaskId = taskId))
            repository.finish("COMPLETED", "达到目标", "done")
            assertTrue(database.studyDao().tasks().first().single().isCompleted)
            assertNull(runtime.linkedTaskId.value)
        } finally { database.close() }
    }

    @Test fun earlyEndThroughViewModelCompletesSelectedTaskAfterRestrictionsRelease() = runBlocking {
        val database = memoryDatabase()
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val runtime = StudyRuntime(context)
            val repository = StudyRepository(database, runtime, OfflineStudyAiProvider())
            val taskId = repository.addTask("提前结束也完成", "数学", "2026-11-20", 25)
            val active = session().copy(linkedTaskId = taskId)
            database.studyDao().saveSession(active)
            runtime.serviceAlive = true
            runtime.activate(active, runtime.beginRequest())
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val model = StudyViewModel(context, repository, runtime, InstalledAppsHelper(context))
                store.put("test", model)
                model.finish(false, "任务已经完成", true)
            }
            withTimeout(5_000) {
                while (database.studyDao().currentSession() != null) delay(20)
                while (database.studyDao().summariesOnce().none { it.day == java.time.LocalDate.now().toString() }) delay(20)
            }
            assertTrue(database.studyDao().tasks().first().single().isCompleted)
            assertEquals("EARLY_END", database.studyDao().sessionsOnce().single().state)
            assertNull(runtime.lease.value)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
            database.close()
        }
    }

    @Test fun serviceTeardownHonorsPendingTaskCompletionAndWritesOnlyOneEnd() = runBlocking {
        val database = memoryDatabase()
        try {
            val runtime = StudyRuntime(context)
            val repository = StudyRepository(database, runtime, OfflineStudyAiProvider())
            val taskId = repository.addTask("结束竞争", "数学", "2026-11-20", 25)
            val active = session().copy(linkedTaskId = taskId)
            database.studyDao().saveSession(active)
            runtime.activate(active, runtime.beginRequest())
            runtime.release("EARLY_END", "已完成任务", true)
            // onDestroy can finish first, without passing the UI's checkbox argument.
            repository.finish("EARLY_END", "已完成任务", active.id)
            repository.finish("EARLY_END", "已完成任务", active.id, true)
            assertTrue(database.studyDao().tasks().first().single().isCompleted)
            assertEquals(1, database.studyDao().eventsOnce().size)
        } finally { database.close() }
    }

    @Test fun taskAssociationSurvivesDatabaseReopenAndFreshRuntime() = runBlocking {
        val name = "studyguard-linked-recovery-${UUID.randomUUID()}.db"
        var database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val runtime = StudyRuntime(context)
            val repository = StudyRepository(database, runtime, OfflineStudyAiProvider())
            val taskId = repository.addTask("重启后继续", "数学", "2026-11-20", 1)
            val active = repository.start("数学", "PAPER", 1, runtime.beginRequest(), taskId)!!
            database.studyDao().saveSession(active.copy(state = "RUNNING", effectiveMs = 1_500))
            database.close()
            database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            val freshRuntime = StudyRuntime(context)
            val recoveredRepository = StudyRepository(database, freshRuntime, OfflineStudyAiProvider())
            recoveredRepository.recover()
            assertEquals(taskId, freshRuntime.linkedTaskId.value)
            assertEquals(taskId, freshRuntime.current.value?.linkedTaskId)
            assertEquals(1_500L, freshRuntime.current.value?.effectiveMs)
            assertEquals("INTERRUPTED", freshRuntime.current.value?.state)
            assertNull(freshRuntime.lease.value)
            database.studyDao().saveSession(freshRuntime.current.value!!.copy(effectiveMs = active.targetMs))
            recoveredRepository.finish("COMPLETED", "达到目标", active.id)
            assertTrue(database.studyDao().tasks().first().single().isCompleted)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun migrationElevenToTwelvePreservesSessionsTasksAndSegments() = runBlocking {
        val name = "studyguard-link-migration-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val schema = JSONObject(assets.open("com.focusforceplus.app.data.db.AppDatabase/11.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        val original = SQLiteDatabase.openOrCreateDatabase(path, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                original.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (offset in 0 until indices.length()) {
                    original.execSQL(indices.getJSONObject(offset).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            original.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            original.execSQL("INSERT INTO room_master_table VALUES(42, ?)", arrayOf(schema.getString("identityHash")))
            original.execSQL("INSERT INTO todos (id,title,priority,isCompleted,isRecurring,snoozeCount,maxSnoozeCount,rescheduleCount,maxRescheduleCount,createdAt) VALUES (42,'保留学习任务',1,0,0,0,2,0,1,1000)")
            original.execSQL("INSERT INTO study_tasks VALUES (7,42,'数学','2026-11-20',25,'MANUAL')")
            original.execSQL("INSERT INTO study_sessions VALUES ('old','数学','PAPER',60000,'PAUSED',1000,2500,2500,1500,NULL,'','','',30,0)")
            original.execSQL("INSERT INTO study_segments VALUES (1,'old','数学','2026-11-20',1000,1500,'USAGE_EVENTS_ONLY')")
            original.version = 11
        } finally { original.close() }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(StudyTaskLinkMigration).build()
        try {
            assertEquals(12, migrated.openHelper.writableDatabase.version)
            assertEquals("保留学习任务", migrated.studyDao().tasksOnce().single().let { migrated.todoDao().getById(it.todoId)?.title })
            val existing = migrated.studyDao().currentSession()!!
            assertEquals(1_500L, existing.effectiveMs)
            assertNull(existing.linkedTaskId)
            assertEquals(1_500L, migrated.studyDao().segmentsOnce().single().durationMs)
            migrated.studyDao().saveSession(existing.copy(linkedTaskId = 7))
            assertEquals(7L, migrated.studyDao().currentSession()?.linkedTaskId)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun backupRestoreRemapsTaskLinksAndDeletedTasksExportWithoutDanglingLinks() = runBlocking {
        val database = memoryDatabase()
        try {
            val repository = StudyRepository(database, StudyRuntime(context), OfflineStudyAiProvider())
            val taskId = repository.addTask("关联备份", "数学", "2026-11-20", 25)
            database.studyDao().saveSession(session().copy(
                linkedTaskId = taskId, state = "EARLY_END", endedWall = 2_000,
            ))
            val backup = repository.exportBackup("test")
            repository.restoreBackup(backup)
            val restoredTask = database.studyDao().tasks().first().single()
            assertNotEquals(taskId, restoredTask.task.id)
            assertEquals(restoredTask.task.id, database.studyDao().sessionsOnce().single().linkedTaskId)
            repository.deleteTask(restoredTask)
            val decoded = com.focusforceplus.app.study.backup.StudyBackupCodec.decode(repository.exportBackup("test"))
            assertTrue(decoded.tasks.isEmpty())
            assertNull(decoded.sessions.single().linkedTaskId)
        } finally { database.close() }
    }
}
