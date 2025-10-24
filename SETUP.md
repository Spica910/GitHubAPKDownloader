# Quick Setup Guide

## Prerequisites (For Termux)

If you're building in Termux, you'll need:

```bash
pkg install openjdk-17 gradle
```

## Step 1: Configure GitHub OAuth

1. Go to https://github.com/settings/developers
2. Click "New OAuth App"
3. Fill in:
   - Application name: GitHub APK Downloader
   - Homepage URL: https://github.com
   - Authorization callback URL: `githubapk://callback`
4. Copy the Client ID and Client Secret

## Step 2: Update Credentials

Edit `app/src/main/java/com/github/apkdownloader/GitHubAuthHelper.kt`:

```kotlin
const val CLIENT_ID = "paste_your_client_id_here"
const val CLIENT_SECRET = "paste_your_client_secret_here"
```

## Step 3: Add App Icons (Optional)

The app is configured to use default Android icons. To add custom icons:

1. Generate icons using https://romannurik.github.io/AndroidAssetStudio/
2. Place them in:
   - `app/src/main/res/mipmap-mdpi/ic_launcher.png`
   - `app/src/main/res/mipmap-hdpi/ic_launcher.png`
   - `app/src/main/res/mipmap-xhdpi/ic_launcher.png`
   - `app/src/main/res/mipmap-xxhdpi/ic_launcher.png`
   - `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`

Or use default Android icons (already configured in manifest).

## Step 4: Build the App

### In Termux:
```bash
cd GitHubAPKDownloader
./gradlew assembleDebug
```

### In Android Studio:
1. Open project
2. Wait for Gradle sync
3. Run > Run 'app'

## Step 5: Install

The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

Install it on your device:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or transfer it to your device and install manually.

## Troubleshooting

### Gradle Build Issues in Termux

If you encounter memory issues:
```bash
export GRADLE_OPTS="-Xmx2048m -XX:MaxPermSize=512m"
./gradlew assembleDebug --no-daemon
```

### Missing Android SDK

You'll need the Android SDK to build. In Termux, you can:
1. Install termux-api
2. Set up Android SDK with command-line tools
3. Or use Android Studio on a computer

### Building Without Android Studio

For a minimal setup:
```bash
# Download command-line tools from:
# https://developer.android.com/studio#command-tools

# Set up environment
export ANDROID_HOME=/path/to/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin

# Accept licenses
sdkmanager --licenses

# Build
./gradlew assembleDebug
```

## Features Included

✓ GitHub OAuth authentication
✓ Repository browsing and search
✓ Release listing
✓ APK file detection and download
✓ System download manager integration
✓ Download notifications
✓ Install prompt after download
✓ Material Design 3 UI
✓ Dark theme support

## Next Steps

1. Test the app thoroughly
2. Add error handling for edge cases
3. Implement pagination for large repository lists
4. Add filters for releases (stable, pre-release, etc.)
5. Support multiple APK downloads per release
6. Add download queue management
7. Implement APK verification (checksums)

Enjoy your GitHub APK Downloader!
