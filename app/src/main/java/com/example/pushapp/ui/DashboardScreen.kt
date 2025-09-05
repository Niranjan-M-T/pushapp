package com.example.pushapp.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
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
    onViewLogs: () -> Unit,
    onViewUsageChart: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAppsLocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSidebar by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Black)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header with menu button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showSidebar = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Text(
                    text = "App Lock Dashboard",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = White,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                
                // Empty space to balance the layout
                Spacer(modifier = Modifier.size(48.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Statistics cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Apps Locked card - clickable to open new screen
                ClickableStatCard(
                    title = "Apps Locked",
                    value = totalAppsLocked.toString(),
                    icon = Icons.Default.Lock,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenAppsLocked
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
            
            // Quick actions - simplified
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
                            text = "All Apps",
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
                            text = "Usage Chart",
                            icon = Icons.Default.Info,
                            onClick = onViewUsageChart,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Test Overlay Button (for debugging)
                Spacer(modifier = Modifier.height(16.dp))
                
                TestOverlayButton()
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LockInstagramButton()
                
                Spacer(modifier = Modifier.height(8.dp))
                
                CheckInstagramUsageButton()
                
                Spacer(modifier = Modifier.height(8.dp))
                
                ForceLockInstagramButton()
                
                Spacer(modifier = Modifier.height(8.dp))
                
                StartOverlayServiceButton()
                
                Spacer(modifier = Modifier.height(8.dp))
                
                CheckOverlayPermissionButton()
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TestFullScreenOverlayButton()
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TestInstagramOverlayButton()
            }
        }
        
        // Sidebar
        AnimatedVisibility(
            visible = showSidebar,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(300)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(300)
            )
        ) {
            Sidebar(
                onClose = { showSidebar = false },
                onViewLogs = onViewLogs,
                onOpenSettings = onOpenSettings
            )
        }
        
        // Overlay to close sidebar when clicking outside
        if (showSidebar) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showSidebar = false }
            )
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
private fun ClickableStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
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
private fun Sidebar(
    onClose: () -> Unit,
    onViewLogs: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp),
        colors = CardDefaults.cardColors(
            containerColor = MediumGrey
        ),
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
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
                    text = "Menu",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = White
                )
                
                IconButton(
                    onClick = onClose
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = White
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Menu items
            SidebarItem(
                text = "All Apps",
                icon = Icons.Default.Settings,
                onClick = {
                    onOpenSettings()
                    onClose()
                }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            SidebarItem(
                text = "View Logs",
                icon = Icons.Default.Info,
                onClick = {
                    onViewLogs()
                    onClose()
                }
            )
        }
    }
}

@Composable
private fun SidebarItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = White,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = White
        )
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

