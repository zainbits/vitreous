plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.zainbits.vitreous.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.zainbits.vitreous.sample"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["appLabel"] = "Vitreous"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "Vitreous Debug"
        }
        // Release code (R8, no debuggable overhead) that profilers may still attach to.
        // Use this one for performance numbers; debug builds run Compose far slower.
        create("bench") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            isProfileable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            applicationIdSuffix = ".bench"
            manifestPlaceholders["appLabel"] = "Vitreous"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":vitreous"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
}
