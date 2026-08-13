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
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
