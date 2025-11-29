plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.smartworks"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.smartworks"
        minSdk = 24
        targetSdk = 35
        // Semantic Versioning
        val versionMajor = 1
        val versionMinor = 0
        val versionPatch = 0
        
        versionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch
        versionName = "$versionMajor.$versionMinor.$versionPatch"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.swiperefreshlayout)
    
    // HTTP networking with OkHttp
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    
    // JSON parsing with Gson
    implementation(libs.gson)
    
    // AndroidX Security for encrypted preferences
    implementation(libs.androidx.security.crypto)
    
    // For JSON parsing in BLE provisioning
    implementation(libs.json)

    // Testing dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}