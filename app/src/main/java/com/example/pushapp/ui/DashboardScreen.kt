package com.example.pushapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pushapp.data.AppLockSettings
import com.example.pushapp.ui.theme.*
import com.example.pushapp.utils.PermissionChecker
import com.example.pushapp.data.PermissionState
import androidx.compose.ui.platform.LocalContext

@Composable
fun DashboardScreen(
    lockedApps: List<AppLockSettings>,
    totalAppsLocked: Int,
    onUnlockApp: (AppLockSettings) -> Unit,
    onOpenSettings: () -> Unit,
    onStartPushUps: () -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onViewLogs: () -> Unit,
    onViewUsageChart: () -> Unit,
    onOpenPermissions: () -> Unit,
    isMonitoring: Boolean = false,
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
            text = "App Lock Dashboard",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = White,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Statistics cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Apps Locked",
                value = totalAppsLocked.toString(),
                icon = Icons.Default.Lock,
                modifier = Modifier.weight(1f)
            )
            
            StatCard(
                title = "Active Locks",
                value = lockedApps.count { it.isLocked }.toString(),
                icon = Icons.Default.Lock,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Permission status card
        PermissionStatusCard(
            onOpenPermissions = onOpenPermissions
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Quick actions
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
                Text(
                    text = "Quick Actions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        text = "Settings",
                        icon = Icons.Default.Settings,
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f)
                    )
                    
                ActionButton(
                    text = "Push-ups",
                    icon = Icons.Default.PlayArrow,
                    onClick = onStartPushUps,
                    modifier = Modifier.weight(1f)
                )
                
                ActionButton(
                    text = "View Logs",
                    icon = Icons.Default.Info,
                    onClick = onViewLogs,
                    modifier = Modifier.weight(1f)
                )
                
                ActionButton(
                    text = "Usage Chart",
                    icon = Icons.Default.Info,
                    onClick = onViewUsageChart,
                    modifier = Modifier.weight(1f)
                )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Monitoring control button
                ActionButton(
                    text = if (isMonitoring) "Stop Monitoring" else "Start Monitoring",
                    icon = if (isMonitoring) Icons.Default.Close else Icons.Default.PlayArrow,
                    onClick = if (isMonitoring) onStopMonitoring else onStartMonitoring,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Locked apps section
        if (lockedApps.isNotEmpty()) {
            Text(
                text = "Locked Applications",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(lockedApps.filter { it.isLocked }) { app ->
                    LockedAppCard(
                        app = app,
                        onUnlock = { onUnlockApp(app) }
                    )
                }
            }
        } else {
            // No locked apps message
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
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "No locked apps",
                        tint = PrimaryRed,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "No apps are currently locked",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = White,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "Go to Settings to configure app locks",
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
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MediumGrey
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = PrimaryRed,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = White
            )
            
            Text(
                text = title,
                fontSize = 14.sp,
                color = White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryRed
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = White,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = White
            )
        }
    }
}

@Composable
private fun LockedAppCard(
    app: AppLockSettings,
    onUnlock: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MediumGrey
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = PrimaryRed,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = app.appName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = White
                )
                
                Text(
                    text = "Time used: ${app.timeUsedToday} min / ${app.dailyTimeLimit} min",
                    fontSize = 14.sp,
                    color = White.copy(alpha = 0.7f)
                )
                
                Text(
                    text = "Push-ups required: ${app.pushUpRequirement}",
                    fontSize = 14.sp,
                    color = LightRed
                )
            }
            
            Button(
                onClick = onUnlock,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryRed
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Unlock",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = White
                )
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(
    onOpenPermissions: () -> Unit
) {
    val context = LocalContext.current
    val permissionChecker = remember { PermissionChecker(context) }
    var permissionState by remember { mutableStateOf<PermissionState?>(null) }
    
    LaunchedEffect(Unit) {
        permissionState = permissionChecker.checkAllPermissions()
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (permissionState?.allPermissionsGranted == true) 
                PrimaryRed.copy(alpha = 0.1f) 
            else 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (permissionState?.allPermissionsGranted == true) 
                    Icons.Default.CheckCircle 
                else 
                    Icons.Default.Warning,
                contentDescription = null,
                tint = if (permissionState?.allPermissionsGranted == true) 
                    PrimaryRed 
                else 
                    MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (permissionState?.allPermissionsGranted == true) 
                        "All Permissions Granted" 
                    else 
                        "Permissions Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (permissionState?.allPermissionsGranted == true) 
                        PrimaryRed 
                    else 
                        MaterialTheme.colorScheme.error
                )
                
                Text(
                    text = if (permissionState?.allPermissionsGranted == true) {
                        "App is fully functional"
                    } else {
                        "${permissionState?.missingRequiredCount ?: 0} required permission(s) missing"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (permissionState?.allPermissionsGranted != true) {
                Button(
                    onClick = onOpenPermissions,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Fix")
                }
            }
        }
    }
}
