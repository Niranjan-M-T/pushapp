import { AppState, AppStateStatus } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import AppUsageService from './AppUsageService';

export interface RoutineSettings {
  enabled: boolean;
  time: Date;
  pushupRequirement: number;
  selectedApps: string[];
}

export interface BackgroundServiceConfig {
  checkInterval: number; // milliseconds
  enableRoutineMonitoring: boolean;
  enableUsageMonitoring: boolean;
}

class BackgroundService {
  private isRunning = false;
  private checkInterval: NodeJS.Timeout | null = null;
  private appStateSubscription: any = null;
  private listeners: { [event: string]: Function[] } = {};
  
  private config: BackgroundServiceConfig = {
    checkInterval: 30000, // 30 seconds
    enableRoutineMonitoring: true,
    enableUsageMonitoring: true,
  };

  async initialize(config?: Partial<BackgroundServiceConfig>): Promise<void> {
    if (config) {
      this.config = { ...this.config, ...config };
    }

    try {
      // Initialize app usage service
      await AppUsageService.initialize();

      // Listen for app state changes
      this.appStateSubscription = AppState.addEventListener('change', this.handleAppStateChange.bind(this));

      // Listen for app usage events
      AppUsageService.on('blockedAppLaunched', this.handleBlockedAppLaunched.bind(this));
      AppUsageService.on('usageLimitExceeded', this.handleUsageLimitExceeded.bind(this));

      console.log('Background service initialized');
    } catch (error) {
      console.error('Failed to initialize background service:', error);
      throw error;
    }
  }

  async start(): Promise<void> {
    if (this.isRunning) return;

    try {
      this.isRunning = true;

      // Start periodic checks
      this.checkInterval = setInterval(() => {
        this.performPeriodicChecks();
      }, this.config.checkInterval);

      // Start app usage monitoring
      if (this.config.enableUsageMonitoring) {
        await AppUsageService.startMonitoring();
      }

      this.emit('serviceStarted', { config: this.config });
      console.log('Background service started');
    } catch (error) {
      console.error('Failed to start background service:', error);
      this.isRunning = false;
      throw error;
    }
  }

  async stop(): Promise<void> {
    if (!this.isRunning) return;

    try {
      this.isRunning = false;

      // Stop periodic checks
      if (this.checkInterval) {
        clearInterval(this.checkInterval);
        this.checkInterval = null;
      }

      // Stop app usage monitoring
      await AppUsageService.stopMonitoring();

      this.emit('serviceStopped', {});
      console.log('Background service stopped');
    } catch (error) {
      console.error('Failed to stop background service:', error);
    }
  }

  private async performPeriodicChecks(): Promise<void> {
    try {
      // Check routine restrictions
      if (this.config.enableRoutineMonitoring) {
        await this.checkRoutineRestrictions();
      }

      // Check app usage limits
      if (this.config.enableUsageMonitoring) {
        await this.checkUsageLimits();
      }

      // Clean up expired unlocks
      await this.cleanupExpiredUnlocks();

      // Update daily usage stats
      await this.updateDailyStats();

    } catch (error) {
      console.error('Error during periodic checks:', error);
    }
  }

  private async checkRoutineRestrictions(): Promise<void> {
    try {
      const now = new Date();
      const currentTime = now.getHours() * 60 + now.getMinutes(); // minutes since midnight

      // Load routine settings
      const morningRoutine = await this.loadRoutineSettings('morning');
      const midnightRoutine = await this.loadRoutineSettings('midnight');

      // Check morning routine
      if (morningRoutine?.enabled) {
        const routineTime = morningRoutine.time.getHours() * 60 + morningRoutine.time.getMinutes();
        const routineWindow = 4 * 60; // 4 hours window
        
        if (currentTime >= routineTime && currentTime <= routineTime + routineWindow) {
          await this.enforceRoutineRestrictions('morning', morningRoutine);
        }
      }

      // Check midnight routine
      if (midnightRoutine?.enabled) {
        const routineTime = midnightRoutine.time.getHours() * 60 + midnightRoutine.time.getMinutes();
        const routineWindow = 8 * 60; // 8 hours window (until morning)
        
        // Handle midnight crossing
        const isInMidnightWindow = currentTime >= routineTime || currentTime <= (routineTime + routineWindow) % (24 * 60);
        
        if (isInMidnightWindow) {
          await this.enforceRoutineRestrictions('midnight', midnightRoutine);
        }
      }
    } catch (error) {
      console.error('Error checking routine restrictions:', error);
    }
  }

