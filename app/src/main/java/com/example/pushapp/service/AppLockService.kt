package com.example.pushapp.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.app.usage.UsageStatsManager
import androidx.core.app.NotificationCompat
import com.example.pushapp.MainActivity
import com.example.pushapp.R
import com.example.pushapp.data.AppLockDatabase
import com.example.pushapp.data.AppLockSettings
import com.example.pushapp.utils.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.*

class AppLockService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var database: AppLockDatabase
    private var isMonitoring = false
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "AppLockChannel"
        private const val MONITORING_INTERVAL = 10000L // 10 seconds
        
        fun isServiceRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            return manager.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == AppLockService::class.java.name }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        database = AppLockDatabase.getInstance(this)
        createNotificationChannel()
        AppLogger.i("AppLockService", "Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_MONITORING" -> {
                startMonitoring()
                AppLogger.i("AppLockService", "Start monitoring command received")
            }
            "STOP_MONITORING" -> {
                stopMonitoring()
                AppLogger.i("AppLockService", "Stop monitoring command received")
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        
        try {
            // Start foreground service with proper error handling
            startForeground(NOTIFICATION_ID, createNotification())
            
            serviceScope.launch {
                while (isMonitoring) {
                    checkAppUsage()
                    delay(MONITORING_INTERVAL)
                }
            }
        } catch (e: SecurityException) {
            // Handle security exceptions (e.g., missing permissions)
            isMonitoring = false
            AppLogger.e("AppLockService", "Security exception when starting foreground service", e)
            throw e
        } catch (e: Exception) {
            // Handle other exceptions
            isMonitoring = false
            AppLogger.e("AppLockService", "Exception when starting foreground service", e)
            throw e
        }
    }
    
    private fun stopMonitoring() {
        isMonitoring = false
        stopForeground(true)
        stopSelf()
        AppLogger.i("AppLockService", "Monitoring stopped")
    }
    
    private suspend fun checkAppUsage() {
        try {
            // First check if we have the required permission
            val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOpsManager.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
            
            if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
                AppLogger.e("AppLockService", "Usage stats permission not granted. Mode: $mode")
                // Show notification to user about missing permission
                showPermissionRequiredNotification()
                return
            }
            
            AppLogger.i("AppLockService", "Usage stats permission granted, proceeding with monitoring")
            
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - (24 * 60 * 60 * 1000) // Last 24 hours
            
            val usageStats = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            } else {
                emptyList()
            }
            
            AppLogger.d("AppLockService", "Retrieved ${usageStats.size} usage stats entries")
            
            // Get actual app lock settings from database using Flow
            val appLockSettings = try {
                database.appLockDao().getAllAppLockSettings().first()
            } catch (e: Exception) {
                AppLogger.e("AppLockService", "Failed to get app lock settings from database", e)
                emptyList()
            }
            
            AppLogger.d("AppLockService", "Checking ${appLockSettings.size} app lock settings")
            
            if (appLockSettings.isEmpty()) {
                AppLogger.w("AppLockService", "No app lock settings found in database")
                return
            }
            
            for (setting in appLockSettings) {
                if (setting.isLocked) {
                    AppLogger.d("AppLockService", "Skipping already locked app: ${setting.appName}")
                    continue
                }
                
                val appUsage = usageStats.find { it.packageName == setting.packageName }
                val timeUsedMinutes = (appUsage?.totalTimeInForeground ?: 0) / (1000 * 60)
                
                AppLogger.d("AppLockService", "App: ${setting.appName}, Time used: ${timeUsedMinutes}min, Limit: ${setting.dailyTimeLimit}min")
                
                // Update time used today
                val updatedSetting = setting.copy(timeUsedToday = timeUsedMinutes.toInt())
                try {
                    database.appLockDao().updateAppLockSettings(updatedSetting)
                    AppLogger.d("AppLockService", "Updated time used for ${setting.appName}: ${timeUsedMinutes}min")
                } catch (e: Exception) {
                    AppLogger.e("AppLockService", "Failed to update time used for ${setting.appName}", e)
                }
                
                if (timeUsedMinutes > setting.dailyTimeLimit && setting.dailyTimeLimit > 0) {
                    // App exceeded time limit, lock it immediately
                    val lockedSetting = updatedSetting.copy(isLocked = true)
                    try {
                        database.appLockDao().updateAppLockSettings(lockedSetting)
                        AppLogger.i("AppLockService", "App locked: ${setting.appName} (${timeUsedMinutes}min > ${setting.dailyTimeLimit}min)")
                        
                                            // Show overlay to lock the app
                    showAppLockedOverlay(setting.appName, setting.packageName, timeUsedMinutes.toInt(), setting.dailyTimeLimit, setting.pushUpRequirement)
                    
                    // Also show notification
                    showAppLockedNotification(setting.appName, setting.packageName)
                    } catch (e: Exception) {
                        AppLogger.e("AppLockService", "Failed to lock app ${setting.appName}", e)
                    }
                }
            }
            
            // Reset daily usage at midnight
            val calendar = Calendar.getInstance()
            if (calendar.get(Calendar.HOUR_OF_DAY) == 0 && calendar.get(Calendar.MINUTE) == 0) {
                try {
                    database.appLockDao().resetDailyUsage(System.currentTimeMillis())
                    AppLogger.i("AppLockService", "Daily usage reset at midnight")
                } catch (e: Exception) {
                    AppLogger.e("AppLockService", "Failed to reset daily usage", e)
                }
            }
            
        } catch (e: Exception) {
            // Log the error for debugging
            AppLogger.e("AppLockService", "Error checking app usage", e)
        }
    }
    
    private fun showAppLockedNotification(appName: String, packageName: String) {
        // Create intent to open push-up screen
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("openPushUpScreen", true)
            putExtra("lockedAppPackage", packageName)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Locked: $appName")
            .setContentText("You've exceeded your daily time limit. Complete push-ups to unlock.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Start Push-ups",
                pendingIntent
            )
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
        
        AppLogger.i("AppLockService", "Notification sent for locked app: $appName")
    }
    
    private fun showAppLockedOverlay(
        appName: String,
        packageName: String,
        timeUsed: Int,
        timeLimit: Int,
        pushUpRequirement: Int
    ) {
        try {
            val intent = Intent(this, com.example.pushapp.service.AppLockOverlayService::class.java).apply {
                action = com.example.pushapp.service.AppLockOverlayService.ACTION_SHOW_OVERLAY
                putExtra(com.example.pushapp.service.AppLockOverlayService.EXTRA_PACKAGE_NAME, packageName)
                putExtra(com.example.pushapp.service.AppLockOverlayService.EXTRA_APP_NAME, appName)
                putExtra(com.example.pushapp.service.AppLockOverlayService.EXTRA_TIME_USED, timeUsed)
                putExtra(com.example.pushapp.service.AppLockOverlayService.EXTRA_TIME_LIMIT, timeLimit)
                putExtra(com.example.pushapp.service.AppLockOverlayService.EXTRA_PUSH_UP_REQUIREMENT, pushUpRequirement)
            }
            
            startService(intent)
            AppLogger.i("AppLockService", "Overlay service started for locked app: $appName")
            
        } catch (e: Exception) {
            AppLogger.e("AppLockService", "Failed to start overlay service for $appName", e)
        }
    }
    
    private fun showPermissionRequiredNotification() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Permission Required")
            .setContentText("Usage stats permission needed for app monitoring")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Grant Permission",
                pendingIntent
            )
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 2, notification)
        
        AppLogger.w("AppLockService", "Permission required notification sent")
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Lock Active")
            .setContentText("Monitoring app usage and enforcing time limits")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Lock Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when app lock service is running"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        AppLogger.i("AppLockService", "Service destroyed")
    }
}
