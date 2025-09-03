package com.example.pushapp.integration

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.pushapp.PushUpViewModel
import com.example.pushapp.data.AppLockSettings
import com.example.pushapp.ui.PushUpScreen
import com.example.pushapp.ui.theme.*

@Composable
fun PushUpIntegration(
    pushUpViewModel: PushUpViewModel,
    targetApp: AppLockSettings?,
    onPushUpComplete: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pushUpCount = pushUpViewModel.pushUpCount
    val isCounting = pushUpViewModel.isCounting
    val isInCorrectPosition = pushUpViewModel.isInCorrectPosition
    
    // Default push-up settings
    val showInstructions = true
    val showEncouragement = true
    val previewOpacity = 0.7f
    
    val targetCount = targetApp?.pushUpRequirement ?: 10
    
    // Start counting automatically when screen loads
    LaunchedEffect(Unit) {
        if (!isCounting) {
            pushUpViewModel.startCounting()
        }
    }
    
    // Monitor push-up completion
    LaunchedEffect(pushUpCount) {
        if (pushUpCount >= targetCount) {
            // Push-ups completed, unlock the app
            onPushUpComplete(pushUpCount)
        }
    }
    
    PushUpScreen(
        pushUpCount = pushUpCount,
        targetCount = targetCount,
        isInCorrectPosition = isInCorrectPosition,
        showInstructions = showInstructions,
        showEncouragement = showEncouragement,
        previewOpacity = previewOpacity,
        onClose = onClose,
        pushUpViewModel = pushUpViewModel,
        modifier = modifier
    )
}

// Extension function to check if user is in correct starting position
val PushUpViewModel.isInCorrectPosition: Boolean
    get() {
        // This would integrate with your existing pose detection logic
        // For now, returning true as placeholder
        return true
    }
