package com.example.pushapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // TODO: Handle permission granted
            // Permission granted, camera will be initialized
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
    
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
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
                
                // Counter display
                CounterDisplay(
                    pushUpCount = viewModel.pushUpCount,
                    modifier = Modifier.padding(16.dp)
                )
                
                // Control buttons
                ControlButtons(
                    onStart = { viewModel.startCounting() },
                    onStop = { viewModel.stopCounting() },
                    onReset = { viewModel.resetCounter() },
                    isCounting = viewModel.isCounting,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}



@Composable
fun CounterDisplay(
    pushUpCount: Int,
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
            Text(
                text = "Push-ups Completed",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "$pushUpCount",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ControlButtons(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    isCounting: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
}