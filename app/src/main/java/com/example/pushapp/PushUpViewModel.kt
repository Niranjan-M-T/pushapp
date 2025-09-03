package com.example.pushapp

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import com.example.pushapp.PoseDetectionHelper
import androidx.camera.core.ExperimentalGetImage
import com.example.pushapp.utils.AppLogger
import com.example.pushapp.PoseOverlayView

@ExperimentalGetImage
class PushUpViewModel : ViewModel() {
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview: Preview? = null
    private lateinit var cameraExecutor: ExecutorService
    private var lifecycleOwner: androidx.lifecycle.LifecycleOwner? = null
    private var previewView: androidx.camera.view.PreviewView? = null
    private var overlayView: PoseOverlayView? = null
    
    private val poseDetector: PoseDetector by lazy {
        val options = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
        PoseDetection.getClient(options)
    }
    
    // UI State
    var pushUpCount by mutableStateOf(0)
        private set
    
    var isCounting by mutableStateOf(false)
        private set
    
    var isCameraInitialized by mutableStateOf(false)
        private set
    
    // Push-up detection state
    private var isInDownPosition = false
    private var isInUpPosition = false
    private var lastElbowAngle = 0f
    private var lastShoulderAngle = 0f
    
    // Improved push-up state tracking
    private var pushUpState = PushUpState.IDLE
    private var hasBeenInDownPosition = false
    
    enum class PushUpState {
        IDLE,           // Starting state
        GOING_DOWN,     // Moving from up to down
        DOWN,           // In down position
        GOING_UP,       // Moving from down to up
        UP              // In up position
    }
    
    private val poseHelper = PoseDetectionHelper()
    
