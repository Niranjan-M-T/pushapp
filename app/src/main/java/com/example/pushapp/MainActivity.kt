package com.example.pushapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pushapp.ui.*
import com.example.pushapp.ui.theme.PushAppTheme
import com.example.pushapp.utils.AccessibilityHelper
import com.example.pushapp.integration.PushUpIntegration
import com.example.pushapp.utils.AppLogger
import com.example.pushapp.utils.PermissionChecker
import com.example.pushapp.data.PermissionState

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted - camera should work now
            android.util.Log.d("MainActivity", "Camera permission granted")
        } else {
            // Permission denied - show message
            android.util.Log.w("MainActivity", "Camera permission denied")
        }
    }
    
    private val requestUsageStatsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Check if usage stats permission was granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOpsManager.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
            if (mode == android.app.AppOpsManager.MODE_ALLOWED) {
                android.util.Log.d("MainActivity", "Usage stats permission granted")
            } else {
                android.util.Log.w("MainActivity", "Usage stats permission denied")
            }
        }
    }
    
    private val requestOverlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Check if overlay permission was granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                android.util.Log.d("MainActivity", "Overlay permission granted")
            } else {
                android.util.Log.w("MainActivity", "Overlay permission denied")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        AppLogger.i("MainActivity", "=== MainActivity onCreate started ===")
        android.util.Log.i("MainActivity", "=== MainActivity onCreate started ===")
        
        // Force restart the AppLockService to ensure new code is loaded
        try {
            AppLogger.i("MainActivity", "Attempting to restart AppLockService...")
            
            // Stop the service first to ensure clean restart
            val stopIntent = Intent(this, com.example.pushapp.service.AppLockService::class.java)
            stopIntent.action = "STOP_MONITORING"
            val stopResult = stopService(stopIntent)
            AppLogger.i("MainActivity", "Stop service result: $stopResult")
            
            // Start the service with new code
            val startIntent = Intent(this, com.example.pushapp.service.AppLockService::class.java)
            startIntent.action = "START_MONITORING"
            val startResult = startService(startIntent)
            AppLogger.i("MainActivity", "Start service result: $startResult")
            AppLogger.i("MainActivity", "AppLockService restart completed successfully")
            
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Failed to restart AppLockService", e)
            android.util.Log.e("MainActivity", "Failed to restart AppLockService", e)
            
            // Fallback: try to start service normally
            try {
                val fallbackIntent = Intent(this, com.example.pushapp.service.AppLockService::class.java)
                fallbackIntent.action = "START_MONITORING"
                startService(fallbackIntent)
                AppLogger.i("MainActivity", "Fallback service start attempted")
                android.util.Log.i("MainActivity", "Fallback service start attempted")
            } catch (fallbackException: Exception) {
                AppLogger.e("MainActivity", "Fallback service start also failed", fallbackException)
                android.util.Log.e("MainActivity", "Fallback service start also failed", fallbackException)
            }
        }
        
        AppLogger.i("MainActivity", "=== MainActivity onCreate completed ===")
        android.util.Log.i("MainActivity", "=== MainActivity onCreate completed ===")
        
        setContent {
            PushAppTheme {
                AppLockApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Service status will be checked when the app initializes
        
        // Check if we should open push-up screen from notification
        intent?.let { intent ->
            if (intent.getBooleanExtra("openPushUpScreen", false)) {
                val lockedAppPackage = intent.getStringExtra("lockedAppPackage")
                if (lockedAppPackage != null) {
                    AppLogger.i("MainActivity", "Opening push-up screen for locked app: $lockedAppPackage")
                    // Navigate to push-up screen directly
                    // This will be handled by the navigation system when the composable is created
                }
            }
        }
    }
    
}

@Composable
fun AppLockApp() {
    val navController = rememberNavController()
    val appLockViewModel: AppLockViewModel = viewModel()
    val context = LocalContext.current
    val permissionChecker = remember { PermissionChecker(context) }
    var permissionState by remember { mutableStateOf<PermissionState?>(null) }
    var showPermissionSetup by remember { mutableStateOf(false) }
    var pendingPushUpNavigation by remember { mutableStateOf<String?>(null) }
    
    // Check permissions on app start
    LaunchedEffect(Unit) {
        val state = permissionChecker.checkAllPermissions()
        permissionState = state
        showPermissionSetup = !state.allPermissionsGranted
        
        // Check if we should navigate to push-up screen
        val activity = context as? MainActivity
        activity?.intent?.let { intent ->
            if (intent.getBooleanExtra("openPushUpScreen", false)) {
                val lockedAppPackage = intent.getStringExtra("lockedAppPackage")
                if (lockedAppPackage != null) {
                    AppLogger.i("MainActivity", "Setting pending push-up navigation for: $lockedAppPackage")
                    pendingPushUpNavigation = lockedAppPackage
                }
            }
        }
    }
    
    // Navigate to push-up screen when pending navigation is set
    LaunchedEffect(pendingPushUpNavigation) {
        if (pendingPushUpNavigation != null && !showPermissionSetup) {
            AppLogger.i("MainActivity", "Navigating to push-up screen for: $pendingPushUpNavigation")
            navController.navigate("pushup/${pendingPushUpNavigation}")
            pendingPushUpNavigation = null
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (showPermissionSetup) {
            PermissionSetupScreen(
                onPermissionsGranted = {
                    showPermissionSetup = false
                },
                onSkip = {
                    showPermissionSetup = false
                }
            )
        } else {
            AppNavigation(
                navController = navController,
                appLockViewModel = appLockViewModel
            )
        }
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    appLockViewModel: AppLockViewModel
) {
    val uiState by appLockViewModel.uiState.collectAsState()
    val installedApps by appLockViewModel.installedApps.collectAsState()
    val appLockSettings by appLockViewModel.appLockSettings.collectAsState()
    val pushUpSettings by appLockViewModel.pushUpSettings.collectAsState()
    
    // Show error/success messages
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            // Show error message (you can implement a snackbar here)
            appLockViewModel.clearError()
        }
    }
    
    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            // Show success message (you can implement a snackbar here)
            appLockViewModel.clearMessage()
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        composable("dashboard") {
            DashboardScreen(
                lockedApps = appLockSettings,
                totalAppsLocked = uiState.lockedApps,
                onUnlockApp = { app ->
                    // Navigate to push-up screen for unlocking
                    navController.navigate("pushup/${app.packageName}")
                },
                onOpenSettings = {
                    navController.navigate("settings")
                },
                onStartPushUps = {
                    navController.navigate("pushup")
                },
                onViewLogs = {
                    android.util.Log.d("MainActivity", "View Logs clicked, navigating to logs")
                    navController.navigate("logs")
                },
                onViewUsageChart = {
                    navController.navigate("usage-chart")
                },
                onOpenPermissions = {
                    navController.navigate("permissions")
                },
                onOpenAppsLocked = {
                    navController.navigate("apps-locked")
                }
            )
        }
        
        composable("settings") {
            AppSelectionScreen(
                installedApps = installedApps,
                appLockSettings = appLockSettings,
                onAppToggle = { app, isLocked ->
                    appLockViewModel.toggleAppLock(app, isLocked)
                },
                onTimeLimitChange = { packageName, timeLimit ->
                    appLockViewModel.updateTimeLimit(packageName, timeLimit)
                },
                onPushUpRequirementChange = { packageName, pushUpCount ->
                    appLockViewModel.updatePushUpRequirement(packageName, pushUpCount)
                },
                onSaveSettings = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("apps-locked") {
            AppsLockedScreen(
                lockedApps = appLockSettings,
                onBack = {
                    navController.popBackStack()
                },
                onTimeLimitChange = { packageName, timeLimit ->
                    appLockViewModel.updateTimeLimit(packageName, timeLimit)
                },
                onPushUpRequirementChange = { packageName, pushUpCount ->
                    appLockViewModel.updatePushUpRequirement(packageName, pushUpCount)
                },
                onToggleAppLock = { packageName, isLocked ->
                    val app = installedApps.find { it.packageName == packageName }
                    if (app != null) {
                        appLockViewModel.toggleAppLock(app, isLocked)
                    }
                }
            )
        }
        
        composable("pushup") {
            PushUpUnlockScreen(
                appLockViewModel = appLockViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("pushup/{packageName}") { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName")
            if (packageName != null) {
                val appSettings = appLockSettings.find { it.packageName == packageName }
                if (appSettings != null) {
                    PushUpUnlockScreen(
                        appLockViewModel = appLockViewModel,
                        targetApp = appSettings,
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
        
        composable("logs") {
            LogViewerScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("usage-chart") {
            UsageChartScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("permissions") {
            PermissionSetupScreen(
                onPermissionsGranted = {
                    navController.popBackStack()
                },
                onSkip = {
                    navController.popBackStack()
                }
            )
        }
    }
}

@Composable
fun PushUpUnlockScreen(
    appLockViewModel: AppLockViewModel,
    targetApp: com.example.pushapp.data.AppLockSettings? = null,
    onBack: () -> Unit
) {
    val pushUpSettings by appLockViewModel.pushUpSettings.collectAsState()
    val context = LocalContext.current
    val pushUpViewModel: PushUpViewModel = viewModel()
    
    // Handle push-up completion
    val onPushUpComplete = { pushUpCount: Int ->
        if (targetApp != null) {
            // Unlock the specific app
            appLockViewModel.unlockAppWithPushUps(targetApp, pushUpCount)
        }
        onBack()
    }
    
    // Use the integration layer
    PushUpIntegration(
        pushUpViewModel = pushUpViewModel,
        targetApp = targetApp,
        onPushUpComplete = onPushUpComplete,
        onClose = onBack,
        modifier = Modifier.fillMaxSize()
    )
}