package com.example.pushapp.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class LogExporter(private val context: Context) {
    
    fun copyLogsToClipboard(logs: String): Boolean {
        return try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("App Logs", logs)
            clipboardManager.setPrimaryClip(clipData)
            AppLogger.i("LogExporter", "Logs copied to clipboard successfully")
            true
        } catch (e: Exception) {
            AppLogger.e("LogExporter", "Failed to copy logs to clipboard", e)
            false
        }
    }
    
    fun exportLogsToFile(logs: String): Uri? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "pushapp_logs_$timestamp.txt"
            
            // Create logs directory in external storage
            val logsDir = File(context.getExternalFilesDir(null), "logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }
            
            val logFile = File(logsDir, fileName)
            
            // Write logs to file
            FileWriter(logFile).use { writer ->
                writer.write("PushApp Logs - $timestamp\n")
                writer.write("=".repeat(50) + "\n\n")
                writer.write(logs)
            }
            
            AppLogger.i("LogExporter", "Logs exported to file: ${logFile.absolutePath}")
            
            // Return URI for sharing
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                logFile
            )
        } catch (e: Exception) {
            AppLogger.e("LogExporter", "Failed to export logs to file", e)
            null
        }
    }
    
    fun shareLogs(logs: String): Intent {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "pushapp_logs_$timestamp.txt"
        
        return try {
            // Create temporary file
            val tempFile = File(context.cacheDir, fileName)
            FileWriter(tempFile).use { writer ->
                writer.write("PushApp Logs - $timestamp\n")
                writer.write("=".repeat(50) + "\n\n")
                writer.write(logs)
            }
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "PushApp Logs - $timestamp")
                putExtra(Intent.EXTRA_TEXT, "PushApp usage logs and monitoring data")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            AppLogger.e("LogExporter", "Failed to create share intent", e)
            // Fallback to text sharing
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, logs)
                putExtra(Intent.EXTRA_SUBJECT, "PushApp Logs - $timestamp")
            }
        }
    }
    
    fun getLogsFromAppLogger(): String {
        return try {
            // This would need to be implemented in AppLogger to collect all logs
            // For now, return a placeholder
            "Log collection from AppLogger not yet implemented.\n" +
            "This would contain all the app's log entries.\n" +
            "Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}"
        } catch (e: Exception) {
            AppLogger.e("LogExporter", "Failed to get logs from AppLogger", e)
            "Error retrieving logs: ${e.message}"
        }
    }
}
