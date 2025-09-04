package com.example.pushapp

import android.Manifest
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
import com.example.pushapp.integration.PushUpIntegration
import com.example.pushapp.utils.AppLogger

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
        // Handle usage stats permission result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check and request camera permission
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
            }
            else -> {
                // Request camera permission directly
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        
        // Check usage stats permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
            
            if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
                // Request usage stats permission directly
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                requestUsageStatsPermissionLauncher.launch(intent)
            }
        }
        
        // Check foreground service permission for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                this,
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"
            ) != PackageManager.PERMISSION_GRANTED) {
                // Request the permission
                requestPermissionLauncher.launch("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
            }
        }
        
        // Check overlay permission
        if (!hasOverlayPermission()) {
            requestOverlayPermission()
        }
        
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
                    // Navigate to push-up screen
                    // This will be handled by the navigation system
                }
            }
        }
    }
    
    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }
    
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }
}

@Composable
fun AppLockApp() {
    val navController = rememberNavController()
    val appLockViewModel: AppLockViewModel = viewModel()
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AppNavigation(
            navController = navController,
            appLockViewModel = appLockViewModel
        )
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
                onStartMonitoring = {
                    appLockViewModel.startMonitoring()
                },
                onStopMonitoring = {
                    appLockViewModel.stopMonitoring()
                },
                onViewLogs = {
                    navController.navigate("logs")
                },
                isMonitoring = uiState.isMonitoring
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