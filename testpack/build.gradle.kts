plugins {
    id("com.android.application")
}

android {
    namespace = "dev.pulsar.testpack"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.pulsar.testpack"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
