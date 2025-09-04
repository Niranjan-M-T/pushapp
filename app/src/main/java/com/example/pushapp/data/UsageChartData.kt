package com.example.pushapp.data

import android.graphics.drawable.Drawable

data class AppUsageData(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val timeUsedMinutes: Int,
    val timeUsedHours: Double,
    val percentage: Float,
    val lastUsed: Long
)

data class UsageChartData(
    val totalApps: Int,
    val totalUsageMinutes: Int,
    val topApps: List<AppUsageData>,
    val dateRange: String
)

data class LogExportData(
    val logs: String,
    val fileName: String,
    val timestamp: Long
)
