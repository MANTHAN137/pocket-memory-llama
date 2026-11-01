plugins { id("com.android.application") }
android {
    namespace = "com.pocketmemory.app"
    compileSdk = 36
    ndkVersion = "30.0.16248370"
    defaultConfig {
        applicationId = "com.pocketmemory.llama"
        minSdk = 33
        targetSdk = 36
        versionCode = 22
        versionName = "0.8.0"
        buildConfigField("String", "REVIEWER_UNLOCK_SHA256", "\"\"")
        buildConfigField("String", "PLAY_BILLING_PUBLIC_KEY", "\"\"")
        buildConfigField("String", "SUPPORT_EMAIL", "\"\"")
        buildConfigField("String", "PRIVACY_URL", "\"\"")
        buildConfigField("String", "REPORTS_URL", "\"\"")
        buildConfigField("String", "PUBLISHER", "\"Manthan Dhole\"")
        buildConfigField("int", "UNLOCK_MULTIPLIER", "0")
        buildConfigField("int", "UNLOCK_OFFSET", "0")
        testInstrumentationRunner = "com.pocketmemory.app.EditionInstrumentation"
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
dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.22"))
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
}
