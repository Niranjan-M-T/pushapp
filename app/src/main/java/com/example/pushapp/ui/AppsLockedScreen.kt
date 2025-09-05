package com.example.pushapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pushapp.data.AppLockSettings
import com.example.pushapp.ui.theme.*
import com.example.pushapp.service.UsageDataService
import kotlinx.coroutines.launch

@Composable
fun AppsLockedScreen(
    lockedApps: List<AppLockSettings>,
    onBack: () -> Unit,
    onTimeLimitChange: (String, Int) -> Unit,
    onPushUpRequirementChange: (String, Int) -> Unit,
    onToggleAppLock: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val usageDataService = remember { UsageDataService(context) }
    var realTimeUsageData by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    
    // Function to refresh usage data
    val refreshUsageData = {
        scope.launch {
            try {
                val usageData = usageDataService.getUsageChartData()
                val usageMap = usageData.topApps.associate { it.packageName to it.timeUsedMinutes }
                realTimeUsageData = usageMap
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }
    
    // Load usage data on first load
    LaunchedEffect(Unit) {
        refreshUsageData()
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Black)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = White,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Text(
                text = "Apps Locked",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = White,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            
            IconButton(
                onClick = { refreshUsageData() }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Usage",
                    tint = White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Apps list
        if (lockedApps.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(lockedApps) { app ->
                    AppLockItem(
                        app = app,
                        realTimeUsage = realTimeUsageData[app.packageName] ?: app.timeUsedToday,
                        onTimeLimitChange = onTimeLimitChange,
                        onPushUpRequirementChange = onPushUpRequirementChange,
                        onToggleLock = onToggleAppLock
                    )
                }
            }
        } else {
            // No apps configured message
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MediumGrey
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "No locked apps",
                        tint = PrimaryRed,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "No apps are configured",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = White,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "Go to All Apps to configure app locks",
                        fontSize = 14.sp,
                        color = White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppLockItem(
    app: AppLockSettings,
    realTimeUsage: Int,
    onTimeLimitChange: (String, Int) -> Unit,
    onPushUpRequirementChange: (String, Int) -> Unit,
    onToggleLock: (String, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MediumGrey
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = app.appName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Switch(
                    checked = app.isLocked,
                    onCheckedChange = { onToggleLock(app.packageName, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = White,
                        checkedTrackColor = PrimaryRed,
                        uncheckedThumbColor = White,
                        uncheckedTrackColor = MediumGrey
                    )
                )
            }
            
            if (app.isLocked) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Time limit section
                Column {
                    Text(
                        text = "Time Limit: ${app.dailyTimeLimit} minutes",
                        fontSize = 16.sp,
                        color = White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Slider(
                        value = app.dailyTimeLimit.toFloat(),
                        onValueChange = { onTimeLimitChange(app.packageName, it.toInt()) },
                        valueRange = 5f..180f,
                        steps = 34, // 5-minute increments
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryRed,
                            activeTrackColor = PrimaryRed,
                            inactiveTrackColor = MediumGrey
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Push-up requirement section
                Column {
                    Text(
                        text = "Push-ups Required: ${app.pushUpRequirement}",
                        fontSize = 16.sp,
                        color = White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Slider(
                        value = app.pushUpRequirement.toFloat(),
                        onValueChange = { onPushUpRequirementChange(app.packageName, it.toInt()) },
                        valueRange = 1f..50f,
                        steps = 48, // 1 push-up increments
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryRed,
                            activeTrackColor = PrimaryRed,
                            inactiveTrackColor = MediumGrey
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Usage info
                Text(
                    text = "Used today: $realTimeUsage minutes",
                    fontSize = 14.sp,
                    color = White.copy(alpha = 0.7f)
                )
            }
        }
    }
}
