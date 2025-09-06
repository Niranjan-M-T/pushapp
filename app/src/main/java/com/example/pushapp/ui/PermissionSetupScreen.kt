package com.example.pushapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pushapp.data.PermissionInfo
import com.example.pushapp.data.PermissionState
import com.example.pushapp.utils.PermissionChecker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUnitApi::class)
@Composable
fun PermissionSetupScreen(
    onPermissionsGranted: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val permissionChecker = remember { PermissionChecker(context) }
    var permissionState by remember { mutableStateOf<PermissionState?>(null) }
    var isCheckingPermissions by remember { mutableStateOf(true) }
    
    val scope = rememberCoroutineScope()
    
    // Check permissions on screen load
    LaunchedEffect(Unit) {
        scope.launch {
            isCheckingPermissions = true
            permissionState = permissionChecker.checkAllPermissions()
            isCheckingPermissions = false
        }
    }
    
    // Refresh permissions when returning from settings
    val refreshPermissions: () -> Unit = {
        scope.launch {
            isCheckingPermissions = true
            permissionState = permissionChecker.checkAllPermissions()
            isCheckingPermissions = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permission Setup") },
                actions = {
                    IconButton(onClick = refreshPermissions) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when {
                isCheckingPermissions -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Checking permissions...")
                        }
                    }
                }
                
                permissionState != null -> {
                    PermissionSetupContent(
                        permissionState = permissionState!!,
                        permissionChecker = permissionChecker,
                        onPermissionsGranted = onPermissionsGranted,
                        onSkip = onSkip,
                        onRefresh = refreshPermissions
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionSetupContent(
    permissionState: PermissionState,
    permissionChecker: PermissionChecker,
    onPermissionsGranted: () -> Unit,
    onSkip: () -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            PermissionHeader(permissionState = permissionState)
        }
        
        if (permissionState.missingRequiredCount > 0) {
            item {
                Text(
                    text = "Required Permissions",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            items(permissionState.requiredPermissions.filter { !it.isGranted }) { permission ->
                PermissionItem(
                    permission = permission,
                    permissionChecker = permissionChecker,
                    onRefresh = onRefresh
                )
            }
        }
        
        if (permissionState.missingOptionalCount > 0) {
            item {
                Text(
                    text = "Optional Permissions",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            items(permissionState.optionalPermissions.filter { !it.isGranted }) { permission ->
                PermissionItem(
                    permission = permission,
                    permissionChecker = permissionChecker,
                    onRefresh = onRefresh
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (permissionState.allPermissionsGranted) {
                    Button(
                        onClick = onPermissionsGranted,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Continue to App")
                    }
                } else if (permissionState.missingRequiredCount == 0) {
                    // All required permissions granted, but some optional missing
                    Button(
                        onClick = onPermissionsGranted,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Continue to App")
                    }
                    
                    OutlinedButton(
                        onClick = onSkip,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Skip Optional Permissions")
                    }
                } else {
                    // Required permissions missing
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Required Permissions Missing",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "Please grant the required permissions above to use all app features.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionHeader(permissionState: PermissionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                            Icon(
                    imageVector = if (permissionState.allPermissionsGranted) Icons.Default.CheckCircle else Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = if (permissionState.allPermissionsGranted) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (permissionState.allPermissionsGranted) 
                    "All Permissions Granted!" 
                else 
                    "Permission Setup Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (permissionState.allPermissionsGranted) {
                    "Your app is ready to use with all features enabled."
                } else {
                    "Grant the following permissions to enable all app features and ensure proper functionality."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Permission summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PermissionSummaryItem(
                    label = "Required",
                    count = permissionState.requiredPermissions.count { it.isGranted },
                    total = permissionState.requiredPermissions.size,
                    isRequired = true
                )
                
                PermissionSummaryItem(
                    label = "Optional",
                    count = permissionState.optionalPermissions.count { it.isGranted },
                    total = permissionState.optionalPermissions.size,
                    isRequired = false
                )
            }
        }
    }
}

@Composable
private fun PermissionSummaryItem(
    label: String,
    count: Int,
    total: Int,
    isRequired: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$count/$total",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (isRequired && count < total) 
                MaterialTheme.colorScheme.error 
            else 
                MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PermissionItem(
    permission: PermissionInfo,
    permissionChecker: PermissionChecker,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Permission icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (permission.isGranted) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getPermissionIcon(permission.permission),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (permission.isGranted) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Permission info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = permission.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    if (permission.isRequired) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Required",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Optional",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = permission.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Action button
            if (permission.isGranted) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(
                    onClick = {
                        permissionChecker.openPermissionSettings(permission)
                        // Refresh permissions after a delay to allow user to grant permission
                        kotlinx.coroutines.GlobalScope.launch {
                            kotlinx.coroutines.delay(1000)
                            onRefresh()
                        }
                    }
                ) {
                    Text("Grant")
                }
            }
        }
    }
}

private fun getPermissionIcon(permission: String): ImageVector {
    return when (permission) {
        PermissionChecker.PERMISSION_USAGE_STATS -> Icons.Default.Info
        PermissionChecker.PERMISSION_OVERLAY -> Icons.Default.Settings
        PermissionChecker.PERMISSION_CAMERA -> Icons.Default.Info
        PermissionChecker.PERMISSION_NOTIFICATIONS -> Icons.Default.Notifications
        PermissionChecker.PERMISSION_FOREGROUND_SERVICE -> Icons.Default.Settings
        PermissionChecker.PERMISSION_ACCESSIBILITY -> Icons.Default.Settings
        else -> Icons.Default.Lock
    }
}
