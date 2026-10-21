plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.mostadiag.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.mostadiag.app"
        minSdk = 26
        targetSdk = 32
        versionCode = 7
        versionName = "0.6.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    // Sideloaded APK: retain the existing OBD permission behavior; Play target migration is separate.
    lint { disable += "ExpiredTargetSdkVersion" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
