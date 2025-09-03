package com.example.pushapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_lock_settings")
data class AppLockSettings(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isLocked: Boolean = false,
    val dailyTimeLimit: Int = 0, // in minutes
    val timeUsedToday: Int = 0, // in minutes
    val pushUpRequirement: Int = 10, // number of push-ups to unlock
    val lastResetDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "push_up_settings")
data class PushUpSettings(
    @PrimaryKey val id: Int = 1,
    val defaultPushUpCount: Int = 10,
    val showInstructions: Boolean = true,
    val showEncouragement: Boolean = true,
    val previewOpacity: Float = 0.7f
)

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable?,
    val isSystemApp: Boolean = false
)

data class AppUsageStats(
    val packageName: String,
    val timeUsed: Long, // in milliseconds
    val lastTimeUsed: Long
)
