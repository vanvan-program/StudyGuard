package com.focusforceplus.app.study.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class TaskStudyTime(val taskId: Long, val effectiveMs: Long)
data class PendingTaskReview(val sessionId: String, val taskId: Long, val title: String, val plannedMinutes: Int)
data class SessionFeedback(val sessionId: String, val distractions: Int, val latestCheck: String?)

@Dao
interface StudyDao {
    @Query("SELECT * FROM study_profile WHERE id = 1")
    fun profile(): Flow<StudyProfile?>

    @Query("SELECT * FROM study_profile WHERE id = 1")
    suspend fun profileOnce(): StudyProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: StudyProfile)

    @Insert
    suspend fun insertTask(task: StudyTask): Long

    @Query("SELECT * FROM study_tasks ORDER BY day, id")
    fun tasks(): Flow<List<StudyTask>>

    @Query("SELECT * FROM study_tasks WHERE id = :id")
    suspend fun taskById(id: Long): StudyTask?

    @Query("SELECT linkedTaskId AS taskId, SUM(effectiveMs) AS effectiveMs FROM study_sessions WHERE linkedTaskId IS NOT NULL GROUP BY linkedTaskId")
    fun taskStudyTimes(): Flow<List<TaskStudyTime>>

    @Query("""
        SELECT s.id AS sessionId, t.id AS taskId, t.title, t.plannedMinutes
        FROM study_events e JOIN study_sessions s ON e.sessionId = s.id
        JOIN study_tasks t ON t.id = s.linkedTaskId
        WHERE e.kind = 'TASK_REVIEW_PENDING' AND t.isCompleted = 0
        ORDER BY e.atWall, e.id LIMIT 1
    """)
    fun pendingTaskReview(): Flow<PendingTaskReview?>

    @Query("""
        UPDATE study_events SET kind = :kind, detail = :detail
        WHERE kind = 'TASK_REVIEW_PENDING' AND sessionId IN
          (SELECT id FROM study_sessions WHERE linkedTaskId = :taskId)
    """)
    suspend fun resolveTaskReviews(taskId: Long, kind: String, detail: String)

    @Query("UPDATE study_tasks SET title = :title, subject = :subject, day = :day, plannedMinutes = :minutes WHERE id = :id")
    suspend fun updateTask(id: Long, title: String, subject: String, day: String, minutes: Int)

    @Query("""
        UPDATE study_tasks SET isCompleted = NOT isCompleted,
            completedAt = CASE WHEN isCompleted = 0 THEN :now ELSE NULL END
        WHERE id = :id
    """)
    suspend fun toggleTask(id: Long, now: Long)

    @Query("UPDATE study_tasks SET isCompleted = 1, completedAt = :now WHERE id = :id AND isCompleted = 0")
    suspend fun completeTask(id: Long, now: Long)

    @Query("DELETE FROM study_tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("SELECT * FROM study_sessions ORDER BY startedWall DESC")
    fun sessions(): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions WHERE state IN ('RUNNING', 'PAUSED', 'INTERRUPTED') ORDER BY startedWall DESC LIMIT 1")
    suspend fun currentSession(): StudySession?

    @Upsert
    suspend fun saveSession(session: StudySession)

    @Insert
    suspend fun insertSegments(segments: List<StudySegment>)

    @Query("SELECT * FROM study_segments WHERE sessionId = :sessionId ORDER BY id DESC LIMIT 1")
    suspend fun lastSegment(sessionId: String): StudySegment?

    @Update
    suspend fun updateSegment(segment: StudySegment)

    @Query("SELECT * FROM study_segments ORDER BY startedWall")
    fun segments(): Flow<List<StudySegment>>

    @Query("SELECT * FROM study_segments WHERE day = :day")
    suspend fun segmentsForDay(day: String): List<StudySegment>

    @Insert
    suspend fun insertEvent(event: StudyEvent)

    @Query("SELECT * FROM study_events ORDER BY atWall DESC")
    fun recentEvents(): Flow<List<StudyEvent>>

    @Query("""
        SELECT s.id AS sessionId,
          (SELECT COUNT(*) FROM study_events WHERE sessionId = s.id AND kind = 'DISTRACTION') AS distractions,
          (SELECT detail FROM study_events WHERE sessionId = s.id AND kind = 'CHECK' ORDER BY atWall DESC, id DESC LIMIT 1) AS latestCheck
        FROM study_sessions s WHERE s.state IN ('RUNNING', 'PAUSED', 'INTERRUPTED')
        ORDER BY s.startedWall DESC LIMIT 1
    """)
    fun sessionFeedback(): Flow<SessionFeedback?>

    @Query("SELECT * FROM study_events WHERE atWall >= :start AND atWall < :end ORDER BY atWall")
    suspend fun eventsBetween(start: Long, end: Long): List<StudyEvent>

    @Query("SELECT * FROM study_summaries ORDER BY day DESC LIMIT 31")
    fun summaries(): Flow<List<StudySummary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSummary(summary: StudySummary)

    @Query("SELECT * FROM study_profile")
    suspend fun profilesOnce(): List<StudyProfile>

    @Query("SELECT * FROM study_tasks ORDER BY id")
    suspend fun tasksOnce(): List<StudyTask>

    @Query("SELECT * FROM study_sessions ORDER BY startedWall")
    suspend fun sessionsOnce(): List<StudySession>

    @Query("SELECT * FROM study_segments ORDER BY id")
    suspend fun segmentsOnce(): List<StudySegment>

    @Query("SELECT * FROM study_events ORDER BY id")
    suspend fun eventsOnce(): List<StudyEvent>

    @Query("SELECT * FROM study_summaries ORDER BY day")
    suspend fun summariesOnce(): List<StudySummary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<StudySession>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegmentsOnce(segments: List<StudySegment>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<StudyEvent>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummaries(summaries: List<StudySummary>)

    @Query("DELETE FROM study_events")
    suspend fun deleteAllEvents()

    @Query("DELETE FROM study_segments")
    suspend fun deleteAllSegments()

    @Query("DELETE FROM study_summaries")
    suspend fun deleteAllSummaries()

    @Query("DELETE FROM study_tasks")
    suspend fun deleteAllTasks()

    @Query("DELETE FROM study_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM study_profile")
    suspend fun deleteProfile()
}
