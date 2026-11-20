package com.focusforceplus.app.study.monitor

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focusforceplus.app.study.data.StudyRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StudyWorkerEntryPoint {
    fun studyRepository(): StudyRepository
}

class StudySummaryWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val repository = EntryPointAccessors.fromApplication(applicationContext, StudyWorkerEntryPoint::class.java)
            .studyRepository()
        repository.createSummary(LocalDate.now().minusDays(1))
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        const val UNIQUE_NAME = "studyguard_daily_summary"

        /** 计算距离下一次本地凌晨 00:10 的延迟毫秒数，确保昨日记录已完整落库。 */
        fun initialDelayMillis(now: ZonedDateTime = ZonedDateTime.now()): Long {
            val next = now.toLocalDate().plusDays(1).atTime(0, 10).atZone(now.zone)
            return Duration.between(now, next).toMillis().coerceAtLeast(60_000L)
        }
    }
}
