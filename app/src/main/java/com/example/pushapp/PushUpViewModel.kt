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
                    analyzePose(pose)
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
    
    private fun analyzePose(pose: com.google.mlkit.vision.pose.Pose) {
        val poseState = poseHelper.analyzePose(pose)
        
        if (poseState.currentElbowAngle > 0f && poseState.currentShoulderAngle > 0f) {
            // Detect push-up state changes
            detectPushUpState(poseState.currentElbowAngle, poseState.currentShoulderAngle)
            
            lastElbowAngle = poseState.currentElbowAngle
            lastShoulderAngle = poseState.currentShoulderAngle
        }
    }
    

    
    private fun detectPushUpState(elbowAngle: Float, shoulderAngle: Float) {
        // Push-up down position: elbows bent (angle < 90 degrees) and body lowered
        if (elbowAngle < 90f && shoulderAngle < 45f && !isInDownPosition) {
            isInDownPosition = true
            isInUpPosition = false
            Log.d("PushUpViewModel", "Down position detected - Elbow: ${elbowAngle.toInt()}°, Shoulder: ${shoulderAngle.toInt()}°")
        }
        
        // Push-up up position: elbows extended (angle > 160 degrees) and body raised
        if (elbowAngle > 160f && shoulderAngle > 45f && isInDownPosition && !isInUpPosition) {
            isInUpPosition = true
            isInDownPosition = false
            
            // Increment counter only when we complete a full push-up cycle
            pushUpCount++
            Log.d("PushUpViewModel", "Push-up completed! Count: $pushUpCount - Elbow: ${elbowAngle.toInt()}°, Shoulder: ${shoulderAngle.toInt()}°")
        }
        
        // Reset state if user goes back to standing position
        if (shoulderAngle > 80f) {
            isInDownPosition = false
            isInUpPosition = false
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopCounting()
        cameraExecutor.shutdown()
        poseDetector.close()
    }
}
