plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// gradle.properties 의 pleosSdk 값 (없으면 true)
val usePleosSdk = (findProperty("pleosSdk") as String?)?.toBoolean() ?: true

android {
    namespace = "com.chagok.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chagok.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // 팀원 컴퓨터와 GitHub 자동 빌드가 같은 서명으로 APK를 만들게 하는 시연용 디버그 키
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Pleos SDK를 "이름으로" 불러 쓰기 위한 도구 (공식 샘플 앱도 같은 방식을 씁니다)
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.21")

    if (usePleosSdk) {
        implementation("ai.pleos.playground:Vehicle:2.3.2")
        implementation("ai.pleos.playground:NaviHelper:3.0.5")
    }
}
