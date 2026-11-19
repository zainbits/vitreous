plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.zainbits.vitreous"
    compileSdk = 36

    defaultConfig {
        // Android 13+: RuntimeShader (AGSL) and RenderEffect are the only platform APIs that
        // matter, and both exist from 33, so there are no version checks in the library.
        minSdk = 33
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    explicitApi()
}

dependencies {
    api("io.github.zainbits:cohesion:0.1.0-SNAPSHOT")
    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.graphics)

}
