package com.example.pushapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pushapp.data.AppInfo
import com.example.pushapp.data.AppLockSettings
import com.example.pushapp.ui.theme.*

@Composable
fun AppSelectionScreen(
    installedApps: List<AppInfo>,
    appLockSettings: List<AppLockSettings>,
    onAppToggle: (AppInfo, Boolean) -> Unit,
    onTimeLimitChange: (String, Int) -> Unit,
    onPushUpRequirementChange: (String, Int) -> Unit,
    onSaveSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Black)
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "App Lock Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = White,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Instructions
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MediumGrey
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Select apps to lock and set daily time limits",
                    fontSize = 16.sp,
                    color = White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "When you exceed the time limit, you'll need to complete push-ups to unlock the app",
                    fontSize = 14.sp,
                    color = White.copy(alpha = 0.8f)
                )
            }
        }
        
        // App list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(installedApps) { app ->
                AppLockItem(
                    app = app,
                    lockSettings = appLockSettings.find { it.packageName == app.packageName },
                    onToggle = { isLocked -> onAppToggle(app, isLocked) },
                    onTimeLimitChange = { timeLimit -> onTimeLimitChange(app.packageName, timeLimit) },
                    onPushUpRequirementChange = { pushUpCount -> onPushUpRequirementChange(app.packageName, pushUpCount) }
                )
            }
        }
        
        // Save button
        Button(
            onClick = onSaveSettings,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryRed
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Save Settings",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = White,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun AppLockItem(
    app: AppInfo,
    lockSettings: AppLockSettings?,
    onToggle: (Boolean) -> Unit,
    onTimeLimitChange: (Int) -> Unit,
    onPushUpRequirementChange: (Int) -> Unit
) {
    val isLocked = lockSettings?.isLocked ?: false
    val timeLimit = lockSettings?.dailyTimeLimit ?: 0
    val pushUpRequirement = lockSettings?.pushUpRequirement ?: 10
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLocked) MediumGrey else DarkGrey
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // App header with icon and toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App icon
                app.icon?.let { icon ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(LightGrey)
                    ) {
                        // Display the actual app icon
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { context ->
                                android.widget.ImageView(context).apply {
                                    setImageDrawable(icon)
                                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } ?: run {
                    // Fallback icon if app.icon is null
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(LightGrey),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "App Icon",
                            tint = White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // App name
                Text(
                    text = app.appName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = White,
                    modifier = Modifier.weight(1f)
                )
                
                // Toggle switch
                Switch(
                    checked = isLocked,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = White,
                        checkedTrackColor = PrimaryRed,
                        uncheckedThumbColor = LightGrey,
                        uncheckedTrackColor = MediumGrey
                    )
                )
            }
            
            // Settings (only show when locked)
            if (isLocked) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Time limit setting
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Time Limit",
                        tint = LightRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Daily Time Limit:",
                        fontSize = 14.sp,
                        color = White,
                        modifier = Modifier.weight(1f)
                    )
                    
                    OutlinedTextField(
                        value = timeLimit.toString(),
                        onValueChange = { value ->
                            value.toIntOrNull()?.let { onTimeLimitChange(it) }
                        },
                        modifier = Modifier.width(80.dp),
                        textStyle = LocalTextStyle.current.copy(
                            color = White,
                            fontSize = 14.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryRed,
                            unfocusedBorderColor = LightGrey,
                            focusedLabelColor = PrimaryRed,
                            unfocusedLabelColor = LightGrey
                        ),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "minutes",
                        fontSize = 14.sp,
                        color = White.copy(alpha = 0.7f)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Push-up requirement setting
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Push-up Requirement",
                        tint = LightRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Push-ups to unlock:",
                        fontSize = 14.sp,
                        color = White,
                        modifier = Modifier.weight(1f)
                    )
                    
                    OutlinedTextField(
                        value = pushUpRequirement.toString(),
                        onValueChange = { value ->
                            value.toIntOrNull()?.let { onPushUpRequirementChange(it) }
                        },
                        modifier = Modifier.width(80.dp),
                        textStyle = LocalTextStyle.current.copy(
                            color = White,
                            fontSize = 14.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryRed,
                            unfocusedBorderColor = LightGrey,
                            focusedLabelColor = PrimaryRed,
                            unfocusedLabelColor = LightGrey
                        ),
                        singleLine = true
                    )
                }
            }
        }
    }
}
