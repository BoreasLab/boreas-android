plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.boreaslab.boreas"
    // Compose 1.12 requires compiling against 37. targetSdk stays at 36 on purpose:
    // compiling against newer APIs is separate from opting in to new runtime
    // behavior, and docs/verified-inputs.md records foreground-service behavior for
    // the target SDK as unverified until device work. Raise it with a device result.
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.boreaslab.boreas"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-a1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // The simulated session exists only here. See EngineHost.
            buildConfigField("boolean", "SIMULATION_AVAILABLE", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "SIMULATION_AVAILABLE", "false")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

}
