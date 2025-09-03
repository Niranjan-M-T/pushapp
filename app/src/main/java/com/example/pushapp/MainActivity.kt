package com.example.pushapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, camera will be initialized when the app starts
            Log.d("MainActivity", "Camera permission granted")
        } else {
            // Permission denied, show a message to the user
            Log.w("MainActivity", "Camera permission denied")
            // You could show a dialog here explaining why camera permission is needed
        }
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
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        
        setContent {
            PushUpApp()
        }
    }
}

@Composable
fun PushUpApp() {
    val viewModel: PushUpViewModel = viewModel()
    var showSettings by remember { mutableStateOf(false) }
    
    // Get current orientation
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (isLandscape) {
                // Landscape layout - side by side
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Left side - Camera and Controls
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Push-Up Counter",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // Camera preview
                        CameraPreview(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            viewModel = viewModel
                        )
                        
                        // Control buttons
                        ControlButtons(
                            onStart = { viewModel.startCounting() },
                            onStop = { viewModel.stopCounting() },
                            onReset = { viewModel.resetCounter() },
                            onTest = { viewModel.testCounter() },
                            onSettings = { showSettings = !showSettings },
                            isCounting = viewModel.isCounting,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    
                    // Right side - Status and Settings
                    Column(
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight()
                            .padding(start = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Status and counter display
                        StatusAndCounterDisplay(
                            pushUpCount = viewModel.pushUpCount,
                            isCounting = viewModel.isCounting,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // Settings panel
                        if (showSettings) {
                            ThresholdSettingsPanel(
                                viewModel = viewModel,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        // Debug panel - always visible
                        DebugPanel(
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // Portrait layout - stacked vertically
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Push-Up Counter",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Camera preview
                    CameraPreview(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        viewModel = viewModel
                    )
                    
                    // Status and counter display
                    StatusAndCounterDisplay(
                        pushUpCount = viewModel.pushUpCount,
                        isCounting = viewModel.isCounting,
                        modifier = Modifier.padding(16.dp)
                    )
                    
                    // Control buttons
                    ControlButtons(
                        onStart = { viewModel.startCounting() },
                        onStop = { viewModel.stopCounting() },
                        onReset = { viewModel.resetCounter() },
                        onTest = { viewModel.testCounter() },
                        onSettings = { showSettings = !showSettings },
                        isCounting = viewModel.isCounting,
                        modifier = Modifier.padding(16.dp)
                    )
                    
                                    // Settings panel
                if (showSettings) {
                    ThresholdSettingsPanel(
                        viewModel = viewModel,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                
                // Debug panel - always visible
                DebugPanel(
                    viewModel = viewModel,
                    modifier = Modifier.padding(top = 16.dp)
                )
                }
            }
        }
    }
}

@Composable
fun StatusAndCounterDisplay(
    pushUpCount: Int,
    isCounting: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status indicator
            Row(
                modifier = Modifier.padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = if (isCounting) Color.Green else Color.Red,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCounting) "Detecting..." else "Stopped",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCounting) Color.Green else Color.Red
                )
            }
            
            // Counter
            Text(
                text = "Push-ups Completed",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "$pushUpCount",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Instructions
            if (!isCounting) {
                Text(
                    text = "Position yourself in front of the camera and press Start",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Blue markers show detected body parts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "Angles are displayed in real-time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "Keep your body in frame for best detection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            
            // Pose detection tips
            if (isCounting) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Detection Tips:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "• Face the camera directly",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "• Keep arms and shoulders visible",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "• Maintain good lighting",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}




@Composable
fun ControlButtons(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onTest: () -> Unit,
    onSettings: () -> Unit,
    isCounting: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = if (isCounting) onStop else onStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCounting) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isCounting) "Stop" else "Start")
            }
            
            Button(
                onClick = onReset,
                enabled = !isCounting
            ) {
                Text("Reset")
            }
        }
        
        // Test button for debugging
        if (!isCounting) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onTest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Test Counter (Debug)")
            }
        }

        // Settings button
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onSettings,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        ) {
            Text("Settings")
        }
    }
}

