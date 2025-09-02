# Push-Up Counter App

A real-time Android application that uses TensorFlow Lite and ML Kit pose detection to count push-ups through the front camera.

## Features

- **Real-time Pose Detection**: Uses ML Kit's accurate pose detection to track body movements
- **Push-up Counting**: Automatically counts completed push-up repetitions
- **Front Camera Integration**: Uses the device's front camera for self-monitoring
- **Modern UI**: Built with Jetpack Compose for a clean, responsive interface
- **Live Camera Preview**: Shows real-time camera feed with pose detection overlay

## How It Works

The app uses computer vision and machine learning to:

1. **Capture Video**: Continuously captures frames from the front camera
2. **Pose Detection**: Analyzes each frame to identify key body landmarks (shoulders, elbows, wrists, hips)
3. **Angle Calculation**: Calculates the angles between body joints to determine push-up position
4. **State Machine**: Tracks whether the user is in the "down" or "up" position of a push-up
5. **Repetition Counting**: Increments the counter when a complete push-up cycle is detected

## Technical Implementation

### Dependencies

- **ML Kit Pose Detection**: For accurate body landmark detection
- **CameraX**: For camera functionality and preview
- **TensorFlow Lite**: For on-device machine learning capabilities
- **Jetpack Compose**: For modern UI development
- **ViewModel**: For state management and business logic

### Architecture

- **MainActivity**: Main entry point with camera permission handling
- **PushUpViewModel**: Manages camera operations and pose analysis
- **PoseDetectionHelper**: Handles pose analysis and angle calculations
- **CameraPreview**: Composable for camera preview display

### Push-up Detection Algorithm

The app detects push-ups by monitoring:

1. **Elbow Angle**: 
   - Down position: < 90° (arms bent)
   - Up position: > 160° (arms extended)

2. **Shoulder Angle**:
   - Down position: < 45° (body lowered)
   - Up position: > 45° (body raised)

3. **State Transitions**:
   - Must go from down → up position to count a repetition
   - Resets state when returning to standing position

## Setup and Installation

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK 24+ (Android 7.0+)
- Device with front camera
- Camera permissions

### Building the App

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Build and run on a physical device

### Permissions

The app requires:
- `CAMERA` permission for video capture
- Front camera hardware support

## Usage

1. **Launch**: Open the app and grant camera permissions
2. **Position**: Stand in front of the camera, facing it
3. **Start**: Tap "Start" to begin push-up detection
4. **Exercise**: Perform push-ups in view of the camera
5. **Monitor**: Watch the counter increment with each completed push-up
6. **Stop**: Tap "Stop" to pause detection
7. **Reset**: Use "Reset" to clear the counter

## Tips for Best Results

- **Good Lighting**: Ensure adequate lighting for better pose detection
- **Clear Background**: Avoid cluttered backgrounds
- **Full Body View**: Keep your entire upper body in frame
- **Proper Form**: Maintain standard push-up form for accurate counting
- **Stable Position**: Keep the device steady during use

## Troubleshooting

### Common Issues

- **Camera Not Working**: Check camera permissions in device settings
- **No Pose Detection**: Ensure good lighting and clear view of body
- **Inaccurate Counting**: Adjust position to keep full upper body in frame
- **App Crashes**: Restart app and check device compatibility

### Performance Notes

- Works best on devices with good camera quality
- May use significant battery due to continuous camera and ML processing
- Performance varies based on device hardware capabilities

## Future Enhancements

- **Exercise Variety**: Support for other exercises (squats, planks, etc.)
- **Form Feedback**: Real-time form correction suggestions
- **Workout Tracking**: Save and analyze workout sessions
- **Social Features**: Share achievements and compete with friends
- **Customization**: Adjustable detection sensitivity and thresholds

## Contributing

Contributions are welcome! Please feel free to submit pull requests or open issues for bugs and feature requests.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- ML Kit team for pose detection capabilities
- TensorFlow team for the machine learning framework
- Android team for CameraX and modern development tools
