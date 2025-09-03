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
    
    private val poseHelper = PoseDetectionHelper()
    
    fun initializeCamera(context: Context, lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        if (isCameraInitialized) return
        
        this.lifecycleOwner = lifecycleOwner
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                isCameraInitialized = true
                Log.d("PushUpViewModel", "Camera initialized successfully")
            } catch (e: Exception) {
                Log.e("PushUpViewModel", "Failed to initialize camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    fun setPreviewView(previewView: androidx.camera.view.PreviewView) {
        this.previewView = previewView
    }
    
    fun setOverlayView(overlayView: PoseOverlayView) {
        this.overlayView = overlayView
    }
    
    fun startCounting() {
        if (!isCameraInitialized) return
        
        isCounting = true
        startImageAnalysis()
        Log.d("PushUpViewModel", "Started push-up counting")
    }
    
    fun stopCounting() {
        isCounting = false
        stopImageAnalysis()
        Log.d("PushUpViewModel", "Stopped push-up counting")
    }
    
    fun resetCounter() {
        pushUpCount = 0
        isInDownPosition = false
        isInUpPosition = false
        Log.d("PushUpViewModel", "Reset push-up counter")
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
        val cameraProvider = cameraProvider ?: return
        val previewView = previewView ?: return
        
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
            }
            
        } catch (e: Exception) {
            Log.e("PushUpViewModel", "Failed to bind image analysis", e)
        }
    }
    
    private fun stopImageAnalysis() {
        imageAnalyzer?.clearAnalyzer()
        imageAnalyzer = null
        camera = null
    }
    
    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            poseDetector.process(image)
                .addOnSuccessListener { pose ->
                    analyzePose(pose, image.width, image.height)
                }
                .addOnFailureListener { e ->
                    Log.e("PushUpViewModel", "Pose detection failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
    
    private fun analyzePose(pose: com.google.mlkit.vision.pose.Pose, imageWidth: Int, imageHeight: Int) {
        val poseState = poseHelper.analyzePose(pose)
        
        // Update overlay view with pose data and angles
        overlayView?.let { overlay ->
            overlay.updatePose(pose, imageWidth, imageHeight)
            overlay.updateAngles(poseState.currentElbowAngle, poseState.currentShoulderAngle)
            overlay.updateThresholds(poseHelper.getCurrentThresholds())
        }
        
        if (poseState.currentElbowAngle > 0f && poseState.currentShoulderAngle > 0f) {
            // Use the pose state from the helper to detect push-up state changes
            detectPushUpState(poseState)
            
            lastElbowAngle = poseState.currentElbowAngle
            lastShoulderAngle = poseState.currentShoulderAngle
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
        
        // Store previous state before updating
        val wasInDownPosition = isInDownPosition
        val wasInUpPosition = isInUpPosition
        
        // Update our internal state
        isInDownPosition = poseState.isInDownPosition
        isInUpPosition = poseState.isInUpPosition
        
        // Check if we're transitioning from down to up position (completing a push-up)
        val shouldCountPushUp = poseState.isInUpPosition && !wasInUpPosition && wasInDownPosition
        
        Log.d("PushUpViewModel", "=== STATE TRANSITION ANALYSIS ===")
        Log.d("PushUpViewModel", "Previous State - Down: $wasInDownPosition, Up: $wasInUpPosition")
        Log.d("PushUpViewModel", "Current State - Down: $isInDownPosition, Up: $isInUpPosition")
        Log.d("PushUpViewModel", "Pose State - Down: ${poseState.isInDownPosition}, Up: ${poseState.isInUpPosition}")
        Log.d("PushUpViewModel", "Should Count Push-up: $shouldCountPushUp")
        Log.d("PushUpViewModel", "Condition Breakdown:")
        Log.d("PushUpViewModel", "  - Currently in up position: ${poseState.isInUpPosition}")
        Log.d("PushUpViewModel", "  - Was NOT in up position: ${!wasInUpPosition}")
        Log.d("PushUpViewModel", "  - Was in down position: $wasInDownPosition")
        Log.d("PushUpViewModel", "  - All conditions met: ${poseState.isInUpPosition && !wasInUpPosition && wasInDownPosition}")
        
        if (shouldCountPushUp) {
            // We were in down position and now we're in up position - complete push-up!
            pushUpCount++
            Log.d("PushUpViewModel", "🎉 PUSH-UP COMPLETED! Count: $pushUpCount")
            Log.d("PushUpViewModel", "Elbow: ${poseState.currentElbowAngle.toInt()}°, Shoulder: ${poseState.currentShoulderAngle.toInt()}°")
            
            // Trigger visual animation
            overlayView?.onPushUpCompleted(pushUpCount)
        } else {
            // Log why push-up wasn't counted
            Log.d("PushUpViewModel", "❌ Push-up NOT counted:")
            Log.d("PushUpViewModel", "  - Currently in up position: ${poseState.isInUpPosition}")
            Log.d("PushUpViewModel", "  - Was in up position: $wasInUpPosition")
            Log.d("PushUpViewModel", "  - Was in down position: $wasInDownPosition")
            Log.d("PushUpViewModel", "  - Condition: ${poseState.isInUpPosition && !wasInUpPosition && wasInDownPosition}")
        }
        
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
