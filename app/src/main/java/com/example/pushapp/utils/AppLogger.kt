package com.example.pushapp.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Custom logging utility for the App Lock application
 * Provides structured logging with timestamps and log levels
 */
object AppLogger {
    private const val TAG = "AppLock"
    private const val MAX_LOG_SIZE = 1000 // Keep last 1000 log entries
    
    private val logEntries = mutableListOf<LogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }
    
    data class LogEntry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    )
    
    fun d(tag: String, message: String) {
        log(Level.DEBUG, tag, message)
        Log.d(TAG, "[$tag] $message")
    }
    
    fun i(tag: String, message: String) {
        log(Level.INFO, tag, message)
        Log.i(TAG, "[$tag] $message")
    }
    
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.WARN, tag, message, throwable)
        Log.w(TAG, "[$tag] $message", throwable)
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.ERROR, tag, message, throwable)
        Log.e(TAG, "[$tag] $message", throwable)
    }
    
    private fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )
        
        synchronized(logEntries) {
            logEntries.add(entry)
            if (logEntries.size > MAX_LOG_SIZE) {
                logEntries.removeAt(0)
            }
        }
    }
    
    fun getLogs(level: Level? = null, tag: String? = null): List<LogEntry> {
        return synchronized(logEntries) {
            logEntries.filter { entry ->
                (level == null || entry.level == level) &&
                (tag == null || entry.tag == tag)
            }
        }
    }
    
    fun getLogsAsString(level: Level? = null, tag: String? = null): String {
        val filteredLogs = getLogs(level, tag)
        return buildString {
            filteredLogs.forEach { entry ->
                appendLine("${dateFormat.format(Date(entry.timestamp))} [${entry.level}] [${entry.tag}] ${entry.message}")
                entry.throwable?.let { throwable ->
                    appendLine("  Exception: ${throwable.message}")
                    throwable.stackTrace.take(5).forEach { element ->
                        appendLine("    at $element")
                    }
                }
            }
        }
    }
    
    fun clearLogs() {
        synchronized(logEntries) {
            logEntries.clear()
        }
    }
    
    fun getLogCount(): Int {
        return synchronized(logEntries) {
            logEntries.size
        }
    }
}