    fun initializeCamera(context: Context, lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        if (isCameraInitialized) return
        
        try {
            AppLogger.i("PushUpViewModel", "Initializing camera...")
            
            this.lifecycleOwner = lifecycleOwner
            cameraExecutor = Executors.newSingleThreadExecutor()
            
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    isCameraInitialized = true
                    AppLogger.i("PushUpViewModel", "Camera initialized successfully")
                } catch (e: Exception) {
                    AppLogger.e("PushUpViewModel", "Failed to initialize camera", e)
                    throw e
                }
            }, ContextCompat.getMainExecutor(context))
            
        } catch (e: Exception) {
            AppLogger.e("PushUpViewModel", "Camera initialization failed", e)
            throw e
        }
    }
    
    fun setPreviewView(previewView: androidx.camera.view.PreviewView) {
        try {
            this.previewView = previewView
            AppLogger.d("PushUpViewModel", "Preview view set successfully")
        } catch (e: Exception) {
            AppLogger.e("PushUpViewModel", "Failed to set preview view", e)
            throw e
        }
    }
    
    fun setOverlayView(overlayView: PoseOverlayView) {
        try {
            this.overlayView = overlayView
            AppLogger.d("PushUpViewModel", "Overlay view set successfully")
        } catch (e: Exception) {
            AppLogger.e("PushUpViewModel", "Failed to set overlay view", e)
            throw e
        }
    }
    
    fun startCounting() {
        if (!isCameraInitialized) {
            AppLogger.w("PushUpViewModel", "Cannot start counting: camera not initialized")
            return
        }
        
        try {
            isCounting = true
            startImageAnalysis()
            AppLogger.i("PushUpViewModel", "Started push-up counting")
        } catch (e: Exception) {
            AppLogger.e("PushUpViewModel", "Failed to start counting", e)
            isCounting = false
            throw e
        }
    }
    
    fun stopCounting() {
        try {
            isCounting = false
            stopImageAnalysis()
            AppLogger.i("PushUpViewModel", "Stopped push-up counting")
        } catch (e: Exception) {
            AppLogger.e("PushUpViewModel", "Failed to stop counting", e)
            throw e
        }
    }
    
    fun resetCounter() {
        pushUpCount = 0
        isInDownPosition = false
        isInUpPosition = false
        pushUpState = PushUpState.IDLE
        hasBeenInDownPosition = false
        Log.d("PushUpViewModel", "Reset push-up counter and state machine")
    }
    
    fun testCounter() {
        pushUpCount++
        Log.d("PushUpViewModel", "Test counter incremented to: $pushUpCount")
    }
    
    fun releaseCamera() {
        stopCounting()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }
    
    private fun startImageAnalysis() {
        try {
            val cameraProvider = cameraProvider ?: run {
                AppLogger.e("PushUpViewModel", "Camera provider is null")
                return
            }
            val previewView = previewView ?: run {
                AppLogger.e("PushUpViewModel", "Preview view is null")
                return
            }
            
            AppLogger.d("PushUpViewModel", "Starting image analysis...")
            
            // Create preview use case
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            
            // Create image analysis use case
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }
            
            try {
                cameraProvider.unbindAll()
                
                val owner = lifecycleOwner
                if (owner != null) {
                    camera = cameraProvider.bindToLifecycle(
                        owner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    this.imageAnalyzer = imageAnalysis
                    this.preview = preview
                    
                    AppLogger.i("PushUpViewModel", "Image analysis started successfully")
                } else {
                    AppLogger.e("PushUpViewModel", "Lifecycle owner is null")
                }
                
            } catch (e: Exception) {
                AppLogger.e("PushUpViewModel", "Failed to bind image analysis", e)
                throw e
            }
        } catch (e: Exception) {
            AppLogger.e("PushUpViewModel", "Failed to start image analysis", e)
            throw e
        }
    }
    
    private fun stopImageAnalysis() {
        imageAnalyzer?.clearAnalyzer()
        imageAnalyzer = null
        camera = null
    }
    
    private fun processImage(imageProxy: ImageProxy) {
        try {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                
                AppLogger.d("PushUpViewModel", "Processing image: ${image.width}x${image.height}")
                
                poseDetector.process(image)
                    .addOnSuccessListener { pose ->
                        try {
                            AppLogger.d("PushUpViewModel", "Pose detected with ${pose.allPoseLandmarks.size} landmarks")
                            analyzePose(pose, image.width, image.height)
                        } catch (e: Exception) {
                            AppLogger.e("PushUpViewModel", "Error analyzing pose", e)
                        }
                    }
                    .addOnFailureListener { e ->
                        AppLogger.e("PushUpViewModel", "Pose detection failed", e)
                    }
                    .addOnCompleteListener {
                        try {
                            imageProxy.close()
                        } catch (e: Exception) {
                            AppLogger.e("PushUpViewModel", "Error closing image proxy", e)
                        }
                    }
            } else {
                AppLogger.w("PushUpViewModel", "No media image available")
                imageProxy.close()
            }
        } catch (e: Exception) {
            AppLogger.e("PushUpViewModel", "Error processing image", e)
            try {
                imageProxy.close()
            } catch (closeException: Exception) {
                Log.e("PushUpViewModel", "Error closing image proxy after exception", closeException)
            }
        }
    }
    
    private fun analyzePose(pose: com.google.mlkit.vision.pose.Pose, imageWidth: Int, imageHeight: Int) {
        try {
            val poseState = poseHelper.analyzePose(pose)
            
            // Update overlay view with pose data and angles
            overlayView?.let { overlay ->
                try {
                    overlay.updatePose(pose, imageWidth, imageHeight)
                    overlay.updateAngles(poseState.currentElbowAngle, poseState.currentShoulderAngle)
                    overlay.updateThresholds(poseHelper.getCurrentThresholds())
                } catch (e: Exception) {
                    Log.e("PushUpViewModel", "Error updating overlay", e)
                }
            }
            
            if (poseState.currentElbowAngle > 0f && poseState.currentShoulderAngle > 0f) {
                // Use the pose state from the helper to detect push-up state changes
                try {
                    detectPushUpState(poseState)
                } catch (e: Exception) {
                    Log.e("PushUpViewModel", "Error detecting push-up state", e)
                }
                
                lastElbowAngle = poseState.currentElbowAngle
                lastShoulderAngle = poseState.currentShoulderAngle
            }
        } catch (e: Exception) {
            Log.e("PushUpViewModel", "Error in analyzePose", e)
        }
    }
    
    // Functions to adjust thresholds
    fun adjustElbowDownThreshold(value: Float) {
        poseHelper.elbowDownThreshold = value
        Log.d("PushUpViewModel", "Elbow Down Threshold adjusted to: ${value}°")
    }
    
    fun adjustElbowUpThreshold(value: Float) {
        poseHelper.elbowUpThreshold = value
        Log.d("PushUpViewModel", "Elbow Up Threshold adjusted to: ${value}°")
    }
    
    fun adjustShoulderDownThreshold(value: Float) {
        poseHelper.shoulderDownThreshold = value
        Log.d("PushUpViewModel", "Shoulder Down Threshold adjusted to: ${value}°")
    }
    
    fun adjustShoulderUpThreshold(value: Float) {
        poseHelper.shoulderUpThreshold = value
        Log.d("PushUpViewModel", "Shoulder Up Threshold adjusted to: ${value}°")
    }
    
    fun resetThresholds() {
        poseHelper.resetThresholds()
        Log.d("PushUpViewModel", "Thresholds reset to defaults")
    }
    
    fun getCurrentThresholds(): Map<String, Float> {
        return poseHelper.getCurrentThresholds()
    }
    
    fun getDebugInfo(): String {
        val thresholds = getCurrentThresholds()
        return buildString {
            appendLine("Current State:")
            appendLine("  Down: $isInDownPosition")
            appendLine("  Up: $isInUpPosition")
            appendLine("  Counter: $pushUpCount")
            appendLine("  PushUp State: $pushUpState")
            appendLine("  Has Been Down: $hasBeenInDownPosition")
            appendLine("")
            appendLine("Current Thresholds:")
            appendLine("  Elbow Down: <${thresholds["elbowDown"]?.toInt()}°")
            appendLine("  Elbow Up: >${thresholds["elbowUp"]?.toInt()}°")
            appendLine("  Shoulder Down: >${thresholds["shoulderDown"]?.toInt()}°")
            appendLine("  Shoulder Up: <${thresholds["shoulderUp"]?.toInt()}°")
            appendLine("")
            appendLine("Last Angles:")
            appendLine("  Elbow: ${lastElbowAngle.toInt()}°")
            appendLine("  Shoulder: ${lastShoulderAngle.toInt()}°")
        }
    }
    
    private fun detectPushUpState(poseState: PoseDetectionHelper.PushUpState) {
        // Add debugging information
        Log.d("PushUpViewModel", "=== PUSH-UP DETECTION ===")
        Log.d("PushUpViewModel", "Current State - Down: $isInDownPosition, Up: $isInUpPosition")
        Log.d("PushUpViewModel", "New State - Down: ${poseState.isInDownPosition}, Up: ${poseState.isInUpPosition}")
        Log.d("PushUpViewModel", "Elbow: ${poseState.currentElbowAngle.toInt()}°, Shoulder: ${poseState.currentShoulderAngle.toInt()}°")
        Log.d("PushUpViewModel", "Current PushUp State: $pushUpState")
        
        // Store previous state before updating
        val wasInDownPosition = isInDownPosition
        val wasInUpPosition = isInUpPosition
        val previousPushUpState = pushUpState
        
        // Update our internal state
        isInDownPosition = poseState.isInDownPosition
        isInUpPosition = poseState.isInUpPosition
        
        // State machine logic for push-up detection
        when (pushUpState) {
            PushUpState.IDLE -> {
                // Starting state - wait for up position
                if (poseState.isInUpPosition) {
                    pushUpState = PushUpState.UP
                    Log.d("PushUpViewModel", "🔄 State: IDLE → UP (Starting position)")
                }
            }
            
            PushUpState.UP -> {
                // In up position - wait for down movement
                if (poseState.isInDownPosition) {
                    pushUpState = PushUpState.DOWN
                    hasBeenInDownPosition = true
                    Log.d("PushUpViewModel", "🔄 State: UP → DOWN (Going down)")
                }
            }
            
            PushUpState.DOWN -> {
                // In down position - wait for up movement
                if (poseState.isInUpPosition) {
                    pushUpState = PushUpState.UP
                    // Complete push-up cycle detected!
                    pushUpCount++
                    Log.d("PushUpViewModel", "🎉 PUSH-UP COMPLETED! Count: $pushUpCount")
                    Log.d("PushUpViewModel", "Elbow: ${poseState.currentElbowAngle.toInt()}°, Shoulder: ${poseState.currentShoulderAngle.toInt()}°")
                    
                    // Trigger visual animation
                    overlayView?.onPushUpCompleted(pushUpCount)
                }
            }
            
            PushUpState.GOING_DOWN -> {
                // This state is not currently used but kept for future enhancement
                if (poseState.isInDownPosition) {
                    pushUpState = PushUpState.DOWN
                    hasBeenInDownPosition = true
                    Log.d("PushUpViewModel", "🔄 State: GOING_DOWN → DOWN")
                }
            }
            
            PushUpState.GOING_UP -> {
                // This state is not currently used but kept for future enhancement
                if (poseState.isInUpPosition) {
                    pushUpState = PushUpState.UP
                    // Complete push-up cycle detected!
                    pushUpCount++
                    Log.d("PushUpViewModel", "🎉 PUSH-UP COMPLETED! Count: $pushUpCount")
                    Log.d("PushUpViewModel", "Elbow: ${poseState.currentElbowAngle.toInt()}°, Shoulder: ${poseState.currentShoulderAngle.toInt()}°")
                    
                    // Trigger visual animation
                    overlayView?.onPushUpCompleted(pushUpCount)
                }
            }
        }
        
        Log.d("PushUpViewModel", "=== STATE TRANSITION ANALYSIS ===")
        Log.d("PushUpViewModel", "Previous State - Down: $wasInDownPosition, Up: $wasInUpPosition")
        Log.d("PushUpViewModel", "Current State - Down: $isInDownPosition, Up: $isInUpPosition")
        Log.d("PushUpViewModel", "Pose State - Down: ${poseState.isInDownPosition}, Up: ${poseState.isInUpPosition}")
        Log.d("PushUpViewModel", "PushUp State: $previousPushUpState → $pushUpState")
        Log.d("PushUpViewModel", "Has Been Down: $hasBeenInDownPosition")
        
        // Log state changes for debugging
        if (poseState.isInDownPosition && !wasInDownPosition) {
            Log.d("PushUpViewModel", "⬇️ Down position detected - Elbow: ${poseState.currentElbowAngle.toInt()}°, Shoulder: ${poseState.currentShoulderAngle.toInt()}°")
        }
        
        if (poseState.isInUpPosition && !wasInUpPosition) {
            Log.d("PushUpViewModel", "⬆️ Up position detected - Elbow: ${poseState.currentElbowAngle.toInt()}°, Shoulder: ${poseState.currentShoulderAngle.toInt()}°")
        }
        
        Log.d("PushUpViewModel", "========================")
    }
    
    override fun onCleared() {
        super.onCleared()
        stopCounting()
        cameraExecutor.shutdown()
        poseDetector.close()
    }
}
