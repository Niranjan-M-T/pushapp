package com.example.pushapp.utils

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.pushapp.R
import com.example.pushapp.data.PermissionInfo
import com.example.pushapp.data.PermissionState

class PermissionChecker(private val context: Context) {
    
    companion object {
        const val PERMISSION_USAGE_STATS = "usage_stats"
        const val PERMISSION_OVERLAY = "overlay"
        const val PERMISSION_CAMERA = "camera"
        const val PERMISSION_NOTIFICATIONS = "notifications"
        const val PERMISSION_FOREGROUND_SERVICE = "foreground_service"
        const val PERMISSION_ACCESSIBILITY = "accessibility"
    }
    
    fun checkAllPermissions(): PermissionState {
        val requiredPermissions = listOf(
            checkUsageStatsPermission(),
            checkOverlayPermission(),
            checkCameraPermission(),
            checkAccessibilityPermission()
        )
        
        val optionalPermissions = listOf(
            checkNotificationPermission(),
            checkForegroundServicePermission()
        )
        
        val missingRequired = requiredPermissions.count { !it.isGranted }
        val missingOptional = optionalPermissions.count { !it.isGranted }
        val allRequiredGranted = missingRequired == 0
        
        return PermissionState(
            allPermissionsGranted = allRequiredGranted && missingOptional == 0,
            requiredPermissions = requiredPermissions,
            optionalPermissions = optionalPermissions,
            missingRequiredCount = missingRequired,
            missingOptionalCount = missingOptional
        )
    }
    
    private fun checkUsageStatsPermission(): PermissionInfo {
        val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } else {
            true
        }
        
        return PermissionInfo(
            permission = PERMISSION_USAGE_STATS,
            displayName = "Usage Access",
            description = "Required to monitor app usage and enforce time limits",
            isRequired = true,
            isGranted = isGranted,
            settingsIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        )
    }
    
    private fun checkOverlayPermission(): PermissionInfo {
        val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
        
        return PermissionInfo(
            permission = PERMISSION_OVERLAY,
            displayName = "Display Over Other Apps",
            description = "Required to show app lock overlay when time limits are exceeded",
            isRequired = true,
            isGranted = isGranted,
            settingsIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        )
    }
    
    private fun checkCameraPermission(): PermissionInfo {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        return PermissionInfo(
            permission = PERMISSION_CAMERA,
            displayName = "Camera",
            description = "Required for push-up detection and pose tracking",
            isRequired = true,
            isGranted = isGranted,
            settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
    }
    
    private fun checkNotificationPermission(): PermissionInfo {
        val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        
        return PermissionInfo(
            permission = PERMISSION_NOTIFICATIONS,
            displayName = "Notifications",
            description = "Required to show app lock notifications and alerts",
            isRequired = false,
            isGranted = isGranted,
            settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
    }
    
    private fun checkForegroundServicePermission(): PermissionInfo {
        val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        
        return PermissionInfo(
            permission = PERMISSION_FOREGROUND_SERVICE,
            displayName = "Foreground Service",
            description = "Required for continuous app monitoring in the background",
            isRequired = false,
            isGranted = isGranted,
            settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
    }
    
    fun openPermissionSettings(permissionInfo: PermissionInfo) {
        when (permissionInfo.permission) {
            PERMISSION_ACCESSIBILITY -> {
                AccessibilityHelper.openAccessibilitySettings(context)
            }
            else -> {
                try {
                    permissionInfo.settingsIntent?.let { intent ->
                        context.startActivity(intent)
                    }
                } catch (e: Exception) {
                    AppLogger.e("PermissionChecker", "Failed to open settings for ${permissionInfo.permission}", e)
                    // Fallback to general app settings
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (e2: Exception) {
                        AppLogger.e("PermissionChecker", "Failed to open app settings", e2)
                    }
                }
            }
        }
    }
    
    fun getPermissionStatusText(permissionState: PermissionState): String {
        return when {
            permissionState.allPermissionsGranted -> "All permissions granted"
            permissionState.missingRequiredCount > 0 -> 
                "${permissionState.missingRequiredCount} required permission(s) missing"
            permissionState.missingOptionalCount > 0 -> 
                "${permissionState.missingOptionalCount} optional permission(s) missing"
            else -> "Permission status unknown"
        }
    }
    
    private fun checkAccessibilityPermission(): PermissionInfo {
        val isGranted = AccessibilityHelper.isAccessibilityServiceEnabled(context)
        return PermissionInfo(
            permission = PERMISSION_ACCESSIBILITY,
            displayName = "Accessibility Service",
            description = "Required for instant app detection and better performance",
            isGranted = isGranted,
            isRequired = true,
            settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        )
    }
    
}
