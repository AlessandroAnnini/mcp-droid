package com.alessandroannini.mcpdroid.infra

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(
    val id: Long = nextId.getAndIncrement(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val tag: String,
    val message: String,
    val detail: String? = null,
) {
    companion object {
        private val nextId = AtomicLong(0)
    }
}

object EventLog {
    private const val MAX_ENTRIES = 500
    private val buffer = ArrayDeque<LogEntry>(MAX_ENTRIES)
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    @Synchronized
    fun append(entry: LogEntry) {
        if (buffer.size >= MAX_ENTRIES) buffer.removeFirst()
        buffer.addLast(entry)
        _entries.value = buffer.toList()
    }

    fun info(tag: String, message: String, detail: String? = null) =
        append(LogEntry(level = LogLevel.INFO, tag = tag, message = message, detail = detail))

    fun warn(tag: String, message: String, detail: String? = null) =
        append(LogEntry(level = LogLevel.WARN, tag = tag, message = message, detail = detail))

    fun error(tag: String, message: String, detail: String? = null) =
        append(LogEntry(level = LogLevel.ERROR, tag = tag, message = message, detail = detail))

    @Synchronized
    fun clear() {
        buffer.clear()
        _entries.value = emptyList()
    }
}
