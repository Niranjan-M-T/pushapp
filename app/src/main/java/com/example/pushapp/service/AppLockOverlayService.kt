package com.example.pushapp.service

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.example.pushapp.data.AppLockDatabase
import com.example.pushapp.utils.AccessibilityHelper
import com.example.pushapp.utils.AppLogger
import com.example.pushapp.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.util.*

/**
 * Service that displays persistent overlays to lock apps when time limits are exceeded.
 * This service creates full-screen overlays that prevent users from using locked apps.
 */
class AppLockOverlayService : Service() {
    
    private lateinit var windowManager: WindowManager
    private lateinit var database: AppLockDatabase
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val overlayViews = mutableMapOf<String, View>()
    private val handler = Handler(Looper.getMainLooper())
    private var foregroundCheckRunnable: Runnable? = null
    
    // Performance optimization: Cache frequently accessed data
    private var lastForegroundApp: String? = null
    private val stickyOverlays = mutableSetOf<String>() // Apps that should keep overlay visible
    private var lastLauncherPackage: String? = null
    private var cachedLockedApps = mutableMapOf<String, Boolean>()
    
    // App closure detection
    private var nullForegroundCount = 0 // Count consecutive null foreground detections
    private val maxNullForegroundCount = 2 // Hide overlay after 2 consecutive null detections (2 seconds)
    
    // Removed debounce mechanism for true persistence
    
