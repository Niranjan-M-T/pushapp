package com.example.pushapp.manager

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import com.example.pushapp.data.*
import com.example.pushapp.service.AppLockService
import com.example.pushapp.service.AppLockOverlayService
import com.example.pushapp.utils.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.*

class AppLockManager(private val context: Context) {
    private val database = AppLockDatabase.getInstance(context)
    
    fun getInstalledApps(): List<AppInfo> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        
        val resolveInfoList = packageManager.queryIntentActivities(intent, 0)
        val appList = mutableListOf<AppInfo>()
        
        // Also try to get apps from installed packages
        val installedPackages = packageManager.getInstalledPackages(0)
        
        for (resolveInfo in resolveInfoList) {
            try {
                val packageName = resolveInfo.activityInfo.packageName
                val appName = resolveInfo.loadLabel(packageManager).toString()
                val icon = resolveInfo.loadIcon(packageManager)
                val isSystemApp = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                
                // Less restrictive filtering - only exclude our own app and some system apps
                if (packageName != context.packageName && 
                    !packageName.startsWith("com.android.") &&
                    !packageName.startsWith("android.") &&
                    !packageName.startsWith("com.google.android.") &&
                    !packageName.startsWith("com.qualcomm.")) {
                    appList.add(AppInfo(packageName, appName, icon, isSystemApp))
                }
            } catch (e: Exception) {
                // Skip apps that can't be loaded
                android.util.Log.w("AppLockManager", "Failed to load app: ${e.message}")
            }
        }
        
        // If we still don't have enough apps, try a different approach
        if (appList.size < 5) {
            for (packageInfo in installedPackages) {
                try {
                    val packageName = packageInfo.packageName
                    
                    // Skip if already in the list or our own app
                    if (appList.any { it.packageName == packageName } || 
                        packageName == context.packageName ||
                        packageName.startsWith("com.android.") ||
                        packageName.startsWith("android.")) {
                        continue
                    }
                    
                    val appName = packageInfo.applicationInfo.loadLabel(packageManager).toString()
                    val icon = packageInfo.applicationInfo.loadIcon(packageManager)
                    val isSystemApp = (packageInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    appList.add(AppInfo(packageName, appName, icon, isSystemApp))
                } catch (e: Exception) {
                    // Skip apps that can't be loaded
                    android.util.Log.w("AppLockManager", "Failed to load package: ${e.message}")
                }
            }
        }
        
        return appList.sortedBy { it.appName }
    }
    
    suspend fun getAppLockSettings(packageName: String): AppLockSettings? {
        return database.appLockDao().getAppLockSettings(packageName)
    }
    
    suspend fun saveAppLockSettings(settings: AppLockSettings) {
        AppLogger.i("AppLockManager", "Saving app lock settings for ${settings.appName}")
        database.appLockDao().insertAppLockSettings(settings)
        AppLogger.d("AppLockManager", "App lock settings saved successfully")
    }
    
    suspend fun updateAppLockSettings(settings: AppLockSettings) {
        AppLogger.i("AppLockManager", "Updating app lock settings for ${settings.appName}")
        database.appLockDao().updateAppLockSettings(settings)
        AppLogger.d("AppLockManager", "App lock settings updated successfully")
    }
    
    suspend fun deleteAppLockSettings(settings: AppLockSettings) {
        database.appLockDao().deleteAppLockSettings(settings)
    }
    
    fun getAllAppLockSettings(): Flow<List<AppLockSettings>> {
        return database.appLockDao().getAllAppLockSettings()
    }
    
    fun getLockedApps(): Flow<List<AppLockSettings>> {
        return database.appLockDao().getLockedApps()
    }
    
    suspend fun lockApp(packageName: String) {
        val settings = database.appLockDao().getAppLockSettings(packageName)
        if (settings != null) {
            val updatedSettings = settings.copy(isLocked = true)
            database.appLockDao().updateAppLockSettings(updatedSettings)
        }
    }
    