  private async enforceRoutineRestrictions(routineType: string, routine: RoutineSettings): Promise<void> {
    try {
      const settings = await AppUsageService.loadAppSettings();
      
      for (const packageName of routine.selectedApps) {
        const appSettings = settings.find(s => s.packageName === packageName);
        if (appSettings) {
          const isBlocked = await AppUsageService.isAppBlocked(packageName);
          if (!isBlocked) {
            await AppUsageService.blockApp(packageName);
            this.emit('routineRestrictionApplied', { 
              routineType, 
              packageName, 
              pushupRequirement: routine.pushupRequirement 
            });
          }
        }
      }
    } catch (error) {
      console.error('Error enforcing routine restrictions:', error);
    }
  }

  private async checkUsageLimits(): Promise<void> {
    try {
      const settings = await AppUsageService.loadAppSettings();
      const trackedApps = settings.filter(s => s.isTracked);

      for (const appSettings of trackedApps) {
        const stats = await AppUsageService.getAppUsageStats(appSettings.packageName);
        if (stats) {
          const usageMinutes = stats.totalTimeInForeground / (1000 * 60);
          
          if (usageMinutes >= appSettings.timeLimit) {
            const isBlocked = await AppUsageService.isAppBlocked(appSettings.packageName);
            if (!isBlocked) {
              await AppUsageService.blockApp(appSettings.packageName);
              this.emit('usageLimitReached', {
                packageName: appSettings.packageName,
                usageMinutes,
                limit: appSettings.timeLimit,
                pushupRequirement: appSettings.pushupRequirement,
              });
            }
          }
        }
      }
    } catch (error) {
      console.error('Error checking usage limits:', error);
    }
  }

  private async cleanupExpiredUnlocks(): Promise<void> {
    try {
      const settings = await AppUsageService.loadAppSettings();
      const now = Date.now();

      for (const appSettings of settings) {
        if (!appSettings.isBlocked && appSettings.unlockTime) {
          const unlockExpiry = appSettings.unlockTime + (appSettings.unlockDuration * 60 * 1000);
          
          if (now >= unlockExpiry) {
            await AppUsageService.blockApp(appSettings.packageName);
            this.emit('unlockExpired', { packageName: appSettings.packageName });
          }
        }
      }
    } catch (error) {
      console.error('Error cleaning up expired unlocks:', error);
    }
  }

  private async updateDailyStats(): Promise<void> {
    try {
      const usage = await AppUsageService.getTodayUsage();
      const settings = await AppUsageService.loadAppSettings();

      // Update app usage times
      for (const appSettings of settings) {
        if (appSettings.isTracked) {
          const stats = await AppUsageService.getAppUsageStats(appSettings.packageName);
          if (stats) {
            const usageMinutes = Math.round(stats.totalTimeInForeground / (1000 * 60));
            usage.appUsage[appSettings.packageName] = usageMinutes;
          }
        }
      }

      await AppUsageService.saveDailyUsage(usage);
    } catch (error) {
      console.error('Error updating daily stats:', error);
    }
  }

  private handleAppStateChange(nextAppState: AppStateStatus): void {
    if (nextAppState === 'background' || nextAppState === 'inactive') {
      // App is going to background, ensure monitoring continues
      this.emit('appBackgrounded', { state: nextAppState });
    } else if (nextAppState === 'active') {
      // App is coming to foreground
      this.emit('appForegrounded', { state: nextAppState });
      
      // Perform immediate checks when app becomes active
      this.performPeriodicChecks();
    }
  }

  private async handleBlockedAppLaunched(event: { packageName: string }): Promise<void> {
    const { packageName } = event;
    
    try {
      const settings = await AppUsageService.loadAppSettings();
      const appSettings = settings.find(s => s.packageName === packageName);
      
      if (appSettings) {
        // Determine the pushup requirement
        let pushupRequirement = appSettings.pushupRequirement;
        let unlockReason = 'usage_limit';

        // Check if this is due to a routine restriction
        const routineRestriction = await this.checkRoutineRestriction(packageName);
        if (routineRestriction) {
          pushupRequirement = routineRestriction.pushupRequirement;
          unlockReason = `${routineRestriction.type}_routine`;
        }

        this.emit('pushupChallengeRequired', {
          packageName,
          appName: appSettings.appName,
          appIcon: appSettings.icon,
          pushupRequirement,
          unlockReason,
          enforcement: appSettings.enforcement,
        });
      }
    } catch (error) {
      console.error('Error handling blocked app launch:', error);
    }
  }

