package com.example.pushapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pushapp.data.PushUpSettings
import com.example.pushapp.ui.theme.*
import com.example.pushapp.CameraPreview
import com.example.pushapp.PushUpViewModel
import com.example.pushapp.utils.AppLogger
import androidx.compose.ui.unit.offset

@Composable
fun PushUpScreen(
    pushUpCount: Int,
    targetCount: Int,
    isInCorrectPosition: Boolean,
    showInstructions: Boolean,
    showEncouragement: Boolean,
    previewOpacity: Float,
    onClose: () -> Unit,
    pushUpViewModel: PushUpViewModel,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Full screen camera preview with reduced opacity
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(previewOpacity)
        ) {
            // Show camera preview with error handling
            var cameraError by remember { mutableStateOf(false) }
            var cameraErrorMessage by remember { mutableStateOf("") }
            
            if (!cameraError) {
                CameraPreview(
                    viewModel = pushUpViewModel,
                    onCameraError = { error ->
                        cameraError = true
                        cameraErrorMessage = error
                        AppLogger.e("PushUpScreen", "Camera error received: $error")
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Fallback: show a colored background with camera icon
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Camera",
                            tint = White,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Camera Preview",
                            color = White,
                            fontSize = 18.sp
                        )
                        Text(
                            text = cameraErrorMessage.ifEmpty { "Camera may not be available" },
                            color = White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
        
        // Overlay content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Top bar with close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .background(
                            color = Black.copy(alpha = 0.7f),
                            shape = CircleShape
                        )
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Push-up counter
                Card(
                    modifier = Modifier
                        .background(
                            color = PrimaryRed.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$pushUpCount",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )
                        Text(
                            text = "of $targetCount",
                            fontSize = 16.sp,
                            color = White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Instructions and encouragement at the bottom
            if (showInstructions || showEncouragement) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Black.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        if (showInstructions) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Instructions",
                                    tint = PrimaryRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Instructions",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = White
                                )
                            }
                            
                            if (!isInCorrectPosition) {
                                Text(
                                    text = "Please get into the correct UP position to start",
                                    fontSize = 16.sp,
                                    color = LightRed,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            } else {
                                Text(
                                    text = "Great! Now perform your push-ups with proper form",
                                    fontSize = 16.sp,
                                    color = White,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            
                            Text(
                                text = "• Keep your body straight from head to heels\n" +
                                       "• Lower your body until your chest nearly touches the floor\n" +
                                       "• Push back up to the starting position\n" +
                                       "• Keep your elbows close to your body",
                                fontSize = 14.sp,
                                color = White.copy(alpha = 0.8f),
                                lineHeight = 20.sp
                            )
                        }
                        
                        if (showEncouragement && showInstructions) {
                            Divider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = LightGrey
                            )
                        }
                        
                        if (showEncouragement) {
                            Text(
                                text = getEncouragementText(pushUpCount, targetCount),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = PrimaryRed,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
        
        // Blue markers overlay (positioned absolutely)
        BlueMarkersOverlay(
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun BlueMarkersOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        // Top left marker
        Box(
            modifier = Modifier
                .offset(x = 50.dp, y = 100.dp)
                .size(8.dp)
                .background(
                    color = Color(0xFF2196F3), // Blue color
                    shape = CircleShape
                )
        )
        
        // Top right marker
        Box(
            modifier = Modifier
                .offset(x = (-50).dp, y = 100.dp)
                .size(8.dp)
                .background(
                    color = Color(0xFF2196F3),
                    shape = CircleShape
                )
        )
        
        // Bottom left marker
        Box(
            modifier = Modifier
                .offset(x = 50.dp, y = (-100).dp)
                .size(8.dp)
                .background(
                    color = Color(0xFF2196F3),
                    shape = CircleShape
                )
        )
        
        // Bottom right marker
        Box(
            modifier = Modifier
                .offset(x = (-50).dp, y = (-100).dp)
                .size(8.dp)
                .background(
                    color = Color(0xFF2196F3),
                    shape = CircleShape
                )
        )
    }
}

private fun getEncouragementText(currentCount: Int, targetCount: Int): String {
    return when {
        currentCount == 0 -> "Let's get started! You've got this! 💪"
        currentCount < targetCount / 3 -> "Great start! Keep going! 🔥"
        currentCount < targetCount / 2 -> "You're doing amazing! Almost halfway there! 🚀"
        currentCount < targetCount * 2 / 3 -> "More than halfway! You're unstoppable! ⚡"
        currentCount < targetCount -> "Almost there! Push through! 💯"
        else -> "Congratulations! You've completed all push-ups! 🎉"
    }
}
