import React, { useEffect, useRef } from 'react';
import { StyleSheet, View, TouchableOpacity, Dimensions, Animated, StatusBar } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { ThemedText } from '@/components/ThemedText';
import { ThemedView } from '@/components/ThemedView';
import { Colors } from '@/constants/Colors';
import { useColorScheme } from '@/hooks/useColorScheme';
import { useRouter, useLocalSearchParams } from 'expo-router';
import * as Haptics from 'expo-haptics';

const { width, height } = Dimensions.get('window');

export default function SuccessScreen() {
  const colorScheme = useColorScheme();
  const colors = Colors[colorScheme ?? 'light'];
  const router = useRouter();
  const params = useLocalSearchParams();
  
  // Get parameters from navigation
  const appName = params.appName as string || 'App';
  const appIcon = params.appIcon as string || '📱';
  const pushupCount = parseInt(params.pushupCount as string) || 0;
  
  // Animation values
  const scaleAnim = useRef(new Animated.Value(0)).current;
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const confettiAnim = useRef(new Animated.Value(0)).current;
  const bounceAnim = useRef(new Animated.Value(0)).current;

  const celebrationMessages = [
    "You Did It!",
    "Amazing Work!",
    "Incredible!",
    "Outstanding!",
    "Fantastic!",
    "Well Done!",
    "Excellent!",
    "Superb!",
  ];

  const subMessages = [
    "You've earned your break!",
    "Time to enjoy your app!",
    "Great job staying disciplined!",
    "Your dedication is inspiring!",
    "Keep up the great work!",
  ];

  const randomMessage = celebrationMessages[Math.floor(Math.random() * celebrationMessages.length)];
  const randomSubMessage = subMessages[Math.floor(Math.random() * subMessages.length)];

  useEffect(() => {
    // Haptic feedback
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);

    // Start animations
    startCelebrationAnimation();

    // Auto-dismiss after 5 seconds
    const timer = setTimeout(() => {
      handleReturnToDashboard();
    }, 5000);

    return () => clearTimeout(timer);
  }, []);

  const startCelebrationAnimation = () => {
    // Main celebration animation sequence
    Animated.parallel([
      // Scale in the main content
      Animated.spring(scaleAnim, {
        toValue: 1,
        tension: 50,
        friction: 8,
        useNativeDriver: true,
      }),
      // Fade in
      Animated.timing(fadeAnim, {
        toValue: 1,
        duration: 800,
        useNativeDriver: true,
      }),
      // Confetti animation
      Animated.timing(confettiAnim, {
        toValue: 1,
        duration: 2000,
        useNativeDriver: true,
      }),
    ]).start();

    // Bounce animation for the trophy
    Animated.loop(
      Animated.sequence([
        Animated.timing(bounceAnim, {
          toValue: 1,
          duration: 1000,
          useNativeDriver: true,
        }),
        Animated.timing(bounceAnim, {
          toValue: 0,
          duration: 1000,
          useNativeDriver: true,
        }),
      ])
    ).start();
  };

  const handleOpenApp = () => {
    // In a real implementation, this would open the actual app
    // For now, we'll just show a message and return to dashboard
    router.replace('/(tabs)/');
  };

  const handleReturnToDashboard = () => {
    router.replace('/(tabs)/');
  };

  const renderConfetti = () => {
    const confettiPieces = [];
    for (let i = 0; i < 20; i++) {
      const randomX = Math.random() * width;
      const randomDelay = Math.random() * 2000;
      const randomDuration = 2000 + Math.random() * 1000;
      
      confettiPieces.push(
        <Animated.View
          key={i}
          style={[
            styles.confettiPiece,
            {
              left: randomX,
              backgroundColor: i % 4 === 0 ? colors.primaryAccent : 
                             i % 4 === 1 ? colors.secondaryAccent :
                             i % 4 === 2 ? colors.actionHighlight : colors.progressHighlight,
              transform: [
                {
                  translateY: confettiAnim.interpolate({
                    inputRange: [0, 1],
                    outputRange: [-50, height + 50],
                  }),
                },
                {
                  rotate: confettiAnim.interpolate({
                    inputRange: [0, 1],
                    outputRange: ['0deg', '360deg'],
                  }),
                },
              ],
            },
          ]}
        />
      );
    }
    return confettiPieces;
  };

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <StatusBar hidden />
      
      {/* Confetti */}
      <View style={styles.confettiContainer}>
        {renderConfetti()}
      </View>

      {/* Main Content */}
      <Animated.View
        style={[
          styles.content,
          {
            opacity: fadeAnim,
            transform: [{ scale: scaleAnim }],
          },
        ]}
      >
        {/* Trophy/Celebration Icon */}
        <Animated.View
          style={[
            styles.iconContainer,
            {
              transform: [
                {
                  translateY: bounceAnim.interpolate({
                    inputRange: [0, 1],
                    outputRange: [0, -20],
                  }),
                },
              ],
            },
          ]}
        >
          <LinearGradient
            colors={[colors.primaryAccent, colors.secondaryAccent]}
            style={styles.iconBackground}
          >
            <IconSymbol name="trophy.fill" size={80} color={colors.cardBackground} />
          </LinearGradient>
        </Animated.View>

        {/* Success Message */}
        <ThemedText type="title" style={[styles.successTitle, { color: colors.text }]}>
          {randomMessage}
        </ThemedText>

        <ThemedText type="default" style={[styles.successSubtitle, { color: colors.textSecondary }]}>
          {randomSubMessage}
        </ThemedText>

        {/* Stats */}
        <View style={[styles.statsContainer, { backgroundColor: colors.cardBackground, borderColor: colors.border }]}>
          <View style={styles.statItem}>
            <ThemedText style={styles.statIcon}>{appIcon}</ThemedText>
            <ThemedText type="defaultSemiBold" style={[styles.statLabel, { color: colors.text }]}>
              {appName}
            </ThemedText>
            <ThemedText type="default" style={[styles.statValue, { color: colors.textSecondary }]}>
              Unlocked for 1 hour
            </ThemedText>
          </View>
          
          <View style={styles.statDivider} />
          
          <View style={styles.statItem}>
            <ThemedText style={styles.statIcon}>💪</ThemedText>
            <ThemedText type="defaultSemiBold" style={[styles.statLabel, { color: colors.text }]}>
              Push-ups
            </ThemedText>
            <ThemedText type="default" style={[styles.statValue, { color: colors.primaryAccent }]}>
              {pushupCount} completed
            </ThemedText>
          </View>
        </View>

        {/* Action Buttons */}
        <View style={styles.buttonContainer}>
          <TouchableOpacity
            style={[styles.primaryButton, { backgroundColor: colors.primaryAccent }]}
            onPress={handleOpenApp}
          >
            <IconSymbol name="arrow.right.circle.fill" size={20} color={colors.cardBackground} style={styles.buttonIcon} />
            <ThemedText style={[styles.primaryButtonText, { color: colors.cardBackground }]}>
              Open {appName}
            </ThemedText>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.secondaryButton, { borderColor: colors.border }]}
            onPress={handleReturnToDashboard}
          >
            <ThemedText style={[styles.secondaryButtonText, { color: colors.textSecondary }]}>
              Return to Dashboard
            </ThemedText>
          </TouchableOpacity>
        </View>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  confettiContainer: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    zIndex: 1,
  },
  confettiPiece: {
    position: 'absolute',
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  content: {
    alignItems: 'center',
    paddingHorizontal: 40,
    zIndex: 2,
  },
  iconContainer: {
    marginBottom: 30,
  },
  iconBackground: {
    width: 120,
    height: 120,
    borderRadius: 60,
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 4,
    },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 8,
  },
  successTitle: {
    fontSize: 36,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 12,
  },
  successSubtitle: {
    fontSize: 18,
    textAlign: 'center',
    marginBottom: 40,
    lineHeight: 24,
  },
  statsContainer: {
    flexDirection: 'row',
    borderRadius: 16,
    borderWidth: 1,
    padding: 20,
    marginBottom: 40,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  statItem: {
    flex: 1,
    alignItems: 'center',
  },
  statIcon: {
    fontSize: 32,
    marginBottom: 8,
  },
  statLabel: {
    fontSize: 16,
    marginBottom: 4,
  },
  statValue: {
    fontSize: 14,
    textAlign: 'center',
  },
  statDivider: {
    width: 1,
    backgroundColor: '#E0E0E0',
    marginHorizontal: 20,
  },
  buttonContainer: {
    width: '100%',
    gap: 12,
  },
  primaryButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 16,
    paddingHorizontal: 24,
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.2,
    shadowRadius: 4,
    elevation: 4,
  },
  buttonIcon: {
    marginRight: 8,
  },
  primaryButtonText: {
    fontSize: 18,
    fontWeight: '600',
  },
  secondaryButton: {
    paddingVertical: 16,
    paddingHorizontal: 24,
    borderRadius: 12,
    borderWidth: 1,
    alignItems: 'center',
  },
  secondaryButtonText: {
    fontSize: 16,
    fontWeight: '500',
  },
});