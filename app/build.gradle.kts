plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.aihub"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.aihub"
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    val selectedAbi = providers.gradleProperty("abi").orNull
    if (selectedAbi != null) {
        defaultConfig {
            ndk { abiFilters += selectedAbi }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug { applicationIdSuffix = ".debug" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    // Official sing-box Android binding; native libraries are ABI-filtered by the CI matrix.
    implementation("com.github.singbox-android:libbox:1.14.4")
}
