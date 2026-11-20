package com.focusforceplus.app.study.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object StudyMigration : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        statements.forEach(db::execSQL)
    }

    val statements = listOf(
        "CREATE TABLE IF NOT EXISTS study_profile (id INTEGER NOT NULL PRIMARY KEY, goal TEXT NOT NULL, subjects TEXT NOT NULL, examDay TEXT NOT NULL, dailyMinutes INTEGER NOT NULL, progressPercent INTEGER NOT NULL, checkMinutes INTEGER NOT NULL, blockedPackages TEXT NOT NULL, allowedPackages TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS study_tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, todoId INTEGER NOT NULL, subject TEXT NOT NULL, day TEXT NOT NULL, plannedMinutes INTEGER NOT NULL, source TEXT NOT NULL, FOREIGN KEY(todoId) REFERENCES todos(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_study_tasks_todoId ON study_tasks(todoId)",
        "CREATE INDEX IF NOT EXISTS index_study_tasks_day ON study_tasks(day)",
        "CREATE TABLE IF NOT EXISTS study_sessions (id TEXT NOT NULL PRIMARY KEY, subject TEXT NOT NULL, mode TEXT NOT NULL, targetMs INTEGER NOT NULL, state TEXT NOT NULL, startedWall INTEGER NOT NULL, lastWall INTEGER NOT NULL, lastElapsed INTEGER NOT NULL, effectiveMs INTEGER NOT NULL, endedWall INTEGER, endReason TEXT NOT NULL, blockedPackages TEXT NOT NULL, allowedPackages TEXT NOT NULL, checkMinutes INTEGER NOT NULL, lastCheckEffectiveMs INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS index_study_sessions_state ON study_sessions(state)",
        "CREATE INDEX IF NOT EXISTS index_study_sessions_startedWall ON study_sessions(startedWall)",
        "CREATE TABLE IF NOT EXISTS study_segments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId TEXT NOT NULL, subject TEXT NOT NULL, day TEXT NOT NULL, startedWall INTEGER NOT NULL, durationMs INTEGER NOT NULL, capability TEXT NOT NULL, FOREIGN KEY(sessionId) REFERENCES study_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        "CREATE INDEX IF NOT EXISTS index_study_segments_sessionId ON study_segments(sessionId)",
        "CREATE INDEX IF NOT EXISTS index_study_segments_day ON study_segments(day)",
        "CREATE TABLE IF NOT EXISTS study_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sessionId TEXT NOT NULL, kind TEXT NOT NULL, atWall INTEGER NOT NULL, detail TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS index_study_events_sessionId ON study_events(sessionId)",
        "CREATE INDEX IF NOT EXISTS index_study_events_atWall ON study_events(atWall)",
        "CREATE TABLE IF NOT EXISTS study_summaries (day TEXT NOT NULL PRIMARY KEY, content TEXT NOT NULL, generatedWall INTEGER NOT NULL, source TEXT NOT NULL)",
    )
}

object StudyTaskLinkMigration : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE study_sessions ADD COLUMN linkedTaskId INTEGER DEFAULT NULL")
    }
}
