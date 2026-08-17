plugins {
    alias(libs.plugins.android.application)
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
}

dependencies {
    implementation("androidx.health.connect:connect-client:1.2.0-alpha04")
    // Wearable Data Layer API
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    // Wear Health Services (Wear 전용)
    implementation("androidx.health:health-services-client:1.0.0-beta03")
    implementation(libs.play.services.maps)
    implementation(libs.play.services.wearable)
    // Coroutines & Lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    // 구글 맵 API 라이브러리 추가
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    // (선택) 위치 정보를 가져오기 위한 라이브러리
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // Retrofit (서버 통신) & Gson (데이터 변환)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // ✨ 이거 한 줄을 추가해주세요! (일반 String 텍스트를 받기 위한 컨버터)
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
}