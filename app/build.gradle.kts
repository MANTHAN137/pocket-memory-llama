plugins { id("com.android.application") }
android {
    namespace = "com.pocketmemory.app"
    compileSdk = 36
    ndkVersion = "30.0.16248370"
    defaultConfig {
        applicationId = "com.pocketmemory.llama"
        minSdk = 33
        targetSdk = 36
        versionCode = 20
        versionName = "0.6.0"
        testInstrumentationRunner = "com.pocketmemory.app.OfflineInstrumentation"
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = true }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
    packaging { jniLibs { useLegacyPackaging = false } }
}
dependencies {}
