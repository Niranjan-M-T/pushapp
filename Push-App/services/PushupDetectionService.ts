import { Accelerometer } from 'expo-sensors';
import * as Haptics from 'expo-haptics';

export interface PushupDetectionConfig {
  sensitivity: number; // 0.1 to 1.0
  minPushupDuration: number; // milliseconds
  cooldownPeriod: number; // milliseconds between pushups
}

export interface PushupEvent {
  count: number;
  timestamp: number;
  confidence: number; // 0 to 1
}

class PushupDetectionService {
  private isDetecting = false;
  private pushupCount = 0;
  private lastPushupTime = 0;
  private accelerometerData: { x: number; y: number; z: number; timestamp: number }[] = [];
  private listeners: { [event: string]: Function[] } = {};
  private subscription: any = null;
  
  private config: PushupDetectionConfig = {
    sensitivity: 0.7,
    minPushupDuration: 800, // 800ms minimum for a pushup
    cooldownPeriod: 1000, // 1 second between pushups
  };

  // Accelerometer-based detection parameters
  private readonly GRAVITY = 9.81;
  private readonly PUSHUP_THRESHOLD = 0.3; // G-force threshold
  private readonly WINDOW_SIZE = 20; // Number of samples to analyze
  private readonly MIN_AMPLITUDE = 0.2; // Minimum amplitude for valid pushup

  async startDetection(config?: Partial<PushupDetectionConfig>): Promise<void> {
    if (this.isDetecting) return;

    if (config) {
      this.config = { ...this.config, ...config };
    }

    try {
      // Check if accelerometer is available
      const isAvailable = await Accelerometer.isAvailableAsync();
      if (!isAvailable) {
        throw new Error('Accelerometer not available');
      }

      // Set update interval (50ms = 20Hz)
      Accelerometer.setUpdateInterval(50);

      // Start listening to accelerometer
      this.subscription = Accelerometer.addListener(this.handleAccelerometerData.bind(this));
      
      this.isDetecting = true;
      this.pushupCount = 0;
      this.accelerometerData = [];
      
      this.emit('detectionStarted', { config: this.config });
      console.log('Pushup detection started');
    } catch (error) {
      console.error('Failed to start pushup detection:', error);
      throw error;
    }
  }

  async stopDetection(): Promise<void> {
    if (!this.isDetecting) return;

    try {
      if (this.subscription) {
        this.subscription.remove();
        this.subscription = null;
      }

      this.isDetecting = false;
      this.accelerometerData = [];
      
      this.emit('detectionStopped', { totalCount: this.pushupCount });
      console.log('Pushup detection stopped');
    } catch (error) {
      console.error('Failed to stop pushup detection:', error);
    }
  }

  private handleAccelerometerData(data: { x: number; y: number; z: number }): void {
    const timestamp = Date.now();
    
    // Add new data point
    this.accelerometerData.push({
      x: data.x,
      y: data.y,
      z: data.z,
      timestamp,
    });

    // Keep only recent data (sliding window)
    if (this.accelerometerData.length > this.WINDOW_SIZE * 2) {
      this.accelerometerData = this.accelerometerData.slice(-this.WINDOW_SIZE);
    }

    // Need minimum data points to analyze
    if (this.accelerometerData.length < this.WINDOW_SIZE) return;

    // Check for pushup pattern
    this.analyzePushupPattern();
  }

  private analyzePushupPattern(): void {
    const now = Date.now();
    
    // Respect cooldown period
    if (now - this.lastPushupTime < this.config.cooldownPeriod) return;

    const recentData = this.accelerometerData.slice(-this.WINDOW_SIZE);
    
    // Calculate vertical acceleration (assuming phone is held vertically)
    const verticalAcceleration = recentData.map(d => Math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z));
    
    // Detect pushup pattern: down movement followed by up movement
    const pushupDetected = this.detectPushupMotion(verticalAcceleration, recentData);
    
