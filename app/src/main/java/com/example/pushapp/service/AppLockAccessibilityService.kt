package com.example.pushapp.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.pushapp.data.AppLockDatabase
import com.example.pushapp.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class AppLockAccessibilityService : AccessibilityService() {
    
    private lateinit var database: AppLockDatabase
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val packageName = "com.example.pushapp"
    
    companion object {
        private const val TAG = "AppLockAccessibilityService"
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        database = AppLockDatabase.getInstance(this)
        AppLogger.i(TAG, "AccessibilityService connected - instant app detection enabled")
        
        // Configure the service
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 0
        }
        serviceInfo = info
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            val className = event.className?.toString()
            
            if (packageName != null && packageName != "com.example.pushapp") {
                AppLogger.d(TAG, "App changed to: $packageName (class: $className)")
                handleAppChange(packageName)
            } else if (packageName == "com.example.pushapp") {
                AppLogger.d(TAG, "Ignoring PushApp itself: $packageName")
            } else {
                AppLogger.d(TAG, "Ignoring null or invalid package: $packageName")
            }
        }
    }
    
    private fun handleAppChange(foregroundPackage: String) {
        serviceScope.launch {
            try {
                AppLogger.d(TAG, "Checking if app is locked: $foregroundPackage")
                
                // Handle special cases FIRST - these should hide overlays
                when {
                    foregroundPackage == "com.example.pushapp" -> {
                        AppLogger.d(TAG, "PushApp in foreground - hiding all overlays")
                        hideAllOverlays()
                        return@launch
                    }
                    isLauncherPackage(foregroundPackage) -> {
                        AppLogger.d(TAG, "Launcher detected - hiding all overlays")
                        hideAllOverlays()
                        return@launch
                    }
                }
                
                // Check if this app should be locked
                val appSettings = database.appLockDao().getAppLockSettings(foregroundPackage)
                
                AppLogger.d(TAG, "Database query result for $foregroundPackage: $appSettings")
                
                if (appSettings?.isLocked == true) {
                    AppLogger.i(TAG, "Locked app detected: ${appSettings.appName} - showing overlay")
                    showOverlayForApp(appSettings)
                } else {
                    AppLogger.d(TAG, "App not locked: $foregroundPackage - hiding overlays")
                    hideAllOverlays()
                }
                
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error handling app change", e)
            }
        }
    }
    
    private fun showOverlayForApp(appSettings: com.example.pushapp.data.AppLockSettings) {
        val intent = Intent(this, AppLockOverlayService::class.java).apply {
            action = "SHOW_OVERLAY"
            putExtra("packageName", appSettings.packageName)
            putExtra("appName", appSettings.appName)
            putExtra("timeUsed", appSettings.timeUsedToday)
            putExtra("timeLimit", appSettings.dailyTimeLimit)
            putExtra("pushUpRequirement", appSettings.pushUpRequirement)
        }
        startService(intent)
    }
    
    private fun hideAllOverlays() {
        val intent = Intent(this, AppLockOverlayService::class.java).apply {
            action = "HIDE_ALL_OVERLAYS"
        }
        startService(intent)
    }
    
    private fun isLauncherPackage(packageName: String): Boolean {
        val commonLaunchers = listOf(
            "com.android.launcher", "com.android.launcher2", "com.android.launcher3",
            "com.google.android.launcher", "com.samsung.android.launcher",
            "com.miui.home", "com.oneplus.launcher", "com.huawei.android.launcher"
        )
        return commonLaunchers.any { packageName.startsWith(it) }
    }
    
    override fun onInterrupt() {
        AppLogger.w(TAG, "AccessibilityService interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        AppLogger.i(TAG, "AccessibilityService destroyed")
    }
}
