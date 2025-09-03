# App Lock - Push-Up Verification System

A comprehensive Android application that locks social media and other apps when users exceed daily time limits, requiring them to complete push-ups to unlock.

## Features

### 🔒 App Locking System
- **Selective App Locking**: Choose which applications to monitor and lock
- **Daily Time Limits**: Set custom daily usage limits for each app (in minutes)
- **Automatic Monitoring**: Background service tracks app usage in real-time
- **Smart Notifications**: Get notified when apps are locked due to time limits

### 💪 Push-Up Verification
- **Enhanced Interface**: Full-screen camera preview with reduced opacity for better visibility
- **Real-time Instructions**: Dynamic guidance based on user position and form
- **Blue Markers**: Visual indicators for proper body positioning
- **Encouraging Messages**: Motivational text that changes based on progress
- **Form Validation**: Ensures proper push-up technique before counting

### 🎨 Modern UI/UX
- **Black-Grey Theme**: Sleek dark interface with red accent colors
- **Responsive Design**: Optimized for both portrait and landscape orientations
- **Intuitive Navigation**: Easy-to-use dashboard and settings screens
- **Visual Feedback**: Clear indicators for locked apps and usage statistics

## Technical Architecture

### Data Layer
- **Room Database**: Local storage for app lock settings and push-up configurations
- **Usage Stats API**: Android system integration for app usage monitoring
- **Data Models**: Structured entities for app settings, push-up requirements, and user preferences

### Service Layer
- **AppLockService**: Background service for continuous app usage monitoring
- **AppLockManager**: Business logic for app locking, unlocking, and verification
- **PushUpDetection**: ML Kit integration for accurate push-up counting

### UI Layer
- **Jetpack Compose**: Modern declarative UI framework
- **Navigation**: Single Activity with multiple composable screens
- **State Management**: ViewModel with StateFlow for reactive UI updates

## Screens

### 1. Dashboard
- Overview of locked apps and statistics
- Quick actions for settings and push-up training
- List of currently locked applications

### 2. App Selection
- Browse installed applications
- Toggle app locking on/off
- Configure daily time limits
- Set push-up requirements for unlocking

### 3. Push-Up Interface
- Full-screen camera preview
- Real-time pose detection
- Dynamic instructions and encouragement
- Progress tracking and completion verification

## Setup & Permissions

### Required Permissions
- **Camera**: For push-up detection and verification
- **Usage Stats**: For monitoring app usage (requires user approval)
- **Foreground Service**: For continuous background monitoring
- **Notifications**: For app lock alerts

### Installation
1. Build and install the APK
2. Grant camera permission when prompted
3. Navigate to Settings > Apps > App Lock > Usage Access
4. Enable usage access for the app
5. Configure which apps to lock and set time limits

## Usage Workflow

1. **Setup**: Select apps to lock and configure time limits
2. **Monitoring**: App automatically tracks usage throughout the day
3. **Locking**: When time limit is exceeded, app becomes locked
4. **Unlocking**: Complete required push-ups to regain access
5. **Reset**: Daily limits reset at midnight

## Benefits

- **Digital Wellness**: Promotes healthy device usage habits
- **Physical Activity**: Encourages regular exercise through push-ups
- **Productivity**: Reduces time spent on distracting applications
- **Customizable**: Flexible settings for different apps and user preferences
- **Privacy**: All data stored locally on device

## Technical Requirements

- **Android**: API 24+ (Android 7.0+)
- **Camera**: Front-facing camera required
- **Storage**: Local database for settings and configurations
- **Permissions**: Usage stats access (system-level permission)

## Future Enhancements

- **Multiple Exercise Types**: Support for different workout activities
- **Social Features**: Share achievements and compete with friends
- **Analytics**: Detailed usage and fitness statistics
- **Cloud Sync**: Backup settings across devices
- **Custom Workouts**: Create personalized exercise routines

## Contributing

This project demonstrates modern Android development practices including:
- Jetpack Compose for UI
- Room database for local storage
- MVVM architecture with ViewModels
- Coroutines and Flow for asynchronous operations
- ML Kit integration for pose detection

## License

This project is for educational and personal use. Please respect the terms of any third-party libraries and APIs used.