    if (pushupDetected) {
      this.registerPushup();
    }
  }

  private detectPushupMotion(
    verticalAcceleration: number[], 
    rawData: { x: number; y: number; z: number; timestamp: number }[]
  ): boolean {
    // Look for characteristic pushup pattern:
    // 1. Decrease in vertical acceleration (going down)
    // 2. Increase in vertical acceleration (pushing up)
    // 3. Stabilization (completing the pushup)

    const smoothed = this.smoothData(verticalAcceleration);
    const peaks = this.findPeaks(smoothed);
    const valleys = this.findValleys(smoothed);

    // A valid pushup should have at least one valley (down) followed by a peak (up)
    if (valleys.length === 0 || peaks.length === 0) return false;

    // Check for recent valley-peak pattern
    const recentValley = valleys[valleys.length - 1];
    const recentPeak = peaks[peaks.length - 1];

    // Peak should come after valley
    if (recentPeak <= recentValley) return false;

    // Check amplitude (difference between peak and valley)
    const amplitude = smoothed[recentPeak] - smoothed[recentValley];
    if (amplitude < this.MIN_AMPLITUDE) return false;

    // Check timing (pushup should take reasonable time)
    const duration = (rawData[recentPeak].timestamp - rawData[recentValley].timestamp);
    if (duration < this.config.minPushupDuration / 2 || duration > this.config.minPushupDuration * 2) {
      return false;
    }

    // Additional validation: check for stability after peak
    const stabilityWindow = Math.min(5, smoothed.length - recentPeak - 1);
    if (stabilityWindow > 0) {
      const postPeakData = smoothed.slice(recentPeak + 1, recentPeak + 1 + stabilityWindow);
      const variance = this.calculateVariance(postPeakData);
      if (variance > 0.1) return false; // Too much movement after pushup
    }

    return true;
  }

  private smoothData(data: number[]): number[] {
    // Simple moving average smoothing
    const windowSize = 3;
    const smoothed: number[] = [];
    
    for (let i = 0; i < data.length; i++) {
      const start = Math.max(0, i - Math.floor(windowSize / 2));
      const end = Math.min(data.length, i + Math.floor(windowSize / 2) + 1);
      const window = data.slice(start, end);
      const average = window.reduce((sum, val) => sum + val, 0) / window.length;
      smoothed.push(average);
    }
    
    return smoothed;
  }

  private findPeaks(data: number[]): number[] {
    const peaks: number[] = [];
    
    for (let i = 1; i < data.length - 1; i++) {
      if (data[i] > data[i - 1] && data[i] > data[i + 1]) {
        // Additional check: peak should be significant
        if (data[i] > this.GRAVITY + this.PUSHUP_THRESHOLD) {
          peaks.push(i);
        }
      }
    }
    
    return peaks;
  }

  private findValleys(data: number[]): number[] {
    const valleys: number[] = [];
    
    for (let i = 1; i < data.length - 1; i++) {
      if (data[i] < data[i - 1] && data[i] < data[i + 1]) {
        // Additional check: valley should be significant
        if (data[i] < this.GRAVITY - this.PUSHUP_THRESHOLD) {
          valleys.push(i);
        }
      }
    }
    
    return valleys;
  }

  private calculateVariance(data: number[]): number {
    if (data.length === 0) return 0;
    
    const mean = data.reduce((sum, val) => sum + val, 0) / data.length;
    const variance = data.reduce((sum, val) => sum + Math.pow(val - mean, 2), 0) / data.length;
    
    return variance;
  }

  private registerPushup(): void {
    const now = Date.now();
    this.pushupCount++;
    this.lastPushupTime = now;

    // Provide haptic feedback
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);

    // Calculate confidence based on recent detection quality
    const confidence = this.calculateConfidence();

    const event: PushupEvent = {
      count: this.pushupCount,
      timestamp: now,
      confidence,
    };

    this.emit('pushupDetected', event);
    console.log(`Pushup detected! Count: ${this.pushupCount}, Confidence: ${confidence.toFixed(2)}`);
  }

  private calculateConfidence(): number {
    // Simple confidence calculation based on detection consistency
    // In a real implementation, this would be more sophisticated
    const baseConfidence = this.config.sensitivity;
    const recentDataQuality = this.accelerometerData.length >= this.WINDOW_SIZE ? 1.0 : 0.8;
    
    return Math.min(1.0, baseConfidence * recentDataQuality);
  }

  // Camera-based detection (placeholder for future implementation)
  async startCameraDetection(): Promise<void> {
    // This would integrate with TensorFlow Lite or MediaPipe
    // For now, we'll use accelerometer-based detection
    console.log('Camera-based detection not yet implemented, using accelerometer');
    await this.startDetection();
  }

  // Manual pushup registration (for testing/demo)
  registerManualPushup(): void {
    if (!this.isDetecting) return;
    
    const now = Date.now();
    if (now - this.lastPushupTime < this.config.cooldownPeriod) return;
    
    this.registerPushup();
  }

  // Getters
  get currentCount(): number {
    return this.pushupCount;
  }

  get isActive(): boolean {
    return this.isDetecting;
  }

  get configuration(): PushupDetectionConfig {
    return { ...this.config };
  }

  // Configuration
  updateConfig(config: Partial<PushupDetectionConfig>): void {
    this.config = { ...this.config, ...config };
    this.emit('configUpdated', { config: this.config });
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

  // Reset count
  resetCount(): void {
    this.pushupCount = 0;
    this.emit('countReset', { count: 0 });
  }
}

export default new PushupDetectionService();