package com.example.pushapp.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.app.usage.UsageStats
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
            "CHECK_INSTAGRAM_USAGE" -> {
                AppLogger.i("AppLockService", "Manual Instagram usage check requested")
                checkInstagramUsageManually()
            }
            "FORCE_LOCK_INSTAGRAM" -> {
                AppLogger.i("AppLockService", "Force lock Instagram requested")
                forceLockInstagram()
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
            
            // Start the overlay service to monitor foreground apps
            val overlayIntent = Intent(this, com.example.pushapp.service.AppLockOverlayService::class.java)
            startService(overlayIntent)
            AppLogger.i("AppLockService", "Started overlay service")
            
            
            // Auto-lock Instagram for testing
            serviceScope.launch {
                autoLockInstagramForTesting()
            }
            
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
            
            // Get today's usage stats using the helper function
            val usageStats = getTodaysUsageStats(usageStatsManager)
            
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
            
                        // Process each app lock setting
            for (setting in appLockSettings) {
                if (setting.isLocked) {
                    AppLogger.d("AppLockService", "Skipping already locked app: ${setting.appName}")
                    continue
                }
                
                // Find usage stats for this app - try exact match first, then partial match
                var appUsage = usageStats.find { it.packageName == setting.packageName }
                
                // If no exact match, try to find by app name or partial package name
                if (appUsage == null) {
                    appUsage = usageStats.find { 
                        it.packageName.contains(setting.packageName.substringAfterLast(".")) ||
                        it.packageName.contains(setting.appName.lowercase().replace(" ", ""))
                    }
                }
                
                val timeUsedMinutes = if (appUsage != null) {
                    (appUsage.totalTimeInForeground / (1000 * 60)).toInt()
                } else {
                    0
                }
                
                AppLogger.d("AppLockService", "App: ${setting.appName} (${setting.packageName})")
                AppLogger.d("AppLockService", "  - Time used today: ${timeUsedMinutes}min")
                AppLogger.d("AppLockService", "  - Raw time in ms: ${appUsage?.totalTimeInForeground ?: 0}")
                AppLogger.d("AppLockService", "  - Daily limit: ${setting.dailyTimeLimit}min")
                AppLogger.d("AppLockService", "  - Last used: ${if (appUsage != null) java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(appUsage.lastTimeUsed)) else "Never"}")
                AppLogger.d("AppLockService", "  - App usage found: ${appUsage != null}")
                AppLogger.d("AppLockService", "  - Matched package: ${appUsage?.packageName}")
                
                // Debug: Show all available usage stats for debugging
                if (appUsage == null) {
                    AppLogger.d("AppLockService", "  - Available usage stats packages: ${usageStats.map { it.packageName }}")
                    val matchingStats = usageStats.filter { it.packageName.contains(setting.packageName.substringAfterLast(".")) }
                    if (matchingStats.isNotEmpty()) {
                        AppLogger.d("AppLockService", "  - Found similar packages: ${matchingStats.map { it.packageName }}")
                    }
                }
                
                // Update time used today in database
                val updatedSetting = setting.copy(timeUsedToday = timeUsedMinutes)
                try {
                    database.appLockDao().updateAppLockSettings(updatedSetting)
                    AppLogger.d("AppLockService", "Updated time used for ${setting.appName}: ${timeUsedMinutes}min")
                } catch (e: Exception) {
                    AppLogger.e("AppLockService", "Failed to update time used for ${setting.appName}", e)
                }
                
                // Check if app exceeded time limit
                if (timeUsedMinutes > setting.dailyTimeLimit && setting.dailyTimeLimit > 0) {
                    AppLogger.w("AppLockService", "App exceeded time limit: ${setting.appName} (${timeUsedMinutes}min > ${setting.dailyTimeLimit}min)")
                    
                    // App exceeded time limit, lock it immediately
                    val lockedSetting = updatedSetting.copy(isLocked = true)
                    try {
                        database.appLockDao().updateAppLockSettings(lockedSetting)
                        AppLogger.i("AppLockService", "App locked: ${setting.appName} (${timeUsedMinutes}min > ${setting.dailyTimeLimit}min)")
                        
                        // Show overlay to lock the app
                        AppLogger.i("AppLockService", "Triggering overlay for locked app: ${setting.appName}")
                        showAppLockedOverlay(setting.appName, setting.packageName, timeUsedMinutes, setting.dailyTimeLimit, setting.pushUpRequirement)
                        
                        // Also show notification
                        showAppLockedNotification(setting.appName, setting.packageName)
                    } catch (e: Exception) {
                        AppLogger.e("AppLockService", "Failed to lock app ${setting.appName}", e)
                    }
                } else {
                    AppLogger.d("AppLockService", "App within time limit: ${setting.appName} (${timeUsedMinutes}min <= ${setting.dailyTimeLimit}min)")
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
    
    /**
     * Helper function to get usage stats for a specific time range
     * Following the documented UsageStatsManager approach
     */
    private fun getUsageStatsForTimeRange(
        usageStatsManager: UsageStatsManager,
        startTime: Long,
        endTime: Long
    ): List<UsageStats> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                // Try multiple intervals to get more comprehensive data
                val allStats = mutableListOf<UsageStats>()
                
                // First try INTERVAL_BEST for the most accurate data
                val bestStats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST,
                    startTime,
                    endTime
                )
                bestStats?.let { allStats.addAll(it) }
                
                // Also try INTERVAL_DAILY as fallback
                val dailyStats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                )
                dailyStats?.let { allStats.addAll(it) }
                
                // Merge and deduplicate stats by package name, keeping the one with highest usage
                val mergedStats = allStats.groupBy { it.packageName }
                    .mapValues { (_, stats) ->
                        stats.maxByOrNull { it.totalTimeInForeground } ?: stats.first()
                    }
                    .values
                    .toList()
                
                AppLogger.d("AppLockService", "Retrieved ${mergedStats.size} usage stats entries (merged from ${allStats.size} total)")
                
                // Log some sample data for debugging
                mergedStats.take(5).forEach { stat ->
                    AppLogger.d("AppLockService", "Sample usage: ${stat.packageName} - ${stat.totalTimeInForeground}ms")
                }
                
                mergedStats
            } else {
                AppLogger.w("AppLockService", "UsageStatsManager not available on this Android version")
                emptyList()
            }
        } catch (e: Exception) {
            AppLogger.e("AppLockService", "Failed to query usage stats", e)
            emptyList()
        }
    }
    
    /**
     * Helper function to get today's usage stats
     */
    private fun getTodaysUsageStats(usageStatsManager: UsageStatsManager): List<UsageStats> {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        
        // Set start time to beginning of today (midnight)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        
        AppLogger.d("AppLockService", "Getting today's usage stats from ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(startTime))} to ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(endTime))}")
        
        // Also try getting stats from the last 24 hours to catch any edge cases
        val last24HoursStart = endTime - (24 * 60 * 60 * 1000) // 24 hours ago
        val stats24h = getUsageStatsForTimeRange(usageStatsManager, last24HoursStart, endTime)
        val statsToday = getUsageStatsForTimeRange(usageStatsManager, startTime, endTime)
        
        // Merge both results, preferring today's data but falling back to 24h data
        val allStats = mutableListOf<UsageStats>()
        allStats.addAll(statsToday)
        allStats.addAll(stats24h)
        
        // Deduplicate by package name, keeping the one with highest usage
        val mergedStats = allStats.groupBy { it.packageName }
            .mapValues { (_, stats) ->
                stats.maxByOrNull { it.totalTimeInForeground } ?: stats.first()
            }
            .values
            .toList()
        
        AppLogger.d("AppLockService", "Final merged stats: ${mergedStats.size} entries")
        return mergedStats
    }
    
    /**
     * Get usage stats for a specific app package
     */
    private fun getAppUsageStats(packageName: String): UsageStats? {
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val usageStats = getTodaysUsageStats(usageStatsManager)
            usageStats.find { it.packageName == packageName }
        } catch (e: Exception) {
            AppLogger.e("AppLockService", "Failed to get usage stats for $packageName", e)
            null
        }
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
    
    private fun checkInstagramUsageManually() {
        serviceScope.launch {
            try {
                AppLogger.i("AppLockService", "Manually checking Instagram usage...")
                
                // Get Instagram settings from database
                val instagramSettings = database.appLockDao().getAppLockSettings("com.instagram.android")
                if (instagramSettings == null) {
                    AppLogger.w("AppLockService", "Instagram not found in database")
                    return@launch
                }
                
                AppLogger.i("AppLockService", "Instagram settings: timeLimit=${instagramSettings.dailyTimeLimit}, isLocked=${instagramSettings.isLocked}")
                
                // Get current usage
                val usageDataService = UsageDataService(this@AppLockService)
                val usageData = usageDataService.getUsageChartData()
                val instagramUsage = usageData.topApps.find { it.packageName == "com.instagram.android" }
                
                if (instagramUsage != null) {
                    AppLogger.i("AppLockService", "Instagram usage found: ${instagramUsage.timeUsedMinutes} minutes")
                    
                    if (instagramUsage.timeUsedMinutes > instagramSettings.dailyTimeLimit) {
                        AppLogger.i("AppLockService", "Instagram exceeds time limit! Triggering lock...")
                        
                        // Update database to mark as locked
                        val lockedSetting = instagramSettings.copy(isLocked = true, timeUsedToday = instagramUsage.timeUsedMinutes)
                        database.appLockDao().updateAppLockSettings(lockedSetting)
                        
                        // Show overlay
                        showAppLockedOverlay(
                            instagramSettings.appName,
                            instagramSettings.packageName,
                            instagramUsage.timeUsedMinutes,
                            instagramSettings.dailyTimeLimit,
                            instagramSettings.pushUpRequirement
                        )
                        
                        // Show notification
                        showAppLockedNotification(instagramSettings.appName, instagramSettings.packageName)
                    } else {
                        AppLogger.i("AppLockService", "Instagram within time limit: ${instagramUsage.timeUsedMinutes} <= ${instagramSettings.dailyTimeLimit}")
                    }
                } else {
                    AppLogger.w("AppLockService", "No Instagram usage data found")
                }
            } catch (e: Exception) {
                AppLogger.e("AppLockService", "Error checking Instagram usage manually", e)
            }
        }
    }
    
    private fun forceLockInstagram() {
        serviceScope.launch {
            try {
                AppLogger.i("AppLockService", "Force locking Instagram...")
                
                // Get Instagram settings from database
                val instagramSettings = database.appLockDao().getAppLockSettings("com.instagram.android")
                if (instagramSettings == null) {
                    AppLogger.w("AppLockService", "Instagram not found in database")
                    return@launch
                }
                
                // Force lock Instagram regardless of usage
                val lockedSetting = instagramSettings.copy(isLocked = true, timeUsedToday = 999) // High number to ensure it's locked
                database.appLockDao().updateAppLockSettings(lockedSetting)
                
                AppLogger.i("AppLockService", "Instagram force locked in database")
                
                // Show overlay immediately
                showAppLockedOverlay(
                    instagramSettings.appName,
                    instagramSettings.packageName,
                    999,
                    instagramSettings.dailyTimeLimit,
                    instagramSettings.pushUpRequirement
                )
                
                // Show notification
                showAppLockedNotification(instagramSettings.appName, instagramSettings.packageName)
                
                AppLogger.i("AppLockService", "Instagram overlay and notification sent")
            } catch (e: Exception) {
                AppLogger.e("AppLockService", "Error force locking Instagram", e)
            }
        }
    }
    
    private fun autoLockInstagramForTesting() {
        serviceScope.launch {
            try {
                AppLogger.i("AppLockService", "Auto-locking Instagram for testing...")
                
                // Get Instagram settings from database
                val instagramSettings = database.appLockDao().getAppLockSettings("com.instagram.android")
                if (instagramSettings == null) {
                    AppLogger.w("AppLockService", "Instagram not found in database, creating default settings")
                    // Create default Instagram settings if not found
                    val defaultSettings = com.example.pushapp.data.AppLockSettings(
                        packageName = "com.instagram.android",
                        appName = "Instagram",
                        isLocked = true,
                        dailyTimeLimit = 60,
                        pushUpRequirement = 10,
                        timeUsedToday = 999 // High number to ensure it's locked
                    )
                    database.appLockDao().insertAppLockSettings(defaultSettings)
                    AppLogger.i("AppLockService", "Created default Instagram settings with lock enabled")
                } else {
                    // Update existing settings to be locked
                    val lockedSetting = instagramSettings.copy(isLocked = true, timeUsedToday = 999)
                    database.appLockDao().updateAppLockSettings(lockedSetting)
                    AppLogger.i("AppLockService", "Updated Instagram settings to be locked")
                }
                
                AppLogger.i("AppLockService", "Instagram is now permanently locked for testing")
                
                
            } catch (e: Exception) {
                AppLogger.e("AppLockService", "Error auto-locking Instagram", e)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        AppLogger.i("AppLockService", "Service destroyed")
    }
}
