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
        // The installed identity. Deliberately separate from `namespace`, which
        // stays a code-organization concern and names the Kotlin package.
        applicationId = "org.joefang.boreas.android"
        // 29 is derived from a requirement, not picked for taste: VpnService's
        // isAlwaysOn() and isLockdownEnabled() arrive at 29, and below that the
        // app cannot read always-on state at all. Raising the floor deletes an
        // entire "cannot know" variant from the model rather than guarding it.
        minSdk = 29
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

    /**
     * Lint reports every issue it finds, and every issue it finds fails the build.
     *
     * A warning is a defect the tool already located. Left as a warning it survives
     * one review, and after that nobody reads the list at all.
     *
     * The report format is not configured here. AGP 9 always writes the text, HTML,
     * and XML reports and deprecates the switches that used to select them, so CI
     * prints the text report from disk instead. See .github/workflows/ci.yml.
     */
    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
        checkTestSources = true
        // A baseline would turn today's findings into permanent exemptions. There
        // is no baseline file, and adding one needs a reason recorded here.
        baseline = null
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        // Same rule as :domain. explicitApi is deliberately absent: this module is
        // an application rather than a published surface, and every @Composable
        // would have to write out `: Unit` for no reader's benefit.
        allWarningsAsErrors.set(true)
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
