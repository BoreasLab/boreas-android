plugins {
    alias(libs.plugins.kotlin.jvm)
}

/**
 * The pure half of the Android shell.
 *
 * AGENTS.md requires framework effects to stay in Kotlin adapters and decisions to
 * stay pure. A module boundary makes that a compile error rather than a convention:
 * nothing in here can reach for an Android type, so the lifecycle state machine
 * cannot quietly acquire one, and its tests run on a plain JVM.
 */
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    /**
     * Every declaration states its visibility and its return type.
     *
     * This module is the seam the shared engine will be bound against, so what is
     * public here is a decision rather than a default. Strict mode makes an omitted
     * modifier an error: a helper cannot become part of the contract by being
     * forgotten, and an inferred return type cannot change the published signature
     * when its body is edited.
     */
    explicitApi()

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // A warning is a defect the compiler already found. Treating it as anything
        // less means the first one is tolerated and the hundredth is invisible.
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
