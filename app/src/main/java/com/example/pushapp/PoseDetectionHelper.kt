package com.example.pushapp

import android.util.Log
import com.google.mlkit.vision.common.Point3D
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class PoseDetectionHelper {
    
    companion object {
        private const val TAG = "PoseDetectionHelper"
        
        // Push-up detection thresholds
        // private const val ELBOW_DOWN_THRESHOLD = 90f
        // private const val ELBOW_UP_THRESHOLD = 160f
        // private const val SHOULDER_DOWN_THRESHOLD = 45f
        // private const val SHOULDER_UP_THRESHOLD = 45f
        // private const val SHOULDER_RESET_THRESHOLD = 80f
        
        // Confidence threshold for landmark detection
        private const val CONFIDENCE_THRESHOLD = 0.5f
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
        
        if (leftShoulder != null && rightShoulder != null && 
            leftElbow != null && rightElbow != null && 
            leftWrist != null && rightWrist != null &&
            leftHip != null && rightHip != null) {
            
            // Calculate angles
            val leftElbowAngle = calculateAngle(leftShoulder.position, leftElbow.position, leftWrist.position)
            val rightElbowAngle = calculateAngle(rightShoulder.position, rightElbow.position, rightWrist.position)
            val leftShoulderAngle = calculateAngle(leftElbow.position, leftShoulder.position, leftHip.position)
            val rightShoulderAngle = calculateAngle(rightElbow.position, rightShoulder.position, rightHip.position)
            
            // Average angles for more stable detection
            val avgElbowAngle = (leftElbowAngle + rightElbowAngle) / 2f
            val avgShoulderAngle = (leftShoulderAngle + rightShoulderAngle) / 2f
            
            Log.d(TAG, "Elbow: ${avgElbowAngle.toInt()}°, Shoulder: ${avgShoulderAngle.toInt()}°")
            
            return PushUpState(
                currentElbowAngle = avgElbowAngle,
                currentShoulderAngle = avgShoulderAngle
            )
        }
        
        return PushUpState()
    }
    
    private fun calculateAngle(point1: com.google.mlkit.vision.common.Point3D, point2: com.google.mlkit.vision.common.Point3D, point3: com.google.mlkit.vision.common.Point3D): Float {
        val vector1 = com.google.mlkit.vision.common.Point3D(point1.x - point2.x, point1.y - point2.y, point1.z - point2.z)
        val vector2 = com.google.mlkit.vision.common.Point3D(point3.x - point2.x, point3.y - point2.y, point3.z - point2.z)
        
        val dotProduct = vector1.x * vector2.x + vector1.y * vector2.y + vector1.z * vector2.z
        val magnitude1 = sqrt(vector1.x * vector1.x + vector1.y * vector1.y + vector1.z * vector1.z)
        val magnitude2 = sqrt(vector2.x * vector2.x + vector2.y * vector2.y + vector2.z * vector2.z)
        
        if (magnitude1 == 0.0 || magnitude2 == 0.0) return 0f
        
        val cosAngle = dotProduct / (magnitude1 * magnitude2)
        return acos(max(-1f, min(1f, cosAngle))) * (180f / kotlin.math.PI.toFloat())
    }
}
