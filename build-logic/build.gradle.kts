plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        create("versioning") {
            id = "dev.boreaslab.boreas.versioning"
            implementationClass = "dev.boreaslab.boreas.release.VersioningPlugin"
        }
    }
}

kotlin {
    compilerOptions {
        // Same rule as :app and :domain. A warning is a defect the compiler has
        // already located, and build logic is not the place to start excusing them.
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
    }
}
