import React, { useState, useEffect } from 'react';
import { StyleSheet, ScrollView, View, TouchableOpacity, Dimensions } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { ThemedText } from '@/components/ThemedText';
import { ThemedView } from '@/components/ThemedView';
import { Colors } from '@/constants/Colors';
import { useColorScheme } from '@/hooks/useColorScheme';
import { useRouter } from 'expo-router';
import AppUsageService from '@/services/AppUsageService';
import BackgroundService from '@/services/BackgroundService';

const { width } = Dimensions.get('window');

// Mock data - will be replaced with real data later
const mockApps = [
  { name: 'Instagram', icon: '📷', usage: '2h 15m' },
  { name: 'TikTok', icon: '🎵', usage: '1h 45m' },
  { name: 'Twitter', icon: '🐦', usage: '45m' },
  { name: 'YouTube', icon: '📺', usage: '30m' },
];

const motivationalQuotes = [
  "Discipline is the bridge between goals and accomplishment.",
  "The pain you feel today will be the strength you feel tomorrow.",
  "Success is the sum of small efforts repeated day in and day out.",
  "Your body can stand almost anything. It's your mind you have to convince.",
  "The only bad workout is the one that didn't happen.",
];

export default function DashboardScreen() {
  const colorScheme = useColorScheme();
  const colors = Colors[colorScheme ?? 'light'];
  const router = useRouter();
  const [pushupCount, setPushupCount] = useState(0);
  const [totalUsage, setTotalUsage] = useState('0h 0m');
  const [dailyQuote, setDailyQuote] = useState(motivationalQuotes[0]);
  const [topApps, setTopApps] = useState(mockTopApps);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    initializeServices();
    loadDashboardData();
    
    // Set a random quote each day
    const today = new Date().getDate();
    setDailyQuote(motivationalQuotes[today % motivationalQuotes.length]);
  }, []);

  const initializeServices = async () => {
    try {
      // Initialize background service
      await BackgroundService.initialize();
      await BackgroundService.start();
      
      // Listen for pushup updates
      AppUsageService.on('pushupsAdded', (event: { total: number }) => {
        setPushupCount(event.total);
      });
      
      console.log('Services initialized successfully');
    } catch (error) {
      console.error('Failed to initialize services:', error);
    }
  };

  const loadDashboardData = async () => {
    try {
      setIsLoading(true);
      
      // Load today's usage data
      const todayUsage = await AppUsageService.getTodayUsage();
      setPushupCount(todayUsage.totalPushups);
      
      // Calculate total usage time
      const totalMinutes = Object.values(todayUsage.appUsage).reduce((sum, minutes) => sum + minutes, 0);
      const hours = Math.floor(totalMinutes / 60);
      const minutes = totalMinutes % 60;
      setTotalUsage(`${hours}h ${minutes}m`);
      
      // Load top apps with real usage data
      const installedApps = await AppUsageService.getInstalledApps();
      const appsWithUsage = await Promise.all(
        installedApps.slice(0, 5).map(async (app) => {
          const stats = await AppUsageService.getAppUsageStats(app.packageName);
          const usageMinutes = stats ? Math.round(stats.totalTimeInForeground / (1000 * 60)) : 0;
          const hours = Math.floor(usageMinutes / 60);
          const mins = usageMinutes % 60;
          
          return {
            name: app.appName,
            icon: app.icon,
            usage: hours > 0 ? `${hours}h ${mins}m` : `${mins}m`,
          };
        })
      );
      
      setTopApps(appsWithUsage);
      
    } catch (error) {
      console.error('Failed to load dashboard data:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const progressPercentage = Math.min((pushupCount / 50) * 100, 100); // Assuming daily goal of 50

  const handleStartPushups = () => {
    router.push({
      pathname: '/pushup',
      params: {
        goal: 10,
        appName: 'Instagram',
        appIcon: '📷',
      },
    });
  };

  return (
    <ThemedView style={styles.container}>
      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        {/* Header Section */}
        <ThemedView style={[styles.header, { backgroundColor: colors.background }]}>
          <View style={styles.headerTop}>
            <ThemedText type="title" style={[styles.headerTitle, { color: colors.text }]}>
              Today's Progress
            </ThemedText>
            <TouchableOpacity style={styles.settingsButton}>
              <IconSymbol name="gear" size={24} color={colors.icon} />
            </TouchableOpacity>
          </View>

          {/* Circular Progress Indicator */}
          <View style={styles.progressContainer}>
            <View style={[styles.progressCircle, { borderColor: colors.border }]}>
              <LinearGradient
                colors={[colors.primaryAccent, colors.secondaryAccent]}
                style={[
                  styles.progressFill,
                  {
                    transform: [{ rotate: `${(progressPercentage / 100) * 360}deg` }],
                  },
                ]}
                start={{ x: 0, y: 0 }}
                end={{ x: 1, y: 1 }}
              />
              <View style={[styles.progressInner, { backgroundColor: colors.background }]}>
                <ThemedText type="title" style={[styles.pushupNumber, { color: colors.text }]}>
                  {pushupCount}
                </ThemedText>
                <ThemedText type="default" style={[styles.pushupLabel, { color: colors.textSecondary }]}>
                  push-ups completed
                </ThemedText>
              </View>
            </View>
          </View>
        </ThemedView>

        {/* Stats Section */}
        <ThemedView style={styles.statsSection}>
          {/* Today's Usage Card */}
          <ThemedView style={[styles.card, { backgroundColor: colors.cardBackground, borderColor: colors.border }]}>
            <ThemedText type="subtitle" style={[styles.cardTitle, { color: colors.text }]}>
              Today's Usage
            </ThemedText>
            <ThemedText type="title" style={[styles.usageTime, { color: colors.primaryAccent }]}>
              {totalUsage}
            </ThemedText>
            
            {/* Top Apps List */}
            <ThemedText type="defaultSemiBold" style={[styles.topAppsTitle, { color: colors.text }]}>
              Top Apps
            </ThemedText>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.topAppsList}>
              {topApps.map((app, index) => (
                <View key={index} style={[styles.appItem, { backgroundColor: colors.surface, borderColor: colors.border }]}>
                  <ThemedText style={styles.appIcon}>{app.icon}</ThemedText>
                  <ThemedText type="defaultSemiBold" style={[styles.appName, { color: colors.text }]} numberOfLines={1}>
                    {app.name}
                  </ThemedText>
                  <ThemedText type="default" style={[styles.appUsage, { color: colors.textSecondary }]}>
                    {app.usage}
                  </ThemedText>
                </View>
              ))}
            </ScrollView>
          </ThemedView>

          {/* Motivation Section */}
          <ThemedView style={[styles.card, styles.motivationCard, { backgroundColor: colors.cardBackground, borderColor: colors.border }]}>
            <IconSymbol name="quote.bubble" size={24} color={colors.primaryAccent} style={styles.quoteIcon} />
            <ThemedText type="default" style={[styles.motivationText, { color: colors.text }]}>
              {dailyQuote}
            </ThemedText>
          </ThemedView>
        </ThemedView>
      </ScrollView>

      {/* Floating Action Button */}
      <TouchableOpacity
        style={[styles.fab, { backgroundColor: colors.actionHighlight }]}
        onPress={handleStartPushups}
      >
        <IconSymbol name="figure.strengthtraining.traditional" size={24} color={colors.cardBackground} />
      </TouchableOpacity>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollView: {
    flex: 1,
  },
  header: {
    paddingTop: 60,
    paddingHorizontal: 20,
    paddingBottom: 30,
  },
  headerTop: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 30,
  },
  headerTitle: {
    fontSize: 28,
    fontWeight: 'bold',
  },
  settingsButton: {
    padding: 8,
  },
  progressContainer: {
    alignItems: 'center',
    marginVertical: 20,
  },
  progressCircle: {
    width: 200,
    height: 200,
    borderRadius: 100,
    borderWidth: 8,
    position: 'relative',
    justifyContent: 'center',
    alignItems: 'center',
  },
  progressFill: {
    position: 'absolute',
    width: 184,
    height: 184,
    borderRadius: 92,
    top: 0,
    left: 0,
  },
  progressInner: {
    width: 160,
    height: 160,
    borderRadius: 80,
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 1,
  },
  pushupNumber: {
    fontSize: 48,
    fontWeight: 'bold',
  },
  pushupLabel: {
    fontSize: 14,
    textAlign: 'center',
    marginTop: 4,
  },
  statsSection: {
    padding: 20,
    gap: 20,
  },
  card: {
    borderRadius: 16,
    padding: 20,
    borderWidth: 1,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 12,
  },
  usageTime: {
    fontSize: 32,
    fontWeight: 'bold',
    marginBottom: 20,
  },
  topAppsTitle: {
    fontSize: 16,
    marginBottom: 12,
  },
  topAppsList: {
    flexDirection: 'row',
  },
  appItem: {
    width: 80,
    padding: 12,
    borderRadius: 12,
    alignItems: 'center',
    marginRight: 12,
    borderWidth: 1,
  },
  appIcon: {
    fontSize: 24,
    marginBottom: 8,
  },
  appName: {
    fontSize: 12,
    textAlign: 'center',
    marginBottom: 4,
  },
  appUsage: {
    fontSize: 10,
    textAlign: 'center',
  },
  motivationCard: {
    alignItems: 'center',
    paddingVertical: 24,
  },
  quoteIcon: {
    marginBottom: 12,
  },
  motivationText: {
    fontSize: 16,
    textAlign: 'center',
    lineHeight: 24,
    fontStyle: 'italic',
  },
  fab: {
    position: 'absolute',
    bottom: 100, // Above the tab bar
    right: 20,
    width: 56,
    height: 56,
    borderRadius: 28,
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
});
