package com.example.pushapp.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.pushapp.R
import com.example.pushapp.utils.AppLogger
import com.example.pushapp.MainActivity

/**
 * Service that displays persistent overlays to lock apps when time limits are exceeded.
 * This service creates full-screen overlays that prevent users from using locked apps.
 */
class AppLockOverlayService : Service() {
    
    private lateinit var windowManager: WindowManager
    private val overlayViews = mutableMapOf<String, View>()
    
    companion object {
        private const val TAG = "AppLockOverlayService"
        
        // Intent actions
        const val ACTION_SHOW_OVERLAY = "com.example.pushapp.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.example.pushapp.HIDE_OVERLAY"
        const val ACTION_HIDE_ALL_OVERLAYS = "com.example.pushapp.HIDE_ALL_OVERLAYS"
        
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
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                val appName = intent.getStringExtra(EXTRA_APP_NAME)
                val timeUsed = intent.getIntExtra(EXTRA_TIME_USED, 0)
                val timeLimit = intent.getIntExtra(EXTRA_TIME_LIMIT, 0)
                val pushUpRequirement = intent.getIntExtra(EXTRA_PUSH_UP_REQUIREMENT, 0)
                
                if (packageName != null && appName != null) {
                    showOverlay(packageName, appName, timeUsed, timeLimit, pushUpRequirement)
                }
            }
            ACTION_HIDE_OVERLAY -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                if (packageName != null) {
                    hideOverlay(packageName)
                }
            }
            ACTION_HIDE_ALL_OVERLAYS -> {
                hideAllOverlays()
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
        try {
            // Remove existing overlay for this package if it exists
            hideOverlay(packageName)
            
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
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.START
            }
            
            // Add the overlay to the window manager
            windowManager.addView(overlayView, params)
            
            // Store the overlay view for later removal
            overlayViews[packageName] = overlayView
            
            AppLogger.i(TAG, "Overlay displayed successfully for $appName")
            
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
        
        // Set up close button (for testing purposes)
        overlayView.findViewById<Button>(R.id.btnClose).setOnClickListener {
            AppLogger.i(TAG, "Close button clicked for $appName")
            hideOverlay(packageName)
        }
    }
    
    private fun hideOverlay(packageName: String) {
        try {
            val overlayView = overlayViews[packageName]
            if (overlayView != null) {
                windowManager.removeView(overlayView)
                overlayViews.remove(packageName)
                AppLogger.i(TAG, "Overlay hidden for package: $packageName")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to hide overlay for package: $packageName", e)
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
            AppLogger.i(TAG, "All overlays hidden")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to hide all overlays", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        hideAllOverlays()
        AppLogger.i(TAG, "AppLockOverlayService destroyed")
    }
}
