package com.example.pushapp.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.pushapp.MainActivity
import com.example.pushapp.R
import com.example.pushapp.data.AppLockDatabase
import com.example.pushapp.utils.AppLogger
import kotlinx.coroutines.*

class AppLockService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var database: AppLockDatabase
    private var isMonitoring = false
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "AppLockChannel"
        
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
            null -> {
                // Default action - start monitoring if no specific action provided
                AppLogger.i("AppLockService", "No action provided, starting monitoring by default")
                startMonitoring()
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startMonitoring() {
        if (isMonitoring) {
            AppLogger.w("AppLockService", "⚠️ Monitoring already started, skipping")
            return
        }
        isMonitoring = true
        
        try {
            AppLogger.i("AppLockService", "=== SERVICE STARTUP DEBUG ===")
            AppLogger.i("AppLockService", "Starting monitoring...")
            
            // Start foreground service with proper error handling
            startForeground(NOTIFICATION_ID, createNotification())
            AppLogger.i("AppLockService", "✅ Started foreground service")
            
            // Start overlay service to monitor and show overlays
            startOverlayService()
            
            // Setup Instagram monitoring (no longer needed since AccessibilityService handles detection)
            serviceScope.launch {
                AppLogger.i("AppLockService", "Launching Instagram setup coroutine")
                setupInstagramMonitoring()
            }
            
            AppLogger.i("AppLockService", "✅ All services and coroutines started successfully")
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
    
    private suspend fun setupInstagramMonitoring() {
        try {
            AppLogger.i("AppLockService", "=== DATABASE CONFIGURATION DEBUG ===")
            AppLogger.i("AppLockService", "Setting up Instagram monitoring...")
            
            // Get database instance
            AppLogger.i("AppLockService", "Database: $database")
            AppLogger.i("AppLockService", "Database DAO: ${database.appLockDao()}")
            
            // Check if Instagram is already locked
            val instagramSettings = database.appLockDao().getAppLockSettings("com.instagram.android")
            
            if (instagramSettings != null) {
                AppLogger.i("AppLockService", "Instagram settings query result: $instagramSettings")
                AppLogger.i("AppLockService", "✅ Instagram settings found:")
                AppLogger.i("AppLockService", "  - Package: ${instagramSettings.packageName}")
                AppLogger.i("AppLockService", "  - App Name: ${instagramSettings.appName}")
                AppLogger.i("AppLockService", "  - Is Locked: ${instagramSettings.isLocked}")
                AppLogger.i("AppLockService", "  - Time Limit: ${instagramSettings.dailyTimeLimit}")
                AppLogger.i("AppLockService", "  - Time Used Today: ${instagramSettings.timeUsedToday}")
                AppLogger.i("AppLockService", "  - Push Up Requirement: ${instagramSettings.pushUpRequirement}")
                AppLogger.i("AppLockService", "✅ Instagram monitoring setup complete")
            } else {
                AppLogger.w("AppLockService", "❌ Instagram settings not found in database")
            }
        } catch (e: Exception) {
            AppLogger.e("AppLockService", "Failed to setup Instagram monitoring", e)
        }
    }
    
    private fun startOverlayService() {
        try {
            AppLogger.i("AppLockService", "Starting AppLockOverlayService...")
            val intent = Intent(this, com.example.pushapp.service.AppLockOverlayService::class.java)
            val result = startService(intent)
            AppLogger.i("AppLockService", "AppLockOverlayService start result: $result")
        } catch (e: Exception) {
            AppLogger.e("AppLockService", "Failed to start AppLockOverlayService", e)
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
