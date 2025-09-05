package com.example.pushapp.service

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.example.pushapp.data.AppUsageData
import com.example.pushapp.data.UsageChartData
import com.example.pushapp.utils.AppLogger
import java.text.SimpleDateFormat
import java.util.*

class UsageDataService(private val context: Context) {
    
    private val packageManager = context.packageManager
    
    suspend fun getUsageChartData(): UsageChartData {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val usageStats = getTodaysUsageStats(usageStatsManager)
            
            val appUsageData = usageStats.mapNotNull { stat ->
                try {
                    val appInfo = packageManager.getApplicationInfo(stat.packageName, 0)
                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = packageManager.getApplicationIcon(appInfo)
                    val timeUsedMinutes = (stat.totalTimeInForeground / (1000 * 60)).toInt()
                    
                    AppUsageData(
                        packageName = stat.packageName,
                        appName = appName,
                        icon = icon,
                        timeUsedMinutes = timeUsedMinutes,
                        timeUsedHours = timeUsedMinutes / 60.0,
                        percentage = 0f, // Will be calculated later
                        lastUsed = stat.lastTimeUsed
                    )
                } catch (e: Exception) {
                    AppLogger.w("UsageDataService", "Failed to get app info for ${stat.packageName}: ${e.message}")
                    null
                }
            }
            
            // Filter out apps with no usage and sort by usage time
            val filteredApps = appUsageData
                .filter { it.timeUsedMinutes > 0 }
                .sortedByDescending { it.timeUsedMinutes }
                .take(20) // Top 20 apps
            
            val totalUsageMinutes = filteredApps.sumOf { it.timeUsedMinutes }
            
            // Calculate percentages
            val appsWithPercentage = filteredApps.map { app ->
                app.copy(percentage = if (totalUsageMinutes > 0) {
                    (app.timeUsedMinutes.toFloat() / totalUsageMinutes * 100)
                } else 0f)
            }
            
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val today = dateFormat.format(Date())
            
            UsageChartData(
                totalApps = filteredApps.size,
                totalUsageMinutes = totalUsageMinutes,
                topApps = appsWithPercentage,
                dateRange = "Today ($today)"
            )
            
        } catch (e: Exception) {
            AppLogger.e("UsageDataService", "Failed to get usage chart data", e)
            UsageChartData(
                totalApps = 0,
                totalUsageMinutes = 0,
                topApps = emptyList(),
                dateRange = "Error loading data"
            )
        }
    }
    
    private fun getTodaysUsageStats(usageStatsManager: UsageStatsManager): List<UsageStats> {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        
        // Set start time to beginning of today (midnight)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        
        AppLogger.d("UsageDataService", "Getting today's usage stats from ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(startTime))} to ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(endTime))}")
        
        return try {
            // Try multiple intervals to get more comprehensive data
            val allStats = mutableListOf<UsageStats>()
            
            // First try INTERVAL_BEST for the most accurate data
            val bestStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                startTime,
                endTime
            )
            bestStats?.let { allStats.addAll(it) }
            
            // Also try INTERVAL_DAILY as fallback
            val dailyStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            dailyStats?.let { allStats.addAll(it) }
            
            // Merge and deduplicate stats by package name, keeping the one with highest usage
            val mergedStats = allStats.groupBy { it.packageName }
                .mapValues { (_, stats) ->
                    stats.maxByOrNull { it.totalTimeInForeground } ?: stats.first()
                }
                .values
                .toList()
            
            AppLogger.d("UsageDataService", "Retrieved ${mergedStats.size} usage stats entries (merged from ${allStats.size} total)")
            
            // Log some sample data for debugging
            mergedStats.take(5).forEach { stat ->
                AppLogger.d("UsageDataService", "Sample usage: ${stat.packageName} - ${stat.totalTimeInForeground}ms")
            }
            
            mergedStats
        } catch (e: Exception) {
            AppLogger.e("UsageDataService", "Failed to query usage stats", e)
            emptyList()
        }
    }
}
