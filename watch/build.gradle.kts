plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.wellrun"
    compileSdk {
        version = release(37) {
        }
    }

    defaultConfig {
        applicationId = "com.example.wellrun"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

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
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.health.connect:connect-client:1.2.0-alpha04")
    // 안드로이드 기본 UI 라이브러리 (AppCompatActivity 용)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.health:health-services-client:1.0.0-beta03")
    implementation("androidx.wear:wear-ongoing:1.0.0")

    // Wear OS 관련
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    implementation("androidx.health:health-services-client:1.0.0-beta03")

    // Activity 관련
    implementation("androidx.activity:activity-ktx:1.7.2")
    // Wearable Data Layer API
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    // Wear Health Services (Wear 전용)
    implementation("androidx.health:health-services-client:1.0.0-beta03")
    implementation(libs.androidx.wear)
    // Coroutines & Lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.compose.ui.tooling)
    implementation(libs.play.services.wearable)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}