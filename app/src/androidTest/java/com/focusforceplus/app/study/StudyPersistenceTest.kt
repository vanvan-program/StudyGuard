package com.focusforceplus.app.study

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.focusforceplus.app.data.db.AppDatabase
import com.focusforceplus.app.data.db.entity.TodoEntity
import com.focusforceplus.app.data.repository.SettingsRepository
import com.focusforceplus.app.data.repository.SleepRepository
import com.focusforceplus.app.study.ai.OfflineStudyAiProvider
import com.focusforceplus.app.study.data.*
import com.focusforceplus.app.study.monitor.StudyRuntime
import com.focusforceplus.app.study.ui.StudyViewModel
import com.focusforceplus.app.util.AlarmHelper
import com.focusforceplus.app.util.BackupManager
import com.focusforceplus.app.util.FocusAlarmHelper
import com.focusforceplus.app.util.InstalledAppsHelper
import com.focusforceplus.app.util.SleepAlarmHelper
import com.focusforceplus.app.util.TodoAlarmHelper
import com.focusforceplus.app.util.TodoNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StudyPersistenceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun usageMode(): String {
        val ops = context.getSystemService(android.app.AppOpsManager::class.java)
        return when (ops.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), context.packageName)) {
            android.app.AppOpsManager.MODE_ALLOWED -> "allow"
            android.app.AppOpsManager.MODE_IGNORED -> "ignore"
            android.app.AppOpsManager.MODE_ERRORED -> "deny"
            else -> "default"
        }
    }

    private fun setUsageMode(mode: String) {
        val result = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd appops set ${context.packageName} GET_USAGE_STATS $mode")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(result).use { it.readBytes() }
    }

    @Test fun missingUsagePermissionNeverCreatesASession() = runBlocking {
        val original = usageMode()
        val database = memoryDatabase()
        try {
            setUsageMode("ignore")
            val runtime = StudyRuntime(context)
            val repository = StudyRepository(database, runtime, OfflineStudyAiProvider())
            assertTrue(runCatching { repository.start("数学", "PAPER", 5, runtime.beginRequest()) }.isFailure)
            assertTrue(database.studyDao().sessionsOnce().isEmpty())
            assertTrue(database.studyDao().eventsOnce().isEmpty())
        } finally {
            setUsageMode(original)
            database.close()
        }
    }

    private fun memoryDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    private fun session(id: String = "test") = StudySession(
        id = id, subject = "数学", mode = "PAPER", targetMs = 60_000,
        state = "RUNNING", startedWall = 1_000, lastWall = 1_000, lastElapsed = 1_000,
    )

    private fun createDatabaseFromSchema(name: String, version: Int, seed: (SQLiteDatabase) -> Unit) {
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val schema = JSONObject(assets.open("com.focusforceplus.app.data.db.AppDatabase/$version.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { original ->
            original.setForeignKeyConstraintsEnabled(true)
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
            seed(original)
            original.version = version
        }
    }

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

    @Test fun migrationTenToThirteenPreservesOriginalTodos() = runBlocking {
        val name = "studyguard-migration-${UUID.randomUUID()}.db"
        createDatabaseFromSchema(name, 10) { original ->
            original.execSQL("INSERT INTO todos (id,title,priority,isCompleted,isRecurring,snoozeCount,maxSnoozeCount,rescheduleCount,maxRescheduleCount,createdAt) VALUES (42,'保留旧任务',1,0,0,0,2,0,1,1000)")
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(StudyMigration, StudyTaskLinkMigration, StudyTaskIsolationMigration).build()
        try {
            assertEquals("保留旧任务", migrated.todoDao().getById(42)?.title)
            assertEquals(13, migrated.openHelper.writableDatabase.version)
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

    @Test fun studyTaskEditsAndDeletionLeaveSameIdTodoUntouched() = runBlocking {
        val database = memoryDatabase()
        try {
            val repository = StudyRepository(database, StudyRuntime(context), OfflineStudyAiProvider())
            repository.acceptPlan(listOf(com.focusforceplus.app.study.ai.PlannedTask("做题", "数学", "2026-11-10", 25)))
            val task = database.studyDao().tasks().first().single()
            assertTrue(database.todoDao().getAllOnce().isEmpty())
            val todo = TodoEntity(id = task.id, title = task.title, createdAt = 1_000)
            database.todoDao().insert(todo)
            repository.updateTask(task.id, "复习错题", "物理", "2026-11-11", 30)
            assertEquals("复习错题", database.studyDao().tasks().first().single().title)
            assertEquals("2026-11-11", database.studyDao().tasks().first().single().day)
            repository.toggleTask(task.id)
            val completed = database.studyDao().tasks().first().single()
            assertTrue(completed.isCompleted)
            assertNotNull(completed.completedAt)
            repository.toggleTask(task.id)
            assertNull(database.studyDao().taskById(task.id)?.completedAt)
            repository.deleteTask(task.id)
            assertTrue(database.studyDao().tasks().first().isEmpty())
            assertEquals(listOf(todo), database.todoDao().getAllOnce())
        } finally { database.close() }
    }

    @Test fun legacyTodoDeletionCleanupAndBackupRestorePreserveStudyData() = runBlocking {
        val database = memoryDatabase()
        val scope = CoroutineScope(Dispatchers.IO)
        val storeFile = File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb")
        val backupFile = File.createTempFile("legacy-todos", ".json", context.cacheDir)
        try {
            val study = database.studyDao()
            val todos = database.todoDao()
            val repository = StudyRepository(database, StudyRuntime(context), OfflineStudyAiProvider())
            val pendingId = repository.addTask("同名待办", "数学", "2026-11-12", 30)
            val completedId = repository.addTask("旧完成任务", "数学", "2026-11-12", 30)
            study.completeTask(completedId, 2_000)
            val ended = session().copy(linkedTaskId = completedId, state = "COMPLETED", endedWall = 61_000)
            study.saveSession(ended)
            study.insertSegments(listOf(StudySegment(
                sessionId = ended.id, subject = ended.subject, day = "2026-11-12",
                startedWall = 1_000, durationMs = 60_000, capability = "USAGE_EVENTS_ONLY",
            )))
            val tasksBefore = study.tasksOnce()
            val segmentsBefore = study.segmentsOnce()

            val matching = TodoEntity(id = pendingId, title = "同名待办", createdAt = 1_000)
            todos.insert(matching)
            todos.insert(TodoEntity(id = completedId, title = "旧完成待办", isCompleted = true,
                createdAt = 1_000, completedAt = 2_000))
            todos.delete(matching)
            todos.deleteCompletedBefore(3_000)
            assertTrue(todos.getAllOnce().isEmpty())
            assertEquals(tasksBefore, study.tasksOnce())

            val restored = matching.copy(title = "备份里的待办")
            val store = PreferenceDataStoreFactory.create(scope = scope) { storeFile }
            val manager = BackupManager(
                context, database, database.routineDao(), database.routineTaskDao(), todos,
                database.blockedAppDao(), database.blockerGroupDao(), database.focusSessionDao(),
                database.routineCompletionDao(), database.focusCompletionDao(), SettingsRepository(store),
                SleepRepository(store), AlarmHelper(context), TodoAlarmHelper(context, TodoNotificationHelper(context)),
                FocusAlarmHelper(context), SleepAlarmHelper(context),
            )
            val fileUri = Uri.fromFile(backupFile)
            todos.insert(restored)
            manager.exportTo(fileUri)
            todos.insert(matching)
            manager.importFrom(fileUri)
            assertEquals(listOf(restored), todos.getAllOnce())
            assertEquals(tasksBefore, study.tasksOnce())
            assertEquals(listOf(ended), study.sessionsOnce())
            assertEquals(segmentsBefore, study.segmentsOnce())
        } finally {
            scope.cancel()
            database.close()
            storeFile.delete()
            backupFile.delete()
        }
        Unit
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
            val repository = StudyRepository(database, StudyRuntime(context), OfflineStudyAiProvider())
            val taskId = repository.addTask("持久化学习任务", "数学", "2026-11-12", 30)
            repository.completeTask(taskId)
            val taskBefore = database.studyDao().taskById(taskId)
            database.close()
            database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            assertEquals("持久化", database.studyDao().profileOnce()?.goal)
            assertEquals("离线任务", database.todoDao().getAllOnce().single().title)
            assertEquals(taskBefore, database.studyDao().tasksOnce().single())
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
            val taskId = repository.addTask("备份任务", "数学", "2026-11-12", 30)
            repository.completeTask(taskId)
            val savedTask = database.studyDao().taskById(taskId)!!
            val unrelatedId = database.todoDao().insert(TodoEntity(title = "原版普通待办", createdAt = 1_000))
            val backup = repository.exportBackup("test")

            database.studyDao().saveProfile(StudyProfile(goal = "备份后目标", examDay = "2026-12-11"))
            repository.deleteTask(database.studyDao().tasks().first().single().id)
            val summary = repository.restoreBackup(backup)

            assertEquals("备份前目标", database.studyDao().profileOnce()?.goal)
            val restoredTask = database.studyDao().tasks().first().single()
            assertEquals(savedTask.copy(id = restoredTask.id), restoredTask)
            assertEquals(1, summary.tasks)
            assertEquals(0, summary.sessions)
            assertEquals("原版普通待办", database.todoDao().getAllOnce().single().also {
                assertEquals(unrelatedId, it.id)
            }.title)
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
            assertTrue(tasks.all { it.source == "MANUAL" })

            repository.completeTask(firstId)
            val updated = database.studyDao().tasks().first().associateBy { it.id }
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

            // Finishing a one-minute segment only credits that segment to the 25-minute task.
            database.studyDao().saveSession(session("done").copy(effectiveMs = 60_000, linkedTaskId = taskId))
            repository.finish("COMPLETED", "达到目标", "done")
            val firstReview = database.studyDao().pendingTaskReview().first()!!
            assertFalse(database.studyDao().tasks().first().single().isCompleted)
            repository.reviewTask(firstReview, false)
            assertNull(database.studyDao().pendingTaskReview().first())
            database.studyDao().saveSession(session("second").copy(effectiveMs = 60_000, linkedTaskId = taskId))
            repository.finish("COMPLETED", "达到目标", "second")
            val secondReview = database.studyDao().pendingTaskReview().first()!!
            repository.reviewTask(firstReview, true)
            assertFalse(database.studyDao().tasks().first().single().isCompleted)
            assertEquals(120_000L, database.studyDao().taskStudyTimes().first().single().effectiveMs)
            repository.reviewTask(secondReview, true)
            assertTrue(database.studyDao().tasks().first().single().isCompleted)
            assertNull(database.studyDao().pendingTaskReview().first())
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
        val originalMode = usageMode()
        var database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val runtime = StudyRuntime(context)
            val repository = StudyRepository(database, runtime, OfflineStudyAiProvider())
            setUsageMode("allow")
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
            assertFalse(database.studyDao().tasks().first().single().isCompleted)
            database.close()
            database = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            assertEquals(taskId, database.studyDao().pendingTaskReview().first()!!.taskId)
        } finally {
            setUsageMode(originalMode)
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun migrationElevenToThirteenPreservesSessionsTasksAndSegments() = runBlocking {
        val name = "studyguard-link-migration-${UUID.randomUUID()}.db"
        createDatabaseFromSchema(name, 11) { original ->
            original.execSQL("INSERT INTO todos (id,title,priority,isCompleted,isRecurring,snoozeCount,maxSnoozeCount,rescheduleCount,maxRescheduleCount,createdAt) VALUES (42,'保留学习任务',1,0,0,0,2,0,1,1000)")
            original.execSQL("INSERT INTO study_tasks VALUES (7,42,'数学','2026-11-20',25,'MANUAL')")
            original.execSQL("INSERT INTO study_sessions VALUES ('old','数学','PAPER',60000,'PAUSED',1000,2500,2500,1500,NULL,'','','',30,0)")
            original.execSQL("INSERT INTO study_segments VALUES (1,'old','数学','2026-11-20',1000,1500,'USAGE_EVENTS_ONLY')")
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(StudyTaskLinkMigration, StudyTaskIsolationMigration).build()
        try {
            assertEquals(13, migrated.openHelper.writableDatabase.version)
            assertEquals("保留学习任务", migrated.studyDao().tasksOnce().single().title)
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

    @Test fun migrationTwelveToThirteenMovesOnlyLinkedTodosAndPreservesSessionAssociation() = runBlocking {
        val name = "studyguard-task-isolation-${UUID.randomUUID()}.db"
        createDatabaseFromSchema(name, 12) { original ->
            original.execSQL("""
                INSERT INTO todos (id,title,description,priority,isCompleted,isRecurring,snoozeCount,
                    maxSnoozeCount,rescheduleCount,maxRescheduleCount,createdAt,completedAt)
                VALUES (7,'同名待办','StudyGuard · 数学',1,0,0,0,2,0,1,1000,NULL),
                       (42,'同名待办','StudyGuard · 数学',1,1,0,0,2,0,1,1000,2000),
                       (43,'尚未完成',NULL,1,0,0,0,2,0,1,3000,NULL)
            """)
            original.execSQL("""
                INSERT INTO study_tasks VALUES (7,42,'数学','2026-11-20',25,'MANUAL'),
                    (8,43,'英语','2026-11-21',30,'OFFLINE_RULES')
            """)
            original.execSQL("UPDATE sqlite_sequence SET seq = 99 WHERE name = 'study_tasks'")
            original.execSQL("INSERT INTO study_sessions VALUES ('old','英语','PAPER',60000,'PAUSED',1000,2500,2500,1500,NULL,'','','',30,0,8)")
            original.execSQL("INSERT INTO study_segments VALUES (1,'old','英语','2026-11-20',1000,1500,'USAGE_EVENTS_ONLY')")
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(StudyTaskIsolationMigration).build()
        try {
            val study = migrated.studyDao()
            assertEquals(listOf(
                StudyTask(7, "同名待办", "数学", "2026-11-20", 25, "MANUAL", true, 1_000, 2_000),
                StudyTask(8, "尚未完成", "英语", "2026-11-21", 30, "OFFLINE_RULES", false, 3_000),
            ), study.tasksOnce())
            assertEquals(7L, migrated.todoDao().getAllOnce().single().id)
            assertEquals(8L, study.currentSession()?.linkedTaskId)
            assertEquals(1_500L, study.segmentsOnce().single().durationMs)

            val repository = StudyRepository(migrated, StudyRuntime(context), OfflineStudyAiProvider())
            val newId = repository.addTask("迁移后的新任务", "英语", "2026-11-21", 30)
            assertTrue(newId > 99L)
            repository.finish("EARLY_END", expectedId = "old", completeLinkedTask = true)
            assertTrue(study.taskById(8)!!.isCompleted)
            assertFalse(study.taskById(newId)!!.isCompleted)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun migrationWithNoRemainingTasksDoesNotReuseHistoricalTaskIds() = runBlocking {
        val name = "studyguard-empty-isolation-${UUID.randomUUID()}.db"
        createDatabaseFromSchema(name, 12) { original ->
            original.execSQL("INSERT INTO sqlite_sequence (name,seq) VALUES ('study_tasks',99)")
            original.execSQL("INSERT INTO study_sessions VALUES ('deleted-task','数学','PAPER',60000,'PAUSED',1000,2500,2500,1500,NULL,'','','',30,0,99)")
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(StudyTaskIsolationMigration).build()
        try {
            val repository = StudyRepository(migrated, StudyRuntime(context), OfflineStudyAiProvider())
            val newId = repository.addTask("迁移后的新任务", "数学", "2026-11-21", 30)
            assertTrue(newId > 99L)
            repository.finish("EARLY_END", expectedId = "deleted-task", completeLinkedTask = true)
            assertFalse(migrated.studyDao().taskById(newId)!!.isCompleted)
            assertEquals(99L, migrated.studyDao().sessionsOnce().single().linkedTaskId)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun completingTaskTwiceKeepsOriginalCompletionTime() = runBlocking {
        val database = memoryDatabase()
        try {
            val repository = StudyRepository(database, StudyRuntime(context), OfflineStudyAiProvider())
            val id = repository.addTask("已完成的任务", "数学", "2026-11-21", 30)
            database.studyDao().completeTask(id, 2_000)
            database.studyDao().completeTask(id, 3_000)
            assertEquals(2_000L, database.studyDao().taskById(id)!!.completedAt)
            repository.toggleTask(id)
            val reopened = database.studyDao().taskById(id)!!
            assertFalse(reopened.isCompleted)
            assertNull(reopened.completedAt)
        } finally { database.close() }
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
            assertNotEquals(taskId, restoredTask.id)
            assertEquals(restoredTask.id, database.studyDao().sessionsOnce().single().linkedTaskId)
            repository.deleteTask(restoredTask.id)
            val decoded = com.focusforceplus.app.study.backup.StudyBackupCodec.decode(repository.exportBackup("test"))
            assertTrue(decoded.tasks.isEmpty())
            assertNull(decoded.sessions.single().linkedTaskId)
        } finally { database.close() }
    }

    @Test fun schemaElevenAndTwelveStudyBackupsRestoreWithoutChangingLegacyTodos() = runBlocking {
        val database = memoryDatabase()
        try {
            val repository = StudyRepository(database, StudyRuntime(context), OfflineStudyAiProvider())
            val ordinary = TodoEntity(id = 42, title = "同 ID 的普通待办", createdAt = 1_000)
            database.todoDao().insert(ordinary)
            val assets = InstrumentationRegistry.getInstrumentation().context.assets
            for (version in listOf(11, 12)) {
                val text = assets.open("studyguard-schema$version-backup.json").bufferedReader().use { it.readText() }
                val decoded = com.focusforceplus.app.study.backup.StudyBackupCodec.decode(text)
                repository.restoreBackup(text)
                val restored = database.studyDao().tasksOnce().single()
                val source = decoded.tasks.single()
                assertEquals(source.title, restored.title)
                assertEquals(source.isCompleted, restored.isCompleted)
                assertEquals(source.createdAt, restored.createdAt)
                assertEquals(source.completedAt, restored.completedAt)
                assertEquals(listOf(ordinary), database.todoDao().getAllOnce())
                if (version == 12) {
                    assertEquals(restored.id, database.studyDao().sessionsOnce().single().linkedTaskId)
                }
            }
            val before = database.studyDao().tasksOnce()
            val corruptBackup = repository.exportBackup("test").replace("旧版已完成学习任务", "损坏标题")
            try {
                repository.restoreBackup(corruptBackup)
                fail("损坏备份应在修改数据之前被拒绝")
            } catch (_: IllegalArgumentException) {
                assertEquals(before, database.studyDao().tasksOnce())
                assertEquals(listOf(ordinary), database.todoDao().getAllOnce())
            }
        } finally { database.close() }
    }
}