    suspend fun unlockApp(packageName: String) {
        val settings = database.appLockDao().getAppLockSettings(packageName)
        if (settings != null) {
            val updatedSettings = settings.copy(isLocked = false)
            database.appLockDao().updateAppLockSettings(updatedSettings)
        }
    }
    
    suspend fun verifyPushUpsAndUnlock(packageName: String, pushUpCount: Int): Boolean {
        val settings = database.appLockDao().getAppLockSettings(packageName)
        if (settings != null && pushUpCount >= settings.pushUpRequirement) {
            unlockApp(packageName)
            return true
        }
        return false
    }
    
    fun startMonitoring() {
        try {
            AppLogger.i("AppLockManager", "Starting monitoring service...")
            val intent = Intent(context, AppLockService::class.java).apply {
                action = "START_MONITORING"
            }
            
            // Check if we can start the service
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
                AppLogger.i("AppLockManager", "Started foreground service")
            } else {
                context.startService(intent)
                AppLogger.i("AppLockManager", "Started service")
            }
        } catch (e: SecurityException) {
            // Handle security exceptions (e.g., missing permissions)
            AppLogger.e("AppLockManager", "Security exception when starting service", e)
            throw e
        } catch (e: Exception) {
            // Handle other exceptions
            AppLogger.e("AppLockManager", "Exception when starting service", e)
            throw e
        }
    }
    
    fun stopMonitoring() {
        val intent = Intent(context, AppLockService::class.java).apply {
            action = "STOP_MONITORING"
        }
        context.startService(intent)
    }
    
    suspend fun getPushUpSettings(): PushUpSettings? {
        return database.pushUpSettingsDao().getPushUpSettings().first()
    }
    
    suspend fun savePushUpSettings(settings: PushUpSettings) {
        database.pushUpSettingsDao().insertPushUpSettings(settings)
    }
    
    suspend fun updatePushUpSettings(settings: PushUpSettings) {
        database.pushUpSettingsDao().updatePushUpSettings(settings)
    }
    
    suspend fun initializeDefaultSettings() {
        AppLogger.i("AppLockManager", "Initializing default settings...")
        val pushUpSettings = database.pushUpSettingsDao().getPushUpSettings().first()
        if (pushUpSettings == null) {
            val defaultSettings = PushUpSettings(
                defaultPushUpCount = 10,
                showInstructions = true,
                showEncouragement = true,
                previewOpacity = 0.7f
            )
            database.pushUpSettingsDao().insertPushUpSettings(defaultSettings)
            AppLogger.i("AppLockManager", "Default push-up settings created")
        } else {
            AppLogger.d("AppLockManager", "Push-up settings already exist")
        }
    }
    
    fun testOverlay() {
        try {
            AppLogger.i("AppLockManager", "Testing overlay...")
            val intent = Intent(context, AppLockOverlayService::class.java).apply {
                action = AppLockOverlayService.ACTION_TEST_OVERLAY
            }
            context.startService(intent)
            AppLogger.i("AppLockManager", "Test overlay service started")
        } catch (e: Exception) {
            AppLogger.e("AppLockManager", "Failed to test overlay", e)
        }
    }
    
    fun forceShowOverlayForApp(packageName: String, appName: String, timeUsed: Int, timeLimit: Int, pushUpRequirement: Int) {
        try {
            AppLogger.i("AppLockManager", "Force showing overlay for $appName...")
            val intent = Intent(context, AppLockOverlayService::class.java).apply {
                action = AppLockOverlayService.ACTION_SHOW_OVERLAY
                putExtra(AppLockOverlayService.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AppLockOverlayService.EXTRA_APP_NAME, appName)
                putExtra(AppLockOverlayService.EXTRA_TIME_USED, timeUsed)
                putExtra(AppLockOverlayService.EXTRA_TIME_LIMIT, timeLimit)
                putExtra(AppLockOverlayService.EXTRA_PUSH_UP_REQUIREMENT, pushUpRequirement)
            }
            context.startService(intent)
            AppLogger.i("AppLockManager", "Force overlay service started for $appName")
        } catch (e: Exception) {
            AppLogger.e("AppLockManager", "Failed to force show overlay for $appName", e)
        }
    }
}
