package com.example.pushapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.roundToInt
import kotlin.math.max

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val paint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    
    private val fillPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
        isAntiAlias = true
        alpha = 100
    }
    
    private var pose: Pose? = null
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0
    private var currentElbowAngle: Float = 0f
    private var currentShoulderAngle: Float = 0f
    private var currentThresholds: Map<String, Float> = mapOf()
    private var pushUpCount: Int = 0
    private var showPushUpAnimation: Boolean = false
    private var animationStartTime: Long = 0
    
    fun updatePose(newPose: Pose?, newImageWidth: Int, newImageHeight: Int) {
        pose = newPose
        imageWidth = newImageWidth
        imageHeight = newImageHeight
        invalidate()
    }
    
    fun updateAngles(elbowAngle: Float, shoulderAngle: Float) {
        currentElbowAngle = elbowAngle
        currentShoulderAngle = shoulderAngle
        invalidate()
    }
    
    fun updateThresholds(thresholds: Map<String, Float>) {
        currentThresholds = thresholds
        invalidate()
    }
    
    fun onPushUpCompleted(newCount: Int) {
        pushUpCount = newCount
        showPushUpAnimation = true
        animationStartTime = System.currentTimeMillis()
        invalidate()
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        pose?.let { currentPose ->
            val landmarks = currentPose.allPoseLandmarks
            
            // Draw body part markers
            landmarks.forEach { landmark ->
                if (landmark.inFrameLikelihood > 0.5f) {
                    val position = mapImageToViewCoordinates(landmark.position)
                    drawBodyPartMarker(canvas, position, landmark.landmarkType)
                }
            }
            
            // Draw angle information
            drawAngleInfo(canvas)
            
            // Draw connection lines between body parts
            drawBodyConnections(canvas, landmarks)
        }
        
        // Draw push-up completion animation if active
        if (showPushUpAnimation) {
            drawPushUpAnimation(canvas)
        }
    }
    
    private fun mapImageToViewCoordinates(imagePoint: PointF): PointF {
        val scaleX = viewWidth.toFloat() / imageWidth.toFloat()
        val scaleY = viewHeight.toFloat() / imageHeight.toFloat()
        
        return PointF(
            imagePoint.x * scaleX,
            imagePoint.y * scaleY
        )
    }
    
    private fun drawBodyPartMarker(canvas: Canvas, position: PointF, landmarkType: Int) {
        val radius = 25f
        
        when (landmarkType) {
            PoseLandmark.NOSE -> {
                // Face marker - larger circle
                canvas.drawCircle(position.x, position.y, radius * 1.8f, paint)
                canvas.drawCircle(position.x, position.y, radius * 1.8f, fillPaint)
            }
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER -> {
                // Shoulder markers
                canvas.drawCircle(position.x, position.y, radius, paint)
                canvas.drawCircle(position.x, position.y, radius, fillPaint)
            }
            PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW -> {
                // Elbow markers
                canvas.drawCircle(position.x, position.y, radius, paint)
                canvas.drawCircle(position.x, position.y, radius, fillPaint)
            }
            PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST -> {
                // Wrist markers
                canvas.drawCircle(position.x, position.y, radius, paint)
                canvas.drawCircle(position.x, position.y, radius, fillPaint)
            }
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP -> {
                // Hip markers
                canvas.drawCircle(position.x, position.y, radius, paint)
                canvas.drawCircle(position.x, position.y, radius, fillPaint)
            }
            else -> {
                // Other body parts - smaller markers
                canvas.drawCircle(position.x, position.y, radius * 0.7f, paint)
                canvas.drawCircle(position.x, position.y, radius * 0.7f, fillPaint)
            }
        }
    }
    
    private fun drawBodyConnections(canvas: Canvas, landmarks: List<com.google.mlkit.vision.pose.PoseLandmark>) {
        val connections = listOf(
            // Torso
            Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER),
            Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP),
            Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP),
            Pair(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP),
            
            // Arms
            Pair(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW),
            Pair(PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
            Pair(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW),
            Pair(PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)
        )
        
        connections.forEach { (startType, endType) ->
            val startLandmark = landmarks.find { it.landmarkType == startType }
            val endLandmark = landmarks.find { it.landmarkType == endType }
            
            if (startLandmark != null && endLandmark != null &&
                startLandmark.inFrameLikelihood > 0.5f && endLandmark.inFrameLikelihood > 0.5f) {
                
                val startPos = mapImageToViewCoordinates(startLandmark.position)
                val endPos = mapImageToViewCoordinates(endLandmark.position)
                
                canvas.drawLine(startPos.x, startPos.y, endPos.x, endPos.y, paint)
            }
        }
    }
    
    private fun drawAngleInfo(canvas: Canvas) {
        val padding = 25f
        
        // Draw background for angle info
        val bgPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            alpha = 180
        }
        
        val angleText = "ELBOW: ${currentElbowAngle.roundToInt()}°"
        val shoulderText = "SHOULDER: ${currentShoulderAngle.roundToInt()}°"
        
        val angleBounds = Rect()
        val shoulderBounds = Rect()
        textPaint.getTextBounds(angleText, 0, angleText.length, angleBounds)
        textPaint.getTextBounds(shoulderText, 0, shoulderText.length, shoulderBounds)
        
        val maxWidth = maxOf(angleBounds.width(), shoulderBounds.width())
        val totalHeight = angleBounds.height() + shoulderBounds.height() + padding * 3
        
        // Draw background rectangle
        val bgRect = RectF(
            padding,
            padding,
            padding + maxWidth + padding * 2,
            padding + totalHeight
        )
        canvas.drawRect(bgRect, bgPaint)
        
        // Draw angle text
        canvas.drawText(
            angleText,
            padding * 2,
            padding + angleBounds.height() + padding,
            textPaint
        )
        
        // Draw shoulder text
        canvas.drawText(
            shoulderText,
            padding * 2,
            padding + angleBounds.height() + padding * 2 + shoulderBounds.height(),
            textPaint
        )
        
        // Add push-up state indicator
        val elbowDownThreshold = currentThresholds["elbowDown"] ?: 115f
        val elbowUpThreshold = currentThresholds["elbowUp"] ?: 172f
        val shoulderDownThreshold = currentThresholds["shoulderDown"] ?: 90f
        val shoulderUpThreshold = currentThresholds["shoulderUp"] ?: 17f
        
        val stateText = when {
            currentElbowAngle < elbowDownThreshold && currentShoulderAngle > shoulderDownThreshold -> "DOWN POSITION"
            currentElbowAngle > elbowUpThreshold && currentShoulderAngle < shoulderUpThreshold -> "UP POSITION"
            else -> "TRANSITIONING"
        }
        
        val statePaint = Paint().apply {
            color = when {
                currentElbowAngle < elbowDownThreshold && currentShoulderAngle > shoulderDownThreshold -> Color.RED
                currentElbowAngle > elbowUpThreshold && currentShoulderAngle < shoulderUpThreshold -> Color.GREEN
                else -> Color.YELLOW
            }
            textSize = 28f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        
        val stateBounds = Rect()
        statePaint.getTextBounds(stateText, 0, stateText.length, stateBounds)
        
        // Draw state background
        val stateBgRect = RectF(
            viewWidth - stateBounds.width() - padding * 2,
            padding,
            viewWidth - padding,
            padding + stateBounds.height() + padding
        )
        canvas.drawRect(stateBgRect, bgPaint)
        
        // Draw state text
        canvas.drawText(
            stateText,
            viewWidth - stateBounds.width() - padding,
            padding + stateBounds.height() + 5f,
            statePaint
        )
        
        // Add threshold information for debugging
        val thresholdPaint = Paint().apply {
            color = Color.WHITE
            textSize = 20f
            isAntiAlias = true
        }
        
        val thresholdText = "Thresholds: Elbow Down<${elbowDownThreshold.toInt()}° Up>${elbowUpThreshold.toInt()}° | Shoulder Down<${shoulderDownThreshold.toInt()}° Up>${shoulderUpThreshold.toInt()}°"
        canvas.drawText(
            thresholdText,
            padding,
            viewHeight - padding,
            thresholdPaint
        )
    }
    
    private fun drawPushUpAnimation(canvas: Canvas) {
        val currentTime = System.currentTimeMillis()
        val animationDuration = 2000L // 2 seconds
        val elapsed = currentTime - animationStartTime
        
        if (elapsed > animationDuration) {
            showPushUpAnimation = false
            return
        }
        
        // Calculate animation progress (0.0 to 1.0)
        val progress = (elapsed.toFloat() / animationDuration).coerceIn(0f, 1f)
        
        // Create a pulsing effect
        val alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
        val scale = 1f + (progress * 0.5f)
        
        val animationPaint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.FILL
            this.alpha = alpha
        }
        
        // Adjust text size based on orientation
        val isLandscape = viewWidth > viewHeight
        val baseTextSize = if (isLandscape) 40f else 60f
        
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = baseTextSize * scale
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            this.alpha = alpha
        }
        
        // Draw celebration text in the center
        val text = "PUSH-UP ${pushUpCount}!"
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f
        
        // Adjust circle size based on orientation
        val baseRadius = if (isLandscape) {
            maxOf(textBounds.width(), textBounds.height()) * 0.6f
        } else {
            maxOf(textBounds.width(), textBounds.height()) * 0.8f
        }
        val radius = baseRadius * scale
        
        // Draw background circle
        canvas.drawCircle(centerX, centerY, radius, animationPaint)
        
        // Draw text
        canvas.drawText(
            text,
            centerX - textBounds.width() / 2f,
            centerY + textBounds.height() / 2f,
            textPaint
        )
        
        // Schedule next frame
        postInvalidateOnAnimation()
    }
}
