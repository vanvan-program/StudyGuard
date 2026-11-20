package com.focusforceplus.app.study.data

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.focusforceplus.app.data.db.entity.TodoEntity

@Entity(tableName = "study_profile")
data class StudyProfile(
    @PrimaryKey val id: Int = 1,
    val goal: String = "",
    val subjects: String = "",
    val examDay: String = "",
    val dailyMinutes: Int = 120,
    val progressPercent: Int = 0,
    val checkMinutes: Int = 30,
    val blockedPackages: String = "",
    val allowedPackages: String = "",
)

@Entity(
    tableName = "study_tasks",
    foreignKeys = [ForeignKey(
        entity = TodoEntity::class,
        parentColumns = ["id"],
        childColumns = ["todoId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("todoId", unique = true), Index("day")],
)
data class StudyTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val todoId: Long,
    val subject: String,
    val day: String,
    val plannedMinutes: Int,
    val source: String = "OFFLINE_RULES",
)

data class StudyTaskRow(
    @Embedded val task: StudyTask,
    val title: String,
    val isCompleted: Boolean,
)

@Entity(tableName = "study_sessions", indices = [Index("state"), Index("startedWall")])
data class StudySession(
    @PrimaryKey val id: String,
    val subject: String,
    val mode: String,
    val targetMs: Long,
    val state: String,
    val startedWall: Long,
    val lastWall: Long,
    val lastElapsed: Long,
    val effectiveMs: Long = 0,
    val endedWall: Long? = null,
    val endReason: String = "",
    val blockedPackages: String = "",
    val allowedPackages: String = "",
    val checkMinutes: Int = 30,
    val lastCheckEffectiveMs: Long = 0,
    @ColumnInfo(defaultValue = "NULL") val linkedTaskId: Long? = null,
)

@Entity(
    tableName = "study_segments",
    foreignKeys = [ForeignKey(
        entity = StudySession::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId"), Index("day")],
)
data class StudySegment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val subject: String,
    val day: String,
    val startedWall: Long,
    val durationMs: Long,
    val capability: String,
)

@Entity(tableName = "study_events", indices = [Index("sessionId"), Index("atWall")])
data class StudyEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val kind: String,
    val atWall: Long,
    val detail: String,
)

@Entity(tableName = "study_summaries")
data class StudySummary(
    @PrimaryKey val day: String,
    val content: String,
    val generatedWall: Long,
    val source: String = "OFFLINE_RULES",
)
