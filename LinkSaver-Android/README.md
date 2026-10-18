# LinkSaver Android

A simple Android download manager for direct HTTPS video and audio file URLs (MP4, WebM, MOV, M4V, MP3, M4A). It does not extract or download YouTube watch-page videos.

## APK build

Open the repository **Actions** tab and select **LinkSaver Android APK**. Choose **Run workflow** (or open a push-triggered run), then download the `LinkSaver-debug-apk` artifact after a successful run. The APK inside the artifact is `app-debug.apk`.

The workflow uses Java 17, Android SDK 35, Gradle 8.9 and `assembleDebug` in the `LinkSaver-Android` directory.

## Use

Paste a direct HTTPS media file URL, tap Download, and open the Android Downloads folder when complete. Download only files you own or have permission to save.

## Notes

This project has not been independently compiled or tested in this chat. GitHub Actions will provide the definitive build result. Debug APKs are for testing and are not production-signed.