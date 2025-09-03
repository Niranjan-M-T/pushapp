package com.example.pushapp

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    viewModel: PushUpViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Get current orientation
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    
    DisposableEffect(context) {
        viewModel.initializeCamera(context, lifecycleOwner)
        onDispose {
            viewModel.releaseCamera()
        }
    }
    
    AndroidView(
        factory = { context ->
            // Create a FrameLayout to hold both the camera preview and overlay
            android.widget.FrameLayout(context).apply {
                // Create the camera preview
                val previewView = PreviewView(context).apply {
                    this.scaleType = if (isLandscape) {
                        PreviewView.ScaleType.FILL_CENTER
                    } else {
                        PreviewView.ScaleType.FILL_CENTER
                    }
                    this.id = android.R.id.content
                }
                
                // Create the pose overlay view
                val overlayView = PoseOverlayView(context).apply {
                    this.id = android.R.id.background
                }
                
                // Add both views to the FrameLayout
                addView(previewView)
                addView(overlayView)
                
                // Set the overlay view to be on top
                bringChildToFront(overlayView)
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { frameLayout ->
            val previewView = frameLayout.findViewById<PreviewView>(android.R.id.content)
            val overlayView = frameLayout.findViewById<PoseOverlayView>(android.R.id.background)
            
            viewModel.setPreviewView(previewView)
            viewModel.setOverlayView(overlayView)
        }
    )
}