    companion object {
        private const val TAG = "AppLockOverlayService"
    }
    
    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "AppLockOverlayService created")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        database = AppLockDatabase.getInstance(this)
        AppLogger.i(TAG, "WindowManager and Database initialized")
        
        // Check overlay permission on service creation
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        AppLogger.i(TAG, "Overlay permission on service creation: $hasPermission")
        
        // Determine which monitoring system to use
        if (shouldUsePollingFallback()) {
            AppLogger.i(TAG, "Using polling-based monitoring as fallback (AccessibilityService not available)")
            startForegroundAppMonitoring()
        } else {
            AppLogger.i(TAG, "Using AccessibilityService for instant app detection (polling disabled)")
        }
        
        // Start as foreground service to keep it alive
        startForegroundService()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        AppLogger.i(TAG, "onStartCommand called with action: $action")
        
        when (action) {
            "SHOW_OVERLAY" -> {
                val packageName = intent.getStringExtra("packageName")
                val appName = intent.getStringExtra("appName")
                val timeUsed = intent.getIntExtra("timeUsed", 0)
                val timeLimit = intent.getIntExtra("timeLimit", 60)
                val pushUpRequirement = intent.getIntExtra("pushUpRequirement", 10)
                
                if (packageName != null && appName != null) {
                    AppLogger.i(TAG, "AccessibilityService requested overlay for: $appName")
                    showOverlay(packageName, appName, timeUsed, timeLimit, pushUpRequirement)
                }
            }
            "HIDE_ALL_OVERLAYS" -> {
                AppLogger.i(TAG, "AccessibilityService requested to hide all overlays")
                hideAllOverlays()
            }
            else -> {
                // Check if we should use polling fallback
                if (shouldUsePollingFallback()) {
                    AppLogger.i(TAG, "Starting polling-based monitoring as fallback")
                    startForegroundAppMonitoring()
                } else {
                    AppLogger.i(TAG, "AccessibilityService is active - polling-based monitoring not needed")
                }
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * Determines if we should use polling-based monitoring as a fallback.
     * This should only be used when AccessibilityService is not available or not accepted by the user.
     */
    private fun shouldUsePollingFallback(): Boolean {
        val isAccessibilityEnabled = AccessibilityHelper.isAccessibilityServiceEnabled(this)
        
        if (isAccessibilityEnabled) {
            AppLogger.i(TAG, "AccessibilityService is ENABLED - using instant event-driven detection")
            // Stop polling if it's currently running
            stopPollingIfRunning()
            return false
        } else {
            AppLogger.w(TAG, "AccessibilityService is DISABLED - falling back to polling-based monitoring")
            AppLogger.w(TAG, "⚠️  Polling is less efficient and slower than AccessibilityService")
            AppLogger.w(TAG, "⚠️  Please enable AccessibilityService in settings for better performance")
            return true
        }
    }
    
    /**
     * Stops polling-based monitoring if it's currently running.
     * This is called when AccessibilityService becomes available.
     */
    private fun stopPollingIfRunning() {
        foregroundCheckRunnable?.let { runnable ->
            if (handler.hasCallbacks(runnable)) {
                AppLogger.i(TAG, "Stopping polling-based monitoring (AccessibilityService now available)")
                handler.removeCallbacks(runnable)
            }
        }
    }
    
    private fun showOverlay(
        packageName: String,
        appName: String,
        timeUsed: Int,
        timeLimit: Int,
        pushUpRequirement: Int
    ) {
        // CRITICAL: Never show overlay in PushApp - this prevents overlay in push-up screen
        if (packageName == "com.example.pushapp" || packageName == this.packageName) {
            AppLogger.w(TAG, "CRITICAL: Attempted to show overlay in PushApp - BLOCKED")
            return
        }
        
        // If overlay is already showing for this package, do nothing.
        if (overlayViews.containsKey(packageName)) {
            AppLogger.d(TAG, "Overlay already visible for $packageName. Skipping.")
            return
        }

        try {
            // Check overlay permission first
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (!android.provider.Settings.canDrawOverlays(this)) {
                    AppLogger.e(TAG, "Overlay permission not granted - cannot show overlay")
                    // Optionally, send a notification to the user to grant permission
                    return
                }
            }
            
            AppLogger.i(TAG, "Showing overlay for $appName ($packageName)")
            
            val overlayView = LayoutInflater.from(this).inflate(R.layout.app_lock_overlay, null)
            setupOverlayContent(overlayView, appName, timeUsed, timeLimit, pushUpRequirement, packageName)
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.OPAQUE
            )
            
            windowManager.addView(overlayView, params)
            overlayViews[packageName] = overlayView
            stickyOverlays.add(packageName) // Mark as sticky to prevent accidental hiding
            AppLogger.i(TAG, "✅ Overlay displayed for $appName. Current overlays: ${overlayViews.keys}")
            
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
        overlayView.findViewById<TextView>(R.id.tvAppName).text = appName
        overlayView.findViewById<TextView>(R.id.tvTimeInfo).text =
            "Time used: ${timeUsed}min / ${timeLimit}min"
        overlayView.findViewById<TextView>(R.id.tvPushUpRequirement).text =
            "Complete $pushUpRequirement push-ups to unlock"
        
        overlayView.findViewById<Button>(R.id.btnUnlock).setOnClickListener {
            AppLogger.i(TAG, "Unlock button clicked for $appName")
            hideOverlay(packageName)
            
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("openPushUpScreen", true)
                putExtra("lockedAppPackage", packageName)
            }
            startActivity(intent)
        }
    }
    
    private fun hideOverlay(packageName: String) {
        val overlayView = overlayViews[packageName]
        if (overlayView != null) {
            try {
                // Remove overlay immediately - no delay for true persistence
                windowManager.removeView(overlayView)
                overlayViews.remove(packageName)
                stickyOverlays.remove(packageName) // Remove from sticky overlays
                AppLogger.i(TAG, "Overlay hidden for package: $packageName. Current overlays: ${overlayViews.keys}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to hide overlay for package: $packageName", e)
            }
        }
    }
    
    private fun hideAllOverlays() {
        // Hide all currently managed overlays
        val packageNames = overlayViews.keys.toList() // Create a copy to avoid concurrent modification
        packageNames.forEach { hideOverlay(it) }
        stickyOverlays.clear() // Clear sticky overlays
        // Clear cache when hiding all overlays to ensure fresh detection
        cachedLockedApps.clear()
        AppLogger.i(TAG, "All overlays hidden and cache cleared.")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopForegroundAppMonitoring()
        hideAllOverlays()
        AppLogger.i(TAG, "AppLockOverlayService destroyed")
    }
    
    private fun startForegroundAppMonitoring() {
        if (foregroundCheckRunnable != null) return // Already running
        foregroundCheckRunnable = object : Runnable {
            override fun run() {
                checkForegroundApp()
                handler.postDelayed(this, 1000) // Check every 1 second for faster response
            }
        }
        handler.post(foregroundCheckRunnable!!)
        AppLogger.i(TAG, "Started responsive foreground app monitoring (1s intervals).")
    }
    
    private fun stopForegroundAppMonitoring() {
        foregroundCheckRunnable?.let { handler.removeCallbacks(it) }
        foregroundCheckRunnable = null
        AppLogger.i(TAG, "Stopped foreground app monitoring.")
    }
    
    private fun checkForegroundApp() {
        serviceScope.launch(Dispatchers.Main) {
            val foregroundApp = getForegroundAppPackageName()
            val launcherPackage = getLauncherPackageName()

            // Performance optimization: Only log when foreground app changes
            if (foregroundApp != lastForegroundApp) {
                AppLogger.d(TAG, "Foreground changed: $lastForegroundApp -> $foregroundApp, Overlays: ${overlayViews.keys}")
                lastForegroundApp = foregroundApp
                // Reset null count when foreground app changes
                nullForegroundCount = 0
            }

            // Hide overlays when our own app is in foreground - CRITICAL for push-up screen
            if (foregroundApp == packageName || foregroundApp == "com.example.pushapp") {
                AppLogger.d(TAG, "PushApp detected in foreground - hiding all overlays immediately")
                hideAllOverlays()
                // Clear cache to ensure fresh detection
                cachedLockedApps.clear()
                return@launch
            }
            
            // Additional comprehensive check - never show overlay in PushApp under any circumstances
            if (foregroundApp?.contains("com.example.pushapp") == true) {
                AppLogger.d(TAG, "Comprehensive safety check: PushApp variant detected - hiding all overlays")
                hideAllOverlays()
                return@launch
            }
            
            // Hide overlays when launcher/home screen is active - IMMEDIATE HIDE
            if (foregroundApp == launcherPackage) {
                AppLogger.i(TAG, "Launcher detected - hiding all overlays immediately")
                hideAllOverlays()
                return@launch
            }
            
            // Also check for common launcher package names for immediate hiding
            val commonLaunchers = listOf(
                "com.android.launcher", "com.android.launcher2", "com.android.launcher3",
                "com.google.android.launcher", "com.samsung.android.launcher",
                "com.miui.home", "com.oneplus.launcher", "com.huawei.android.launcher"
            )
            if (foregroundApp != null && commonLaunchers.any { foregroundApp!!.startsWith(it) }) {
                AppLogger.i(TAG, "Common launcher detected ($foregroundApp) - hiding all overlays immediately")
                hideAllOverlays()
                return@launch
            }
            
            // If foreground app detection failed, use timeout mechanism to detect app closure
            if (foregroundApp == null) {
                nullForegroundCount++
                AppLogger.d(TAG, "No foreground app detected (count: $nullForegroundCount/$maxNullForegroundCount)")
                
                if (overlayViews.isNotEmpty()) {
                    if (nullForegroundCount >= maxNullForegroundCount) {
                        // App likely closed - hide overlays after timeout
                        AppLogger.i(TAG, "App likely closed - hiding overlays after $maxNullForegroundCount consecutive null detections")
                        hideAllOverlays()
                        return@launch
                    } else {
                        AppLogger.d(TAG, "Keeping overlays visible temporarily (app might be transitioning)")
                        // Don't return here - continue to check if overlays should be hidden
                    }
                } else {
                    AppLogger.d(TAG, "No foreground app detected and no overlays - nothing to do")
                    return@launch
                }
            } else {
                // Reset null count when we have a valid foreground app
                nullForegroundCount = 0
            }
            
            // Only process app-specific logic if we have a valid foreground app
            if (foregroundApp != null) {
                // Check if the foreground app should be locked (with caching)
                val isLocked = cachedLockedApps[foregroundApp] ?: run {
                    val appSettings = database.appLockDao().getAppLockSettings(foregroundApp)
                    val locked = appSettings?.isLocked ?: false
                    cachedLockedApps[foregroundApp] = locked
                    locked
                }
                
                if (isLocked) {
                    // App is locked, show overlay immediately if not already showing
                    if (!overlayViews.containsKey(foregroundApp)) {
                        val appSettings = database.appLockDao().getAppLockSettings(foregroundApp)
                        if (appSettings != null) {
                            showOverlay(
                                foregroundApp,
                                appSettings.appName,
                                appSettings.timeUsedToday,
                                appSettings.dailyTimeLimit,
                                appSettings.pushUpRequirement
                            )
                        }
                    }
                } else {
                    // App is not locked, hide its overlay if one is showing
                    hideOverlay(foregroundApp)
                }
            }
            
            // Only hide overlays for apps that are definitely not in foreground
            // Be more conservative to prevent overlay disappearing when clicking around
            val currentOverlays = overlayViews.keys.toList()
            currentOverlays.forEach { packageName ->
                if (foregroundApp != null && packageName != foregroundApp) {
                    // Only hide if we're switching to a completely different app AND it's not sticky
                    if (!stickyOverlays.contains(packageName)) {
                        AppLogger.d(TAG, "Hiding overlay for $packageName (switched to $foregroundApp)")
                        hideOverlay(packageName)
                    } else {
                        AppLogger.d(TAG, "Keeping sticky overlay for $packageName visible (switched to $foregroundApp)")
                    }
                } else if (foregroundApp == null) {
                    // No foreground app detected - keep overlays visible
                    // This prevents overlay from disappearing when clicking around in locked app
                    AppLogger.d(TAG, "No foreground app detected - keeping overlay for $packageName visible")
                }
            }
        }
    }

    private fun getForegroundAppPackageName(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()
        val startTime = currentTime - 5000 // Last 5 seconds as recommended in helper doc
        
        try {
            // Use the recommended approach: query with short time interval
            val statsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, currentTime)
            
            if (statsList != null && statsList.isNotEmpty()) {
                // Find the most recent app by lastTimeUsed (as recommended)
                val mostRecentApp = statsList.maxByOrNull { it.lastTimeUsed }
                
                if (mostRecentApp != null && mostRecentApp.packageName != "com.example.pushapp") {
                    AppLogger.d(TAG, "Foreground app detected: ${mostRecentApp.packageName} (last used: ${mostRecentApp.lastTimeUsed})")
                    return mostRecentApp.packageName
                }
            }
            
            // Fallback: Try with a slightly longer window if no recent activity
            val fallbackStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, currentTime - 10000, currentTime)
            if (fallbackStats != null && fallbackStats.isNotEmpty()) {
                val mostRecentApp = fallbackStats.maxByOrNull { it.lastTimeUsed }
                if (mostRecentApp != null && mostRecentApp.packageName != "com.example.pushapp") {
                    AppLogger.d(TAG, "Fallback foreground app: ${mostRecentApp.packageName}")
                    return mostRecentApp.packageName
                }
            }
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error querying usage stats", e)
        }
        
        AppLogger.w(TAG, "No foreground app detected")
        return null
    }

    private fun getLauncherPackageName(): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(intent, android.content.pm.PackageManager.ResolveInfoFlags.of(android.content.pm.PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolveInfo?.activityInfo?.packageName
    }
    
    private suspend fun checkAndRecreateOverlays() {
        try {
            // Check if Instagram overlay is missing and recreate it
            val instagramSettings = database.appLockDao().getAppLockSettings("com.instagram.android")
            if (instagramSettings != null && instagramSettings.isLocked && !overlayViews.containsKey("com.instagram.android")) {
                AppLogger.w(TAG, "Instagram overlay missing, recreating...")
                showOverlay(
                    instagramSettings.packageName,
                    instagramSettings.appName,
                    instagramSettings.timeUsedToday,
                    instagramSettings.dailyTimeLimit,
                    instagramSettings.pushUpRequirement
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to check and recreate overlays", e)
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
