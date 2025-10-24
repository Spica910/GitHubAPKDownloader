# GitHub APK Downloader

An Android application for browsing GitHub repositories and downloading APK files, with automatic APK detection displayed on the first page.

[한국어 README](README_KO.md)

## Features

### Core Features
- **GitHub OAuth Device Flow** authentication (no client secret needed)
- **APK Detection**: Automatically scans and displays APK files from releases on the main screen
- **Branch Selector**: Switch between repository branches with dropdown menu
- **Repository Management**: Create new repositories directly from the app
- **File Browser**: Browse repository file trees
- **Upload Files/Folders**: Upload single files or entire folders to any branch

### Download & Install
- One-click APK download from the main screen
- View all releases with APK files
- Automatic download management with notifications
- Direct installation option after download

### Display
- Shows APK location, filename, and size on repository cards
- Repository counter with APK count (e.g., "📦 6 repositories (2 with APKs)")
- Dark theme with Material Design 3

## Setup Instructions

### 1. Create a GitHub OAuth App

1. Go to [GitHub Developer Settings](https://github.com/settings/developers)
2. Click "New OAuth App"
3. Fill in the details:
   - **Application name**: GitHub APK Downloader (or any name you prefer)
   - **Homepage URL**: https://github.com/yourusername (or any URL)
   - **Authorization callback URL**: `githubapk://callback`
4. Click "Register application"
5. Note down your **Client ID** and generate a **Client Secret**

### 2. Configure the App

Open `app/src/main/java/com/github/apkdownloader/GitHubAuthHelper.kt` and replace the placeholders:

```kotlin
const val CLIENT_ID = "your_github_client_id_here"
const val CLIENT_SECRET = "your_github_client_secret_here"
```

### 3. Build the App

#### Option A: Using Android Studio
1. Open the project in Android Studio
2. Wait for Gradle sync to complete
3. Click "Run" or press Shift+F10

#### Option B: Using Command Line (Termux)
```bash
cd GitHubAPKDownloader
./gradlew assembleDebug
```

The APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### 4. Install and Run

1. Install the generated APK on your Android device
2. Launch the app
3. Click "Login with GitHub"
4. Authorize the app in your browser
5. You'll be redirected back to the app
6. Browse repositories and download APKs!

## Permissions

The app requires the following permissions:
- **INTERNET**: To access GitHub API
- **WRITE_EXTERNAL_STORAGE** (Android 6-9): To save downloaded files
- Downloads are saved to the Downloads folder using scoped storage on Android 10+

## Project Structure

```
GitHubAPKDownloader/
├── app/
│   ├── src/main/
│   │   ├── java/com/github/apkdownloader/
│   │   │   ├── MainActivity.kt              # OAuth login screen
│   │   │   ├── RepositoryListActivity.kt    # List/search repositories
│   │   │   ├── RepositoryAdapter.kt         # Repository RecyclerView adapter
│   │   │   ├── ReleasesActivity.kt          # View releases for a repo
│   │   │   ├── ReleasesAdapter.kt           # Releases RecyclerView adapter
│   │   │   ├── ApkDownloader.kt             # APK download manager
│   │   │   ├── GitHubModels.kt              # Data models
│   │   │   ├── GitHubApiService.kt          # Retrofit API interface
│   │   │   ├── GitHubAuthHelper.kt          # OAuth helper
│   │   │   └── RetrofitClient.kt            # Retrofit client setup
│   │   ├── res/
│   │   │   ├── layout/                      # XML layouts
│   │   │   ├── values/                      # Strings, colors, themes
│   │   │   ├── menu/                        # Menu resources
│   │   │   └── xml/                         # File provider paths
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── README.md
```

## Technologies Used

- **Kotlin**: Modern Android development
- **Retrofit**: REST API client
- **Coroutines**: Asynchronous programming
- **Material Design 3**: Modern UI components
- **GitHub API v3**: Repository and release data
- **Custom Tabs**: In-app browser for OAuth
- **DownloadManager**: System download handling

## Usage

1. **Login**: Authenticate with your GitHub account
2. **Browse**: View your repositories or search for others
3. **Select Repository**: Tap on any repository
4. **View Releases**: See all releases with APK files
5. **Download**: Tap the download button on any release
6. **Install**: After download completes, tap the notification to install

## Notes

- Only releases with APK files are shown
- Multiple APKs per release are supported
- Downloads appear in the system Downloads folder
- You may need to allow installation from unknown sources in your device settings

## Troubleshooting

### "Authentication failed"
- Double-check your Client ID and Client Secret
- Ensure the callback URL is exactly `githubapk://callback`

### "Failed to load repositories"
- Check your internet connection
- Verify the access token is valid
- Check if you have access to the repositories

### "Storage permission required"
- Grant storage permission when prompted (Android 6-9)
- On Android 10+, no permission needed (uses scoped storage)

### Cannot install APK
- Enable "Install unknown apps" permission for this app in Settings
- Check if the APK file downloaded completely

## License

This project is for educational purposes. Feel free to modify and use as needed.

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.
