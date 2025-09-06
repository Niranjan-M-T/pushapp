package com.example.pushapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
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
import com.example.pushapp.utils.AppLogger
import com.example.pushapp.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Service that displays persistent overlays to lock apps when time limits are exceeded.
 * This service creates full-screen overlays that prevent users from using locked apps.
 */
class AppLockOverlayService : Service() {
    
    private lateinit var windowManager: WindowManager
    private lateinit var database: AppLockDatabase
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val overlayViews = mutableMapOf<String, View>()
    
    // Smart overlay persistence - only for locked apps, not launcher
    private val persistentOverlays = mutableSetOf<String>() // Apps that should keep overlay visible
    
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
        
        // Using AccessibilityService for instant app detection
        AppLogger.i(TAG, "Using AccessibilityService for instant app detection")
        
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
                AppLogger.i(TAG, "AccessibilityService is active - no additional monitoring needed")
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    
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
            persistentOverlays.add(packageName) // Mark as persistent to prevent accidental hiding
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
                persistentOverlays.remove(packageName) // Remove from persistent overlays
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
        persistentOverlays.clear() // Clear persistent overlays
        AppLogger.i(TAG, "All overlays hidden.")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        hideAllOverlays()
        AppLogger.i(TAG, "AppLockOverlayService destroyed")
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
