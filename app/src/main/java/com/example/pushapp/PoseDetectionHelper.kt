package com.example.pushapp

import android.util.Log
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class PoseDetectionHelper {
    
    companion object {
        private const val TAG = "PoseDetectionHelper"
        
        // Default push-up detection thresholds - UPDATED FOR BETTER DETECTION
        private const val DEFAULT_ELBOW_DOWN_THRESHOLD = 135f  // Elbow down when < 135° (more realistic)
        private const val DEFAULT_ELBOW_UP_THRESHOLD = 160f    // Elbow up when > 160° (reduced from 170° by 10°)
        private const val DEFAULT_SHOULDER_DOWN_THRESHOLD = 37f // Shoulder down when > 37° (more realistic)
        private const val DEFAULT_SHOULDER_UP_THRESHOLD = 27f   // Shoulder up when < 27° (increased from 17° by 10°)
        
        // Confidence threshold for landmark detection
        private const val CONFIDENCE_THRESHOLD = 0.5f
    }
    
    // Dynamic thresholds that can be adjusted at runtime
    var elbowDownThreshold: Float = DEFAULT_ELBOW_DOWN_THRESHOLD
    var elbowUpThreshold: Float = DEFAULT_ELBOW_UP_THRESHOLD
    var shoulderDownThreshold: Float = DEFAULT_SHOULDER_DOWN_THRESHOLD
    var shoulderUpThreshold: Float = DEFAULT_SHOULDER_UP_THRESHOLD
    
    // Function to reset thresholds to defaults
    fun resetThresholds() {
        elbowDownThreshold = DEFAULT_ELBOW_DOWN_THRESHOLD
        elbowUpThreshold = DEFAULT_ELBOW_UP_THRESHOLD
        shoulderDownThreshold = DEFAULT_SHOULDER_DOWN_THRESHOLD
        shoulderUpThreshold = DEFAULT_SHOULDER_UP_THRESHOLD
    }
    
    // Function to get current threshold values
    fun getCurrentThresholds(): Map<String, Float> {
        return mapOf(
            "elbowDown" to elbowDownThreshold,
            "elbowUp" to elbowUpThreshold,
            "shoulderDown" to shoulderDownThreshold,
            "shoulderUp" to shoulderUpThreshold
        )
    }
    
    data class PushUpState(
        val isInDownPosition: Boolean = false,
        val isInUpPosition: Boolean = false,
        val pushUpCount: Int = 0,
        val currentElbowAngle: Float = 0f,
        val currentShoulderAngle: Float = 0f
    )
    
    fun analyzePose(pose: Pose): PushUpState {
        val landmarks = pose.allPoseLandmarks
        
        if (landmarks.isEmpty()) {
            Log.d(TAG, "No pose landmarks detected")
            return PushUpState()
        }
        
        // Get key body points with confidence check
        val leftShoulder = landmarks.find { 
            it.inFrameLikelihood > CONFIDENCE_THRESHOLD && 
            it.landmarkType == PoseLandmark.LEFT_SHOULDER 
        }
        val rightShoulder = landmarks.find { 
            it.inFrameLikelihood > CONFIDENCE_THRESHOLD && 
            it.landmarkType == PoseLandmark.RIGHT_SHOULDER 
        }
        val leftElbow = landmarks.find { 
            it.inFrameLikelihood > CONFIDENCE_THRESHOLD && 
            it.landmarkType == PoseLandmark.LEFT_ELBOW 
        }
        val rightElbow = landmarks.find { 
            it.inFrameLikelihood > CONFIDENCE_THRESHOLD && 
            it.landmarkType == PoseLandmark.RIGHT_ELBOW 
        }
        val leftWrist = landmarks.find { 
            it.inFrameLikelihood > CONFIDENCE_THRESHOLD && 
            it.landmarkType == PoseLandmark.LEFT_WRIST 
        }
        val rightWrist = landmarks.find { 
            it.inFrameLikelihood > CONFIDENCE_THRESHOLD && 
            it.landmarkType == PoseLandmark.RIGHT_WRIST 
        }
        val leftHip = landmarks.find { 
            it.inFrameLikelihood > CONFIDENCE_THRESHOLD && 
            it.landmarkType == PoseLandmark.LEFT_HIP 
        }
        val rightHip = landmarks.find { 
            it.inFrameLikelihood > CONFIDENCE_THRESHOLD && 
            it.landmarkType == PoseLandmark.RIGHT_HIP 
        }
        
        // Check if we have enough landmarks for analysis
        val requiredLandmarks = listOf(leftShoulder, rightShoulder, leftElbow, rightElbow, leftWrist, rightWrist, leftHip, rightHip)
        val missingLandmarks = requiredLandmarks.count { it == null }
        
        if (missingLandmarks > 0) {
            Log.d(TAG, "Missing $missingLandmarks landmarks for pose analysis")
            return PushUpState()
        }
        
        if (leftShoulder != null && rightShoulder != null && 
            leftElbow != null && rightElbow != null && 
            leftWrist != null && rightWrist != null &&
            leftHip != null && rightHip != null) {
            
            try {
                // Calculate angles
                val leftElbowAngle = calculateAngle(leftShoulder.position, leftElbow.position, leftWrist.position)
                val rightElbowAngle = calculateAngle(rightShoulder.position, rightElbow.position, rightWrist.position)
                val leftShoulderAngle = calculateAngle(leftElbow.position, leftShoulder.position, leftHip.position)
                val rightShoulderAngle = calculateAngle(rightElbow.position, rightShoulder.position, rightHip.position)
                
                // Check if angles are valid (not NaN or infinite)
                if (leftElbowAngle.isNaN() || rightElbowAngle.isNaN() || 
                    leftShoulderAngle.isNaN() || rightShoulderAngle.isNaN() ||
                    leftElbowAngle.isInfinite() || rightElbowAngle.isInfinite() ||
                    leftShoulderAngle.isInfinite() || rightShoulderAngle.isInfinite()) {
                    Log.w(TAG, "Invalid angles calculated, skipping pose analysis")
                    return PushUpState()
                }
                
                // Average angles for more stable detection
                val avgElbowAngle = (leftElbowAngle + rightElbowAngle) / 2f
                val avgShoulderAngle = (leftShoulderAngle + rightShoulderAngle) / 2f
                
                Log.d(TAG, "Elbow: ${avgElbowAngle.toInt()}°, Shoulder: ${avgShoulderAngle.toInt()}°")
                
                // Determine push-up state based on angles
                val isInDownPosition = avgElbowAngle < elbowDownThreshold && avgShoulderAngle > shoulderDownThreshold
                val isInUpPosition = avgElbowAngle > elbowUpThreshold && avgShoulderAngle < shoulderUpThreshold
                
                // Add detailed logging for debugging
                Log.d(TAG, "=== POSE ANALYSIS ===")
                Log.d(TAG, "Elbow: ${avgElbowAngle.toInt()}° (Down: <${elbowDownThreshold}°, Up: >${elbowUpThreshold}°)")
                Log.d(TAG, "Shoulder: ${avgShoulderAngle.toInt()}° (Down: <${shoulderDownThreshold}°, Up: >${shoulderUpThreshold}°)")
                Log.d(TAG, "Down Position: $isInDownPosition")
                Log.d(TAG, "Up Position: $isInUpPosition")
                Log.d(TAG, "=====================")
                
                return PushUpState(
                    isInDownPosition = isInDownPosition,
                    isInUpPosition = isInUpPosition,
                    currentElbowAngle = avgElbowAngle,
                    currentShoulderAngle = avgShoulderAngle
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating angles", e)
                return PushUpState()
            }
        }
        
        return PushUpState()
    }
    
    private fun calculateAngle(point1: android.graphics.PointF, point2: android.graphics.PointF, point3: android.graphics.PointF): Float {
        val vector1 = android.graphics.PointF(point1.x - point2.x, point1.y - point2.y)
        val vector2 = android.graphics.PointF(point3.x - point2.x, point3.y - point2.y)
        
        val dotProduct = vector1.x * vector2.x + vector1.y * vector2.y
        val magnitude1 = sqrt(vector1.x * vector1.x + vector1.y * vector1.y)
        val magnitude2 = sqrt(vector2.x * vector2.x + vector2.y * vector2.y)
        
        if (magnitude1 == 0.0f || magnitude2 == 0.0f) return 0f
        
        val cosAngle = dotProduct / (magnitude1 * magnitude2)
        return acos(max(-1f, min(1f, cosAngle))) * (180f / kotlin.math.PI.toFloat())
    }
}
