# Android App

This Android app converts `.vod` files to `.mp4` directly on the device.

Highlights:
- Native Android app built with Kotlin and Jetpack Compose
- Uses Media3 Transformer for hardware-accelerated transcoding
- File picker, progress bar, cancel, and share support
- Minimum Android version: 6.0 (API 23)

How to run:
1. Open `android-app` in Android Studio.
2. Let Gradle sync and install the required Android SDK components.
3. Run the `app` configuration on a device or emulator.

Note:
- This workspace did not have `gradle` installed, so Gradle wrapper files were not generated here.
- If Android Studio asks for wrapper generation or Gradle setup, let the IDE create it during the first sync.

Current scope:
- High-quality on-device conversion is implemented.
- AI upscaling is not implemented inside the Android app yet. The desktop Python script in the repo is still the path that includes optional AI enhancement.
