import AsyncStorage from '@react-native-async-storage/async-storage';
import { NativeModules, NativeEventEmitter, Platform } from 'react-native';

// Mock native module for development - in production this would be a real Android module
const MockAppUsageModule = {
  getInstalledApps: () => Promise.resolve([
    { packageName: 'com.instagram.android', appName: 'Instagram', icon: '📷' },
    { packageName: 'com.zhiliaoapp.musically', appName: 'TikTok', icon: '🎵' },
    { packageName: 'com.twitter.android', appName: 'Twitter', icon: '🐦' },
    { packageName: 'com.google.android.youtube', appName: 'YouTube', icon: '📺' },
    { packageName: 'com.facebook.katana', appName: 'Facebook', icon: '📘' },
    { packageName: 'com.whatsapp', appName: 'WhatsApp', icon: '💬' },
    { packageName: 'com.snapchat.android', appName: 'Snapchat', icon: '👻' },
    { packageName: 'com.reddit.frontpage', appName: 'Reddit', icon: '🤖' },
  ]),
  getAppUsageStats: (packageName: string) => Promise.resolve({
    packageName,
    totalTimeInForeground: Math.floor(Math.random() * 7200000), // Random usage in milliseconds
    lastTimeUsed: Date.now() - Math.floor(Math.random() * 86400000), // Random time within last 24h
  }),
  requestUsageStatsPermission: () => Promise.resolve(true),
  hasUsageStatsPermission: () => Promise.resolve(false),
  startAppMonitoring: () => Promise.resolve(),
  stopAppMonitoring: () => Promise.resolve(),
  blockApp: (packageName: string) => Promise.resolve(),
  unblockApp: (packageName: string) => Promise.resolve(),
  isAppBlocked: (packageName: string) => Promise.resolve(false),
};

// Use native module if available, otherwise use mock
const AppUsageModule = NativeModules.AppUsageModule || MockAppUsageModule;
const eventEmitter = new NativeEventEmitter(AppUsageModule);

export interface InstalledApp {
  packageName: string;
  appName: string;
  icon: string;
}

export interface AppUsageStats {
  packageName: string;
  totalTimeInForeground: number; // milliseconds
  lastTimeUsed: number; // timestamp
}

export interface AppSettings {
  packageName: string;
  appName: string;
  icon: string;
  isTracked: boolean;
  timeLimit: number; // minutes
  pushupRequirement: number;
  enforcement: 'disable' | 'overlay' | 'popup';
  isBlocked: boolean;
  unlockTime?: number; // timestamp when unlocked
  unlockDuration: number; // minutes (default 60)
}

export interface DailyUsage {
  date: string; // YYYY-MM-DD
  totalPushups: number;
  appUsage: { [packageName: string]: number }; // minutes
}

class AppUsageService {
  private isMonitoring = false;
  private listeners: { [event: string]: Function[] } = {};

  async initialize(): Promise<void> {
    try {
      // Check if we have usage stats permission
      const hasPermission = await AppUsageModule.hasUsageStatsPermission();
      if (!hasPermission) {
        console.log('Usage stats permission not granted');
      }

      // Load saved settings
      await this.loadAppSettings();
      
      // Start monitoring if permission is granted
      if (hasPermission) {
        await this.startMonitoring();
      }
    } catch (error) {
      console.error('Failed to initialize AppUsageService:', error);
    }
  }

  async requestPermissions(): Promise<boolean> {
    try {
      return await AppUsageModule.requestUsageStatsPermission();
    } catch (error) {
      console.error('Failed to request permissions:', error);
      return false;
    }
  }

  async getInstalledApps(): Promise<InstalledApp[]> {
    try {
      return await AppUsageModule.getInstalledApps();
    } catch (error) {
      console.error('Failed to get installed apps:', error);
      return [];
    }
  }

  async getAppUsageStats(packageName: string): Promise<AppUsageStats | null> {
    try {
      return await AppUsageModule.getAppUsageStats(packageName);
    } catch (error) {
      console.error('Failed to get app usage stats:', error);
      return null;
    }
  }

  async startMonitoring(): Promise<void> {
    if (this.isMonitoring) return;

    try {
      await AppUsageModule.startAppMonitoring();
      this.isMonitoring = true;

      // Listen for app launch events
      eventEmitter.addListener('onAppLaunched', this.handleAppLaunched.bind(this));
      eventEmitter.addListener('onAppUsageExceeded', this.handleAppUsageExceeded.bind(this));

      console.log('App monitoring started');
    } catch (error) {
      console.error('Failed to start monitoring:', error);
    }
  }

  async stopMonitoring(): Promise<void> {
    if (!this.isMonitoring) return;

    try {
      await AppUsageModule.stopAppMonitoring();
      this.isMonitoring = false;

      // Remove listeners
      eventEmitter.removeAllListeners('onAppLaunched');
      eventEmitter.removeAllListeners('onAppUsageExceeded');

      console.log('App monitoring stopped');
    } catch (error) {
      console.error('Failed to stop monitoring:', error);
    }
  }

