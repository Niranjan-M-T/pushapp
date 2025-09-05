package com.example.pushapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.pushapp.R
import com.example.pushapp.utils.AppLogger
import com.example.pushapp.MainActivity
import java.util.*

/**
 * Service that displays persistent overlays to lock apps when time limits are exceeded.
 * This service creates full-screen overlays that prevent users from using locked apps.
 */
class AppLockOverlayService : Service() {
    
    private lateinit var windowManager: WindowManager
    private val overlayViews = mutableMapOf<String, View>()
    private val lockedApps = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var foregroundCheckRunnable: Runnable? = null
    private val lastOverlayAction = mutableMapOf<String, Long>()
    private val OVERLAY_DEBOUNCE_TIME = 1000L // 1 second debounce
    
    companion object {
        private const val TAG = "AppLockOverlayService"
        
        // Intent actions
        const val ACTION_SHOW_OVERLAY = "com.example.pushapp.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.example.pushapp.HIDE_OVERLAY"
        const val ACTION_HIDE_ALL_OVERLAYS = "com.example.pushapp.HIDE_ALL_OVERLAYS"
        const val ACTION_TEST_OVERLAY = "com.example.pushapp.TEST_OVERLAY"
        
        // Intent extras
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_TIME_USED = "time_used"
        const val EXTRA_TIME_LIMIT = "time_limit"
        const val EXTRA_PUSH_UP_REQUIREMENT = "push_up_requirement"
    }
    
    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "AppLockOverlayService created")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        AppLogger.i(TAG, "WindowManager initialized: $windowManager")
        
        // Check overlay permission on service creation
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        AppLogger.i(TAG, "Overlay permission on service creation: $hasPermission")
        
        startForegroundAppMonitoring()
        
        // Start as foreground service to keep it alive
        startForegroundService()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.i(TAG, "onStartCommand called with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                val appName = intent.getStringExtra(EXTRA_APP_NAME)
                val timeUsed = intent.getIntExtra(EXTRA_TIME_USED, 0)
                val timeLimit = intent.getIntExtra(EXTRA_TIME_LIMIT, 0)
                val pushUpRequirement = intent.getIntExtra(EXTRA_PUSH_UP_REQUIREMENT, 0)
                
                AppLogger.i(TAG, "Show overlay request: $appName ($packageName), timeUsed=$timeUsed, timeLimit=$timeLimit, pushUps=$pushUpRequirement")
                
                if (packageName != null && appName != null) {
                    showOverlay(packageName, appName, timeUsed, timeLimit, pushUpRequirement)
                    // Add to locked apps set for foreground monitoring
                    lockedApps.add(packageName)
                    AppLogger.i(TAG, "Added $packageName to locked apps set. Current locked apps: $lockedApps")
                } else {
                    AppLogger.e(TAG, "Missing required extras: packageName=$packageName, appName=$appName")
                }
            }
            ACTION_HIDE_OVERLAY -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                if (packageName != null) {
                    hideOverlay(packageName)
                    lockedApps.remove(packageName)
                }
            }
            ACTION_HIDE_ALL_OVERLAYS -> {
                hideAllOverlays()
                lockedApps.clear()
            }
            else -> {
                AppLogger.w(TAG, "Unknown action: ${intent?.action}")
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun requestOverlayPermission() {
        try {
            AppLogger.i(TAG, "Requesting overlay permission")
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = android.net.Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to request overlay permission", e)
        }
    }
    
    private fun showOverlay(
        packageName: String,
        appName: String,
        timeUsed: Int,
        timeLimit: Int,
        pushUpRequirement: Int
    ) {
        try {
            // Check overlay permission first
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (!android.provider.Settings.canDrawOverlays(this)) {
                    AppLogger.e(TAG, "Overlay permission not granted - cannot show overlay")
                    // Try to request permission
                    requestOverlayPermission()
                    return
                }
            }
            
            AppLogger.i(TAG, "Overlay permission granted, proceeding to show overlay")
            
            // Remove existing overlay for this package if it exists
            if (overlayViews.containsKey(packageName)) {
                AppLogger.i(TAG, "Overlay already exists for $packageName, removing it first")
                hideOverlay(packageName)
            }
            
            AppLogger.i(TAG, "Showing overlay for $appName ($packageName)")
            
            // Create the overlay view
            val overlayView = LayoutInflater.from(this).inflate(R.layout.app_lock_overlay, null)
            
            // Set up the overlay content
            setupOverlayContent(overlayView, appName, timeUsed, timeLimit, pushUpRequirement, packageName)
            
            // Create window parameters for full-screen overlay
            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                // Make overlay completely blocking and persistent
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                format = PixelFormat.OPAQUE
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
            
            // Add the overlay to the window manager
            try {
                AppLogger.i(TAG, "=== OVERLAY CREATION DEBUG ===")
                AppLogger.i(TAG, "Package: $packageName")
                AppLogger.i(TAG, "App Name: $appName")
                AppLogger.i(TAG, "WindowManager: $windowManager")
                AppLogger.i(TAG, "Overlay View: $overlayView")
                AppLogger.i(TAG, "Params: width=${params.width}, height=${params.height}")
                AppLogger.i(TAG, "Params: type=${params.type}, flags=${params.flags}")
                AppLogger.i(TAG, "Params: format=${params.format}, gravity=${params.gravity}")
                
                windowManager.addView(overlayView, params)
                AppLogger.i(TAG, "✅ Successfully added overlay view to WindowManager")
                
                // Store the overlay view for later removal
                overlayViews[packageName] = overlayView
                lockedApps.add(packageName)
                
                AppLogger.i(TAG, "✅ Overlay displayed successfully for $appName")
                AppLogger.i(TAG, "Current overlay views: ${overlayViews.keys}")
                AppLogger.i(TAG, "Current locked apps: $lockedApps")
            } catch (e: Exception) {
                AppLogger.e(TAG, "❌ FAILED to add overlay view to WindowManager", e)
                AppLogger.e(TAG, "Error type: ${e.javaClass.simpleName}")
                AppLogger.e(TAG, "Error message: ${e.message}")
                AppLogger.e(TAG, "Error stack trace: ${e.stackTrace.joinToString("\n")}")
                AppLogger.e(TAG, "WindowManager params: width=${params.width}, height=${params.height}, type=${params.type}, flags=${params.flags}")
                
                // Try to get more specific error information
                when (e) {
                    is SecurityException -> AppLogger.e(TAG, "SECURITY EXCEPTION: Overlay permission issue")
                    is IllegalArgumentException -> AppLogger.e(TAG, "ILLEGAL ARGUMENT: WindowManager parameters issue")
                    is WindowManager.BadTokenException -> AppLogger.e(TAG, "BAD TOKEN: WindowManager token issue")
                    else -> AppLogger.e(TAG, "UNKNOWN ERROR: ${e.javaClass.simpleName}")
                }
            }
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to show overlay for $appName", e)
        }
    }
    
    private fun setupOverlayContent(
        overlayView: View,
        appName: String,
        timeUsed: Int,
        timeLimit: Int,
        pushUpRequirement: Int,
        packageName: String
    ) {
        // Set app name
        overlayView.findViewById<TextView>(R.id.tvAppName).text = appName
        
        // Set time information
        overlayView.findViewById<TextView>(R.id.tvTimeInfo).text = 
            "Time used: ${timeUsed}min / ${timeLimit}min"
        
        // Set push-up requirement
        overlayView.findViewById<TextView>(R.id.tvPushUpRequirement).text = 
            "Complete $pushUpRequirement push-ups to unlock"
        
        // Set up unlock button
        overlayView.findViewById<Button>(R.id.btnUnlock).setOnClickListener {
            AppLogger.i(TAG, "Unlock button clicked for $appName")
            
            // Hide the overlay
            hideOverlay(packageName)
            
            // Launch push-up screen
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("openPushUpScreen", true)
                putExtra("lockedAppPackage", packageName)
            }
            startActivity(intent)
        }
        
        // Close button removed - overlay cannot be dismissed by user
    }
    
    private fun hideOverlay(packageName: String) {
        try {
            val overlayView = overlayViews[packageName]
            if (overlayView != null) {
                windowManager.removeView(overlayView)
                overlayViews.remove(packageName)
                // DON'T remove from lockedApps - keep the app locked!
                // lockedApps.remove(packageName)  // ← REMOVED THIS LINE
                AppLogger.i(TAG, "Overlay hidden for package: $packageName (app remains locked)")
                AppLogger.i(TAG, "Current locked apps: $lockedApps")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to hide overlay for package: $packageName", e)
        }
    }
    
    private fun hideOverlayTemporarily(packageName: String) {
        try {
            val overlayView = overlayViews[packageName]
            if (overlayView != null) {
                windowManager.removeView(overlayView)
                overlayViews.remove(packageName)
                // DON'T remove from lockedApps - this is just temporary hiding
                AppLogger.i(TAG, "Overlay temporarily hidden for package: $packageName (app remains locked)")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to temporarily hide overlay for package: $packageName", e)
        }
    }
    
    private fun hideAllOverlays() {
        try {
            overlayViews.values.forEach { overlayView ->
                try {
                    windowManager.removeView(overlayView)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to remove overlay view", e)
                }
            }
            overlayViews.clear()
            lockedApps.clear()
            AppLogger.i(TAG, "All overlays hidden")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to hide all overlays", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopForegroundAppMonitoring()
        hideAllOverlays()
        AppLogger.i(TAG, "AppLockOverlayService destroyed")
    }
    
    private fun startForegroundAppMonitoring() {
        foregroundCheckRunnable = object : Runnable {
            override fun run() {
                checkForegroundApp()
                handler.postDelayed(this, 500) // Check every 500ms for faster response
            }
        }
        handler.post(foregroundCheckRunnable!!)
        AppLogger.i(TAG, "Started aggressive foreground app monitoring")
    }
    
    private fun stopForegroundAppMonitoring() {
        foregroundCheckRunnable?.let { handler.removeCallbacks(it) }
        foregroundCheckRunnable = null
        AppLogger.i(TAG, "Stopped foreground app monitoring")
    }
    
    private fun checkForegroundApp() {
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val time = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                time - 5000, // Check last 5 seconds for more reliable detection
                time
            )
            
            AppLogger.i(TAG, "=== FOREGROUND APP CHECK ===")
            AppLogger.i(TAG, "Stats count: ${stats.size}")
            AppLogger.i(TAG, "Locked apps: $lockedApps")
            AppLogger.i(TAG, "Overlay views: ${overlayViews.keys}")
            
            if (stats.isNotEmpty()) {
                // Filter out our own app from foreground detection
                val filteredStats = stats.filter { it.packageName != "com.example.pushapp" }
                
                // Use the app with the most recent lastTimeUsed as foreground app
                val foregroundApp = filteredStats.maxByOrNull { it.lastTimeUsed }?.packageName
                AppLogger.i(TAG, "Foreground app detected: $foregroundApp")
                
                // Log all recent apps for debugging
                filteredStats.sortedByDescending { it.lastTimeUsed }.take(5).forEach { stat ->
                    AppLogger.d(TAG, "App: ${stat.packageName}, Last used: ${stat.lastTimeUsed}")
                }
                
                // First, hide all overlays for apps that are NOT in foreground
                val appsToHide = overlayViews.keys.filter { it != foregroundApp }
                appsToHide.forEach { packageName ->
                    val currentTime = System.currentTimeMillis()
                    val lastActionTime = lastOverlayAction[packageName] ?: 0
                    
                    if (currentTime - lastActionTime > OVERLAY_DEBOUNCE_TIME) {
                        AppLogger.i(TAG, "Hiding overlay for $packageName (not in foreground)")
                        hideOverlayTemporarily(packageName)
                        lastOverlayAction[packageName] = currentTime
                    } else {
                        AppLogger.d(TAG, "Debouncing overlay hide for $packageName")
                    }
                }
                
                // Then, show overlay only for the foreground app if it's locked
                // Never show overlay in our own app
                if (foregroundApp != null && foregroundApp != "com.example.pushapp" && lockedApps.contains(foregroundApp)) {
                    if (!overlayViews.containsKey(foregroundApp)) {
                        val currentTime = System.currentTimeMillis()
                        val lastActionTime = lastOverlayAction[foregroundApp] ?: 0
                        
                        if (currentTime - lastActionTime > OVERLAY_DEBOUNCE_TIME) {
                            AppLogger.i(TAG, "Locked app $foregroundApp is in foreground, showing overlay")
                            showOverlayForLockedApp(foregroundApp)
                            lastOverlayAction[foregroundApp] = currentTime
                        } else {
                            AppLogger.d(TAG, "Debouncing overlay creation for $foregroundApp")
                        }
                    } else {
                        AppLogger.d(TAG, "Overlay already exists for $foregroundApp")
                    }
                } else {
                    AppLogger.d(TAG, "Foreground app $foregroundApp is not locked or not in locked apps list")
                }
            } else {
                // No stats available, hide all overlays
                AppLogger.d(TAG, "No usage stats available, hiding all overlays")
                hideAllOverlays()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error checking foreground app", e)
        }
    }
    
    private fun showOverlayForLockedApp(packageName: String) {
        // Never show overlay in our own app
        if (packageName == "com.example.pushapp") {
            AppLogger.w(TAG, "Attempted to show overlay in PushApp - blocked")
            return
        }
        
        // This is a simplified version - in a real implementation, you'd get the app details from database
        val appName = packageName.substringAfterLast(".")
        AppLogger.i(TAG, "Showing blocking overlay for locked app: $appName ($packageName)")
        
        // For Instagram, use high values to ensure it's always locked
        if (packageName == "com.instagram.android") {
            showOverlay("Instagram", packageName, 999, 60, 10)
        } else {
            showOverlay(appName, packageName, 999, 60, 10) // Use high values for testing
        }
    }
    
    private fun startForegroundService() {
        try {
            // Create notification channel
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "overlay_service_channel",
                    "App Lock Overlay Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Service for monitoring locked apps"
                    setShowBadge(false)
                }
                
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            }
            
            val notification = NotificationCompat.Builder(this, "overlay_service_channel")
                .setContentTitle("App Lock Overlay Service")
                .setContentText("Monitoring locked apps")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()
            
            startForeground(1, notification)
            AppLogger.i(TAG, "Overlay service started as foreground service")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start foreground service", e)
        }
    }
}
