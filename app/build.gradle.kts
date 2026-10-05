plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.jawad.downloader"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jawad.downloader"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