  async blockApp(packageName: string): Promise<void> {
    try {
      await AppUsageModule.blockApp(packageName);
      await this.updateAppSettings(packageName, { isBlocked: true });
      this.emit('appBlocked', { packageName });
    } catch (error) {
      console.error('Failed to block app:', error);
    }
  }

  async unblockApp(packageName: string, duration: number = 60): Promise<void> {
    try {
      await AppUsageModule.unblockApp(packageName);
      const unlockTime = Date.now();
      await this.updateAppSettings(packageName, {
        isBlocked: false,
        unlockTime,
        unlockDuration: duration,
      });
      
      // Schedule re-blocking after duration
      setTimeout(() => {
        this.blockApp(packageName);
      }, duration * 60 * 1000);

      this.emit('appUnblocked', { packageName, duration });
    } catch (error) {
      console.error('Failed to unblock app:', error);
    }
  }

  async isAppBlocked(packageName: string): Promise<boolean> {
    try {
      return await AppUsageModule.isAppBlocked(packageName);
    } catch (error) {
      console.error('Failed to check if app is blocked:', error);
      return false;
    }
  }

  async saveAppSettings(settings: AppSettings[]): Promise<void> {
    try {
      await AsyncStorage.setItem('app_settings', JSON.stringify(settings));
    } catch (error) {
      console.error('Failed to save app settings:', error);
    }
  }

  async loadAppSettings(): Promise<AppSettings[]> {
    try {
      const data = await AsyncStorage.getItem('app_settings');
      return data ? JSON.parse(data) : [];
    } catch (error) {
      console.error('Failed to load app settings:', error);
      return [];
    }
  }

  async updateAppSettings(packageName: string, updates: Partial<AppSettings>): Promise<void> {
    try {
      const settings = await this.loadAppSettings();
      const index = settings.findIndex(s => s.packageName === packageName);
      
      if (index >= 0) {
        settings[index] = { ...settings[index], ...updates };
      } else {
        // Create new settings entry
        const installedApps = await this.getInstalledApps();
        const app = installedApps.find(a => a.packageName === packageName);
        if (app) {
          settings.push({
            packageName,
            appName: app.appName,
            icon: app.icon,
            isTracked: false,
            timeLimit: 60,
            pushupRequirement: 10,
            enforcement: 'overlay',
            isBlocked: false,
            unlockDuration: 60,
            ...updates,
          });
        }
      }
      
      await this.saveAppSettings(settings);
    } catch (error) {
      console.error('Failed to update app settings:', error);
    }
  }

  async saveDailyUsage(usage: DailyUsage): Promise<void> {
    try {
      const key = `daily_usage_${usage.date}`;
      await AsyncStorage.setItem(key, JSON.stringify(usage));
    } catch (error) {
      console.error('Failed to save daily usage:', error);
    }
  }

  async loadDailyUsage(date: string): Promise<DailyUsage | null> {
    try {
      const key = `daily_usage_${date}`;
      const data = await AsyncStorage.getItem(key);
      return data ? JSON.parse(data) : null;
    } catch (error) {
      console.error('Failed to load daily usage:', error);
      return null;
    }
  }

  async getTodayUsage(): Promise<DailyUsage> {
    const today = new Date().toISOString().split('T')[0];
    const usage = await this.loadDailyUsage(today);
    
    if (usage) {
      return usage;
    }

    // Create new daily usage record
    const newUsage: DailyUsage = {
      date: today,
      totalPushups: 0,
      appUsage: {},
    };

    await this.saveDailyUsage(newUsage);
    return newUsage;
  }

  async addPushups(count: number, packageName?: string): Promise<void> {
    try {
      const usage = await this.getTodayUsage();
      usage.totalPushups += count;
      await this.saveDailyUsage(usage);
      
      this.emit('pushupsAdded', { count, packageName, total: usage.totalPushups });
    } catch (error) {
      console.error('Failed to add pushups:', error);
    }
  }

  private async handleAppLaunched(event: { packageName: string }): Promise<void> {
    const { packageName } = event;
    
    // Check if app is blocked
    const isBlocked = await this.isAppBlocked(packageName);
    if (isBlocked) {
      this.emit('blockedAppLaunched', { packageName });
      return;
    }

    // Check if app should be blocked due to usage limits
    const settings = await this.loadAppSettings();
    const appSettings = settings.find(s => s.packageName === packageName && s.isTracked);
    
    if (appSettings) {
      const stats = await this.getAppUsageStats(packageName);
      if (stats) {
        const usageMinutes = stats.totalTimeInForeground / (1000 * 60);
        if (usageMinutes >= appSettings.timeLimit) {
          await this.blockApp(packageName);
          this.emit('blockedAppLaunched', { packageName });
          return;
        }
      }
    }

    this.emit('appLaunched', { packageName });
  }

  private async handleAppUsageExceeded(event: { packageName: string, usageMinutes: number }): Promise<void> {
    const { packageName, usageMinutes } = event;
    
    const settings = await this.loadAppSettings();
    const appSettings = settings.find(s => s.packageName === packageName && s.isTracked);
    
    if (appSettings && usageMinutes >= appSettings.timeLimit) {
      await this.blockApp(packageName);
      this.emit('usageLimitExceeded', { packageName, usageMinutes, limit: appSettings.timeLimit });
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
}

export default new AppUsageService();