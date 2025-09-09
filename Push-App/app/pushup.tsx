import React, { useState, useEffect, useRef } from 'react';
import { StyleSheet, View, TouchableOpacity, Dimensions, Animated, StatusBar } from 'react-native';
import { Camera, CameraView } from 'expo-camera';
import { LinearGradient } from 'expo-linear-gradient';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { ThemedText } from '@/components/ThemedText';
import { ThemedView } from '@/components/ThemedView';
import { Colors } from '@/constants/Colors';
import { useColorScheme } from '@/hooks/useColorScheme';
import { useRouter, useLocalSearchParams } from 'expo-router';
import * as Haptics from 'expo-haptics';
import PushupDetectionService from '@/services/PushupDetectionService';
import BackgroundService from '@/services/BackgroundService';

const { width, height } = Dimensions.get('window');

export default function PushupScreen() {
  const colorScheme = useColorScheme();
  const colors = Colors[colorScheme ?? 'light'];
  const router = useRouter();
  const params = useLocalSearchParams();
  
  // Get parameters from navigation
  const goal = parseInt(params.goal as string) || 10;
  const appName = params.appName as string || 'App';
  const appIcon = params.appIcon as string || '📱';
  
  const [hasPermission, setHasPermission] = useState<boolean | null>(null);
  const [pushupCount, setPushupCount] = useState(0);
  const [isDetecting, setIsDetecting] = useState(false);
  const [motivationMessage, setMotivationMessage] = useState("Let's get started!");
  const [detectionStarted, setDetectionStarted] = useState(false);
  
  // Animation values
  const scaleAnim = useRef(new Animated.Value(1)).current;
  const progressAnim = useRef(new Animated.Value(0)).current;
  const pulseAnim = useRef(new Animated.Value(1)).current;

  const motivationMessages = [
    "Let's get started!",
    "You've got this!",
    "Keep going!",
    "Great form!",
    "You're doing amazing!",
    "Push through!",
    "Almost there!",
    "Final stretch!",
    "One more!",
    "Incredible work!"
  ];

  useEffect(() => {
    getCameraPermissions();
    setupPushupDetection();
    
    return () => {
      // Cleanup on unmount
      PushupDetectionService.stopDetection();
    };
  }, []);

  const setupPushupDetection = () => {
    // Listen for pushup detection events
    PushupDetectionService.on('pushupDetected', handlePushupDetected);
    PushupDetectionService.on('detectionStarted', () => {
      setDetectionStarted(true);
      setMotivationMessage("Get into position and start your push-ups!");
    });
    PushupDetectionService.on('detectionStopped', () => {
      setDetectionStarted(false);
    });
  };

  const handlePushupDetected = (event: { count: number; confidence: number }) => {
    setPushupCount(event.count);
    setIsDetecting(true);
    
    // Visual feedback
    Animated.sequence([
      Animated.timing(scaleAnim, {
        toValue: 1.2,
        duration: 100,
        useNativeDriver: true,
      }),
      Animated.timing(scaleAnim, {
        toValue: 1,
        duration: 100,
        useNativeDriver: true,
      }),
    ]).start();

    setTimeout(() => setIsDetecting(false), 500);
  };

  useEffect(() => {
    // Update progress animation
    Animated.timing(progressAnim, {
      toValue: pushupCount / goal,
      duration: 300,
      useNativeDriver: false,
    }).start();

    // Update motivation message based on progress
    const progressPercentage = (pushupCount / goal) * 100;
    let messageIndex = Math.floor(progressPercentage / 10);
    messageIndex = Math.min(messageIndex, motivationMessages.length - 1);
    setMotivationMessage(motivationMessages[messageIndex]);

    // Check if goal is reached
    if (pushupCount >= goal) {
      handleGoalReached();
    }
  }, [pushupCount, goal]);

  useEffect(() => {
    // Pulse animation for the counter
    const pulse = Animated.loop(
      Animated.sequence([
        Animated.timing(pulseAnim, {
          toValue: 1.1,
          duration: 1000,
          useNativeDriver: true,
        }),
        Animated.timing(pulseAnim, {
          toValue: 1,
          duration: 1000,
          useNativeDriver: true,
        }),
      ])
    );
    pulse.start();

    return () => pulse.stop();
  }, []);

  const getCameraPermissions = async () => {
    const { status } = await Camera.requestCameraPermissionsAsync();
    setHasPermission(status === 'granted');
  };

  const startDetection = async () => {
    if (detectionStarted) return;
    
    try {
      await PushupDetectionService.startDetection({
        sensitivity: 0.7,
        minPushupDuration: 800,
        cooldownPeriod: 1000,
      });
    } catch (error) {
      console.error('Failed to start pushup detection:', error);
      // Fallback to manual mode
      setMotivationMessage("Automatic detection failed. Use manual mode.");
    }
  };

  const simulatePushupDetection = () => {
    if (pushupCount >= goal) return;
    
    // Use the detection service's manual registration
    PushupDetectionService.registerManualPushup();
  };

  const handleGoalReached = async () => {
    // Stop detection
    await PushupDetectionService.stopDetection();
    
    // Notify background service of completion
    const packageName = `com.${appName.toLowerCase()}.android`; // Mock package name
    await BackgroundService.handlePushupChallengeCompleted(packageName, goal);
    
    // Navigate to success screen
    setTimeout(() => {
      router.replace({
        pathname: '/success',
        params: {
          appName,
          appIcon,
          pushupCount: goal,
        },
      });
    }, 1000);
  };

  const handleSkip = () => {
    router.back();
  };

  if (hasPermission === null) {
    return (
      <ThemedView style={[styles.container, { backgroundColor: colors.background }]}>
        <ThemedText>Requesting camera permission...</ThemedText>
      </ThemedView>
    );
  }

  if (hasPermission === false) {
    return (
      <ThemedView style={[styles.container, styles.centered, { backgroundColor: colors.background }]}>
        <IconSymbol name="camera.fill" size={64} color={colors.textSecondary} style={styles.cameraIcon} />
        <ThemedText type="title" style={[styles.permissionTitle, { color: colors.text }]}>
          Camera Access Required
        </ThemedText>
        <ThemedText type="default" style={[styles.permissionText, { color: colors.textSecondary }]}>
          We need camera access to count your push-ups accurately.
        </ThemedText>
        <TouchableOpacity
          style={[styles.permissionButton, { backgroundColor: colors.primaryAccent }]}
          onPress={getCameraPermissions}
        >
          <ThemedText style={[styles.permissionButtonText, { color: colors.cardBackground }]}>
            Grant Permission
          </ThemedText>
        </TouchableOpacity>
      </ThemedView>
    );
  }

  const progressPercentage = (pushupCount / goal) * 100;

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <StatusBar hidden />
      
      {/* Header */}
      <View style={styles.header}>
        <View style={styles.goalContainer}>
          <ThemedText style={styles.appIcon}>{appIcon}</ThemedText>
          <ThemedText type="default" style={[styles.goalText, { color: colors.textSecondary }]}>
            Goal: {goal} push-ups for {appName}
          </ThemedText>
        </View>
        <TouchableOpacity style={styles.skipButton} onPress={handleSkip}>
          <ThemedText style={[styles.skipText, { color: colors.textSecondary }]}>Skip</ThemedText>
        </TouchableOpacity>
      </View>

      {/* Camera View */}
      <View style={styles.cameraContainer}>
        <CameraView
          style={styles.camera}
          facing="front"
        >
          {/* Counter Overlay */}
          <View style={styles.counterOverlay}>
            <Animated.View
              style={[
                styles.counterContainer,
                {
                  transform: [{ scale: scaleAnim }, { scale: pulseAnim }],
                },
              ]}
            >
              <LinearGradient
                colors={[colors.primaryAccent + '20', colors.secondaryAccent + '20']}
                style={styles.counterBackground}
              >
                <ThemedText type="title" style={[styles.counterText, { color: colors.text }]}>
                  {pushupCount}
                </ThemedText>
              </LinearGradient>
            </Animated.View>
          </View>

          {/* Detection Indicator */}
          {isDetecting && (
            <View style={styles.detectionIndicator}>
              <LinearGradient
                colors={[colors.primaryAccent, colors.secondaryAccent]}
                style={styles.detectionRing}
              />
            </View>
          )}
        </CameraView>

        {/* Control Buttons */}
        <View style={styles.controlButtons}>
          {!detectionStarted ? (
            <TouchableOpacity
              style={[styles.startButton, { backgroundColor: colors.primaryAccent }]}
              onPress={startDetection}
            >
              <ThemedText style={[styles.buttonText, { color: colors.cardBackground }]}>
                Start Detection
              </ThemedText>
            </TouchableOpacity>
          ) : (
            <TouchableOpacity
              style={[styles.simulateButton, { backgroundColor: colors.actionHighlight }]}
              onPress={simulatePushupDetection}
              disabled={pushupCount >= goal}
            >
              <ThemedText style={[styles.simulateButtonText, { color: colors.cardBackground }]}>
                Manual Count
              </ThemedText>
            </TouchableOpacity>
          )}
        </View>
      </View>

      {/* Progress Section */}
      <View style={styles.progressSection}>
        {/* Motivation Message */}
        <ThemedText type="subtitle" style={[styles.motivationText, { color: colors.text }]}>
          {motivationMessage}
        </ThemedText>

        {/* Progress Bar */}
        <View style={styles.progressContainer}>
          <View style={[styles.progressTrack, { backgroundColor: colors.border }]}>
            <Animated.View
              style={[
                styles.progressFill,
                {
                  width: progressAnim.interpolate({
                    inputRange: [0, 1],
                    outputRange: ['0%', '100%'],
                  }),
                  backgroundColor: colors.primaryAccent,
                },
              ]}
            />
          </View>
          <ThemedText type="default" style={[styles.progressText, { color: colors.textSecondary }]}>
            {pushupCount} / {goal}
          </ThemedText>
        </View>

        {/* Progress Percentage */}
        <ThemedText type="title" style={[styles.percentageText, { color: colors.primaryAccent }]}>
          {Math.round(progressPercentage)}%
        </ThemedText>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  centered: {
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: 50,
    paddingHorizontal: 20,
    paddingBottom: 10,
  },
  goalContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  appIcon: {
    fontSize: 24,
    marginRight: 8,
  },
  goalText: {
    fontSize: 16,
  },
  skipButton: {
    padding: 8,
  },
  skipText: {
    fontSize: 16,
  },
  cameraContainer: {
    flex: 1,
    position: 'relative',
  },
  camera: {
    flex: 1,
  },
  counterOverlay: {
    position: 'absolute',
    top: '50%',
    left: '50%',
    transform: [{ translateX: -75 }, { translateY: -75 }],
  },
  counterContainer: {
    width: 150,
    height: 150,
    borderRadius: 75,
    justifyContent: 'center',
    alignItems: 'center',
  },
  counterBackground: {
    width: '100%',
    height: '100%',
    borderRadius: 75,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 3,
    borderColor: 'rgba(255, 255, 255, 0.3)',
  },
  counterText: {
    fontSize: 48,
    fontWeight: 'bold',
    textShadowColor: 'rgba(0, 0, 0, 0.5)',
    textShadowOffset: { width: 1, height: 1 },
    textShadowRadius: 2,
  },
  detectionIndicator: {
    position: 'absolute',
    top: '50%',
    left: '50%',
    transform: [{ translateX: -100 }, { translateY: -100 }],
  },
  detectionRing: {
    width: 200,
    height: 200,
    borderRadius: 100,
    borderWidth: 4,
    borderColor: 'transparent',
  },
  controlButtons: {
    position: 'absolute',
    bottom: 20,
    right: 20,
    flexDirection: 'column',
    gap: 10,
  },
  startButton: {
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderRadius: 8,
  },
  simulateButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 8,
  },
  buttonText: {
    fontSize: 16,
    fontWeight: '600',
  },
  simulateButtonText: {
    fontSize: 14,
    fontWeight: '600',
  },
  progressSection: {
    padding: 20,
    alignItems: 'center',
  },
  motivationText: {
    fontSize: 20,
    fontWeight: '600',
    marginBottom: 20,
    textAlign: 'center',
  },
  progressContainer: {
    width: '100%',
    alignItems: 'center',
    marginBottom: 16,
  },
  progressTrack: {
    width: '100%',
    height: 8,
    borderRadius: 4,
    marginBottom: 8,
  },
  progressFill: {
    height: '100%',
    borderRadius: 4,
  },
  progressText: {
    fontSize: 16,
  },
  percentageText: {
    fontSize: 32,
    fontWeight: 'bold',
  },
  cameraIcon: {
    marginBottom: 20,
  },
  permissionTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 12,
    textAlign: 'center',
  },
  permissionText: {
    fontSize: 16,
    textAlign: 'center',
    marginBottom: 24,
    lineHeight: 24,
  },
  permissionButton: {
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  permissionButtonText: {
    fontSize: 16,
    fontWeight: '600',
  },
});