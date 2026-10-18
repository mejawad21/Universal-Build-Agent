plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.example.linksaver"; compileSdk = 35
    defaultConfig { applicationId = "com.example.linksaver"; minSdk = 26; targetSdk = 35; versionCode = 7; versionName = "4.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
 implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}
