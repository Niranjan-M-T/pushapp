package com.example.pushapp

import android.content.Context
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.camera.core.ExperimentalGetImage
import com.example.pushapp.utils.AppLogger
import com.example.pushapp.PoseOverlayView

@ExperimentalGetImage
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    viewModel: PushUpViewModel,
    onCameraError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Get current orientation
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    DisposableEffect(context) {
        try {
            AppLogger.i("CameraPreview", "Initializing camera...")
            viewModel.initializeCamera(context, lifecycleOwner)
        } catch (e: Exception) {
            AppLogger.e("CameraPreview", "Camera initialization failed", e)
            hasError = true
            errorMessage = "Camera initialization failed: ${e.message}"
            onCameraError(errorMessage)
        }
        
        onDispose {
            AppLogger.i("CameraPreview", "Releasing camera...")
            viewModel.releaseCamera()
        }
    }
    
    if (hasError) {
        AppLogger.w("CameraPreview", "Showing camera error fallback: $errorMessage")
        return
    }
    
    AndroidView(
        factory = { context ->
            try {
                AppLogger.d("CameraPreview", "Creating camera preview views...")
                
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
                    
                    AppLogger.d("CameraPreview", "Camera preview views created successfully")
                }
            } catch (e: Exception) {
                AppLogger.e("CameraPreview", "Failed to create camera preview views", e)
                hasError = true
                errorMessage = "Failed to create camera preview: ${e.message}"
                onCameraError(errorMessage)
                
                // Return a simple view as fallback
                android.widget.TextView(context).apply {
                    text = "Camera Error: ${e.message}"
                    setTextColor(android.graphics.Color.RED)
                }
            }
        },
        modifier = modifier.fillMaxSize(),
        update = { frameLayout ->
            try {
                if (frameLayout is android.widget.FrameLayout) {
                    val previewView = frameLayout.findViewById<PreviewView>(android.R.id.content)
                    val overlayView = frameLayout.findViewById<PoseOverlayView>(android.R.id.background)
                    
                    if (previewView != null && overlayView != null) {
                        AppLogger.d("CameraPreview", "Setting preview and overlay views...")
                        viewModel.setPreviewView(previewView)
                        viewModel.setOverlayView(overlayView)
                        
                        // Start counting after views are set
                        viewModel.startCounting()
                        AppLogger.i("CameraPreview", "Camera preview setup complete, counting started")
                    } else {
                        AppLogger.w("CameraPreview", "Preview or overlay view is null")
                        hasError = true
                        errorMessage = "Camera views not found"
                        onCameraError(errorMessage)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("CameraPreview", "Error updating camera preview", e)
                hasError = true
                errorMessage = "Camera update failed: ${e.message}"
                onCameraError(errorMessage)
            }
        }
    )
}