@Composable
fun ThresholdSettingsPanel(
    viewModel: PushUpViewModel,
    modifier: Modifier = Modifier
) {
    val currentThresholds = viewModel.getCurrentThresholds()
    
    var elbowDownThreshold by remember { mutableStateOf(currentThresholds["elbowDown"] ?: 115f) }
    var elbowUpThreshold by remember { mutableStateOf(currentThresholds["elbowUp"] ?: 172f) }
    var shoulderDownThreshold by remember { mutableStateOf(currentThresholds["shoulderDown"] ?: 90f) }
    var shoulderUpThreshold by remember { mutableStateOf(currentThresholds["shoulderUp"] ?: 17f) }
    
    // Update local state when thresholds change
    LaunchedEffect(currentThresholds) {
        elbowDownThreshold = currentThresholds["elbowDown"] ?: 115f
        elbowUpThreshold = currentThresholds["elbowUp"] ?: 172f
        shoulderDownThreshold = currentThresholds["shoulderDown"] ?: 90f
        shoulderUpThreshold = currentThresholds["shoulderUp"] ?: 17f
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Detection Thresholds",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Elbow Down Threshold
            ThresholdSlider(
                label = "Elbow Down Threshold",
                value = elbowDownThreshold,
                onValueChange = { 
                    elbowDownThreshold = it
                    viewModel.adjustElbowDownThreshold(it)
                },
                valueRange = 100f..130f,  // 115° range
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Elbow Up Threshold
            ThresholdSlider(
                label = "Elbow Up Threshold",
                value = elbowUpThreshold,
                onValueChange = { 
                    elbowUpThreshold = it
                    viewModel.adjustElbowUpThreshold(it)
                },
                valueRange = 160f..180f,  // 165-180° range
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Shoulder Down Threshold
            ThresholdSlider(
                label = "Shoulder Down Threshold",
                value = shoulderDownThreshold,
                onValueChange = { 
                    shoulderDownThreshold = it
                    viewModel.adjustShoulderDownThreshold(it)
                },
                valueRange = 70f..110f,  // 90° range
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Shoulder Up Threshold
            ThresholdSlider(
                label = "Shoulder Up Threshold",
                value = shoulderUpThreshold,
                onValueChange = { 
                    shoulderUpThreshold = it
                    viewModel.adjustShoulderUpThreshold(it)
                },
                valueRange = 5f..30f,  // 10-25° range
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Reset button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        viewModel.resetThresholds()
                        // Reset local state to new defaults
                        elbowDownThreshold = 115f
                        elbowUpThreshold = 172f
                        shoulderDownThreshold = 90f
                        shoulderUpThreshold = 17f
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Reset to Defaults")
                }
            }
        }
    }
}

@Composable
fun ThresholdSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "${value.toInt()}°",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = ((valueRange.endInclusive - valueRange.start) / 2).toInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
fun DebugPanel(
    viewModel: PushUpViewModel,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "🔍 Debug Information",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Current thresholds
            Text(
                text = "Current Thresholds:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            
            val thresholds = viewModel.getCurrentThresholds()
            Text(
                text = "Elbow: Down<${thresholds["elbowDown"]?.toInt()}° Up>${thresholds["elbowUp"]?.toInt()}°",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Shoulder: Down>${thresholds["shoulderDown"]?.toInt()}° Up<${thresholds["shoulderUp"]?.toInt()}°",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Current state
            Text(
                text = "Current State:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            
            // This will be updated by the ViewModel
            var debugInfo by remember { mutableStateOf("Waiting for pose data...") }
            
            // Update debug info when state changes
            LaunchedEffect(Unit) {
                while (true) {
                    delay(500) // Update every 500ms
                    val currentState = viewModel.getDebugInfo()
                    debugInfo = currentState
                }
            }
            
            Text(
                text = debugInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Instructions
            Text(
                text = "💡 Check Android Studio Logcat for detailed logs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}