  private async handleUsageLimitExceeded(event: { packageName: string, usageMinutes: number, limit: number }): Promise<void> {
    this.emit('usageLimitExceeded', event);
  }

  private async checkRoutineRestriction(packageName: string): Promise<{ type: string, pushupRequirement: number } | null> {
    try {
      const now = new Date();
      const currentTime = now.getHours() * 60 + now.getMinutes();

      // Check morning routine
      const morningRoutine = await this.loadRoutineSettings('morning');
      if (morningRoutine?.enabled && morningRoutine.selectedApps.includes(packageName)) {
        const routineTime = morningRoutine.time.getHours() * 60 + morningRoutine.time.getMinutes();
        const routineWindow = 4 * 60;
        
        if (currentTime >= routineTime && currentTime <= routineTime + routineWindow) {
          return { type: 'morning', pushupRequirement: morningRoutine.pushupRequirement };
        }
      }

      // Check midnight routine
      const midnightRoutine = await this.loadRoutineSettings('midnight');
      if (midnightRoutine?.enabled && midnightRoutine.selectedApps.includes(packageName)) {
        const routineTime = midnightRoutine.time.getHours() * 60 + midnightRoutine.time.getMinutes();
        const routineWindow = 8 * 60;
        
        const isInMidnightWindow = currentTime >= routineTime || currentTime <= (routineTime + routineWindow) % (24 * 60);
        
        if (isInMidnightWindow) {
          return { type: 'midnight', pushupRequirement: midnightRoutine.pushupRequirement };
        }
      }

      return null;
    } catch (error) {
      console.error('Error checking routine restriction:', error);
      return null;
    }
  }

  private async loadRoutineSettings(type: 'morning' | 'midnight'): Promise<RoutineSettings | null> {
    try {
      const key = `routine_${type}`;
      const data = await AsyncStorage.getItem(key);
      return data ? JSON.parse(data) : null;
    } catch (error) {
      console.error(`Error loading ${type} routine settings:`, error);
      return null;
    }
  }

  async saveRoutineSettings(type: 'morning' | 'midnight', settings: RoutineSettings): Promise<void> {
    try {
      const key = `routine_${type}`;
      await AsyncStorage.setItem(key, JSON.stringify(settings));
    } catch (error) {
      console.error(`Error saving ${type} routine settings:`, error);
    }
  }

  // Public methods for handling pushup completion
  async handlePushupChallengeCompleted(packageName: string, pushupCount: number): Promise<void> {
    try {
      // Add pushups to daily total
      await AppUsageService.addPushups(pushupCount, packageName);

      // Unblock the app for 1 hour
      await AppUsageService.unblockApp(packageName, 60);

      this.emit('pushupChallengeCompleted', {
        packageName,
        pushupCount,
        unlockDuration: 60,
      });
    } catch (error) {
      console.error('Error handling pushup challenge completion:', error);
    }
  }

  // Event system
  on(event: string, callback: Function): void {
    if (!this.listeners[event]) {
      this.listeners[event] = [];
    }
    this.listeners[event].push(callback);
  }

  off(event: string, callback: Function): void {
    if (this.listeners[event]) {
      this.listeners[event] = this.listeners[event].filter(cb => cb !== callback);
    }
  }

  private emit(event: string, data: any): void {
    if (this.listeners[event]) {
      this.listeners[event].forEach(callback => callback(data));
    }
  }

  // Getters
  get isActive(): boolean {
    return this.isRunning;
  }

  get configuration(): BackgroundServiceConfig {
    return { ...this.config };
  }

  // Configuration
  updateConfig(config: Partial<BackgroundServiceConfig>): void {
    this.config = { ...this.config, ...config };
    this.emit('configUpdated', { config: this.config });
  }

  async cleanup(): Promise<void> {
    try {
      await this.stop();
      
      if (this.appStateSubscription) {
        this.appStateSubscription.remove();
        this.appStateSubscription = null;
      }

      // Remove all listeners
      AppUsageService.off('blockedAppLaunched', this.handleBlockedAppLaunched);
      AppUsageService.off('usageLimitExceeded', this.handleUsageLimitExceeded);

      console.log('Background service cleaned up');
    } catch (error) {
      console.error('Error during cleanup:', error);
    }
  }
}

export default new BackgroundService();