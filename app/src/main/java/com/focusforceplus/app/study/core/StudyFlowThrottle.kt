package com.focusforceplus.app.study.core

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 仅在 [shouldThrottle] 为 true（例如学习监督高频计时中）时对高频事件按 [intervalMs] 节流；
 * 非运行态或退出运行态时立即透传最新值，确保勾选任务、结束学习等操作在统计页即时反映。
 */
internal fun <T> Flow<T>.throttleWhile(
    intervalMs: Long,
    shouldThrottle: Flow<Boolean>,
    initialThrottling: Boolean = false,
    elapsedMs: () -> Long,
): Flow<T> = channelFlow {
    var latest: T? = null
    var hasPending = false
    var throttling = initialThrottling
    var lastEmitAt = elapsedMs() - intervalMs
    var timerJob: Job? = null
    val mutex = Mutex()

    fun flushLocked() {
        timerJob?.cancel()
        timerJob = null
        if (!hasPending) return
        @Suppress("UNCHECKED_CAST")
        val value = latest as T
        lastEmitAt = elapsedMs()
        hasPending = false
        trySend(value)
    }

    val throttleJob = launch {
        shouldThrottle.distinctUntilChanged().collect { active ->
            mutex.withLock {
                val enteredThrottle = !throttling && active
                throttling = active
                if (enteredThrottle) {
                    lastEmitAt = elapsedMs() - intervalMs
                } else if (!active && hasPending) {
                    flushLocked()
                }
            }
        }
    }

    try {
        collect { value ->
            mutex.withLock {
                latest = value
                hasPending = true
                val elapsed = elapsedMs() - lastEmitAt
                if (!throttling || elapsed >= intervalMs) {
                    flushLocked()
                } else if (timerJob?.isActive != true) {
                    val waitMs = (intervalMs - elapsed).coerceAtLeast(0L)
                    timerJob = launch {
                        delay(waitMs)
                        mutex.withLock { flushLocked() }
                    }
                }
            }
        }
        mutex.withLock { flushLocked() }
    } finally {
        throttleJob.cancel()
        timerJob?.cancel()
    }
}.buffer(Channel.UNLIMITED)
