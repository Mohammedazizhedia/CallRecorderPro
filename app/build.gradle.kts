plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.callrecorder.pro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.callrecorder.pro"
        minSdk = 29
        targetSdk = 35
        versionCode = 13
        versionName = "1.0-v13"
        ndk { abiFilters += listOf("arm64-v8a") }
        resourceConfigurations += listOf("ar", "en")
    }

    signingConfigs {
        create("release") {
            val ksPath = providers.gradleProperty("RELEASE_STORE_FILE").orNull
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val ksPath = providers.gradleProperty("RELEASE_STORE_FILE").orNull
            signingConfig = if (ksPath != null)
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.workmanager.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.ui.tooling.preview)
}