@Composable
private fun TestOverlayButton() {
    val context = LocalContext.current
    
    Button(
        onClick = {
            // Test overlay functionality
            val intent = android.content.Intent(context, com.example.pushapp.service.AppLockOverlayService::class.java).apply {
                action = com.example.pushapp.service.AppLockOverlayService.ACTION_TEST_OVERLAY
            }
            context.startService(intent)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryRed
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Test Overlay",
            color = White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LockInstagramButton() {
    val context = LocalContext.current
    
    Button(
        onClick = {
            // Simulate Instagram being locked
            val intent = android.content.Intent(context, com.example.pushapp.service.AppLockOverlayService::class.java).apply {
                action = com.example.pushapp.service.AppLockOverlayService.ACTION_SHOW_OVERLAY
                putExtra(com.example.pushapp.service.AppLockOverlayService.EXTRA_PACKAGE_NAME, "com.instagram.android")
                putExtra(com.example.pushapp.service.AppLockOverlayService.EXTRA_APP_NAME, "Instagram")
                putExtra(com.example.pushapp.service.AppLockOverlayService.EXTRA_TIME_USED, 65)
                putExtra(com.example.pushapp.service.AppLockOverlayService.EXTRA_TIME_LIMIT, 60)
                putExtra(com.example.pushapp.service.AppLockOverlayService.EXTRA_PUSH_UP_REQUIREMENT, 10)
            }
            context.startService(intent)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = LightGrey
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Lock Instagram (Test)",
            color = White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CheckInstagramUsageButton() {
    val context = LocalContext.current
    
    Button(
        onClick = {
            // Manually trigger AppLockService to check Instagram
            val intent = android.content.Intent(context, com.example.pushapp.service.AppLockService::class.java).apply {
                action = "CHECK_INSTAGRAM_USAGE"
            }
            context.startService(intent)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryRed
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Check Instagram Usage",
            color = White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ForceLockInstagramButton() {
    val context = LocalContext.current
    
    Button(
        onClick = {
            // Force lock Instagram in database
            val intent = android.content.Intent(context, com.example.pushapp.service.AppLockService::class.java).apply {
                action = "FORCE_LOCK_INSTAGRAM"
            }
            context.startService(intent)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = DarkRed
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Force Lock Instagram",
            color = White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StartOverlayServiceButton() {
    val context = LocalContext.current
    
    Button(
        onClick = {
            // Start overlay service manually
            val intent = android.content.Intent(context, com.example.pushapp.service.AppLockOverlayService::class.java)
            context.startService(intent)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = LighterGrey
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Start Overlay Service",
            color = White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CheckOverlayPermissionButton() {
    val context = LocalContext.current
    
    Button(
        onClick = {
            try {
                // Check overlay permission
                val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.provider.Settings.canDrawOverlays(context)
                } else {
                    true
                }
                
                android.util.Log.d("DashboardScreen", "Overlay permission granted: $hasPermission")
                android.util.Log.d("DashboardScreen", "Package name: ${context.packageName}")
                
                if (!hasPermission) {
                    // Request permission
                    val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    android.util.Log.d("DashboardScreen", "Started overlay permission request")
                } else {
                    android.util.Log.d("DashboardScreen", "Overlay permission already granted")
                }
            } catch (e: Exception) {
                android.util.Log.e("DashboardScreen", "Error checking overlay permission", e)
            }
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = LightGrey
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Check Overlay Permission",
            color = White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TestFullScreenOverlayButton() {
    val context = LocalContext.current
    
    Button(
        onClick = {
            // Test full screen overlay
            val intent = android.content.Intent(context, com.example.pushapp.service.AppLockOverlayService::class.java).apply {
                action = com.example.pushapp.service.AppLockOverlayService.ACTION_TEST_OVERLAY
            }
            context.startService(intent)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryRed
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Test Full Screen Overlay",
            color = White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TestInstagramOverlayButton() {
    val context = LocalContext.current
    
    Button(
        onClick = {
            try {
                // Directly test Instagram overlay
                val intent = android.content.Intent(context, com.example.pushapp.service.AppLockOverlayService::class.java).apply {
                    action = com.example.pushapp.service.AppLockOverlayService.ACTION_SHOW_OVERLAY
                    putExtra(com.example.pushapp.service.AppLockOverlayService.EXTRA_APP_NAME, "Instagram")
                    putExtra(com.example.pushapp.service.AppLockOverlayService.EXTRA_PACKAGE_NAME, "com.instagram.android")
                    putExtra(com.example.pushapp.service.AppLockOverlayService.EXTRA_TIME_USED, 999)
                    putExtra(com.example.pushapp.service.AppLockOverlayService.EXTRA_TIME_LIMIT, 60)
                    putExtra(com.example.pushapp.service.AppLockOverlayService.EXTRA_PUSH_UP_REQUIREMENT, 10)
                }
                context.startService(intent)
                android.util.Log.d("DashboardScreen", "Sent Instagram overlay test intent")
            } catch (e: Exception) {
                android.util.Log.e("DashboardScreen", "Error testing Instagram overlay", e)
            }
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = DarkRed
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Test Instagram Overlay",
            color = White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

