plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.jifeng.toolbox"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jifeng.toolbox"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "2.1.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/versions/**/OSGI-INF/**"
        }
    }
}

dependencies {
    // AndroidX core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Compose (Material 3 + 液态玻璃基础)
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Networking (下载器 / GitHub / 酷安拉取)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ZIP 处理 (fastboot 卡刷包解析)
    implementation("org.apache.commons:commons-compress:1.26.2")

    // RSA 加密 (ADB 授权 + 加密工具模块)
    implementation("org.bouncycastle:bcprov-jdk18on:1.78")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78")

    // SSH 客户端 (超级终端 SSH 模式)
    implementation("com.github.mwiede:jsch:0.2.20")
}
