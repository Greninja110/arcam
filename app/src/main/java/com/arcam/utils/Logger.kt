package com.arcam.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class Logger private constructor(private val context: Context) {

    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logJob: Job? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val logFile: File by lazy {
        val logsDir = File(context.getExternalFilesDir(null), "logs")
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        File(logsDir, "arcam_log_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.txt")
    }

    companion object {
        @Volatile
        private var INSTANCE: Logger? = null

        fun getInstance(context: Context): Logger {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Logger(context).also { INSTANCE = it }
            }
        }
    }

    init {
        startLogWriter()
    }

    data class LogEntry(
        val level: String,
        val tag: String,
        val message: String,
        val throwable: Throwable?,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun d(tag: String, message: String) {
        log(LogLevel.DEBUG, tag, message, null)
    }

    fun i(tag: String, message: String) {
        log(LogLevel.INFO, tag, message, null)
    }

    fun w(tag: String, message: String) {
        log(LogLevel.WARNING, tag, message, null)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
    }

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        // Log to Android logcat
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARNING -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }

        // Add to file log queue
        logQueue.offer(LogEntry(level.name, tag, message, throwable))
    }

    private fun startLogWriter() {
        logJob = logScope.launch {
            while (isActive) {
                if (logQueue.isNotEmpty()) {
                    val batch = mutableListOf<LogEntry>()

                    // Collect up to 50 log entries at once
                    repeat(50) {
                        logQueue.poll()?.let { batch.add(it) }
                    }

                    if (batch.isNotEmpty()) {
                        writeLogsToFile(batch)
                    }
                }

                delay(100) // Check every 100ms
            }
        }
    }

    private suspend fun writeLogsToFile(logs: List<LogEntry>) = withContext(Dispatchers.IO) {
        try {
            FileWriter(logFile, true).use { writer ->
                logs.forEach { entry ->
                    val timestamp = dateFormat.format(Date(entry.timestamp))
                    val logLine = buildString {
                        append("[$timestamp] ")
                        append("[${entry.level}] ")
                        append("[${entry.tag}] ")
                        append(entry.message)

                        entry.throwable?.let {
                            append("\n")
                            append(it.stackTraceToString())
                        }
                        append("\n")
                    }
                    writer.write(logLine)
                }
                writer.flush()
            }
        } catch (e: Exception) {
            Log.e("Logger", "Failed to write logs to file", e)
        }
    }

    fun getLogFile(): File = logFile

    fun clearLogs() {
        logScope.launch {
            try {
                if (logFile.exists()) {
                    logFile.delete()
                }
                Log.i("Logger", "Logs cleared")
            } catch (e: Exception) {
                Log.e("Logger", "Failed to clear logs", e)
            }
        }
    }

    fun getLogStats(): Map<String, Any> {
        return mapOf(
            "queueSize" to logQueue.size,
            "logFileSize" to if (logFile.exists()) logFile.length() else 0L,
            "logFilePath" to logFile.absolutePath
        )
    }

    fun close() {
        logJob?.cancel()

        // Write any remaining logs
        if (logQueue.isNotEmpty()) {
            runBlocking {
                writeLogsToFile(logQueue.toList())
            }
        }
    }

    enum class LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }
}