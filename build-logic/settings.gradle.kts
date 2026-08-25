// The version algebra is an included build rather than a Gradle script, because
// it is code with laws and the laws have tests. See README in the repository root
// docs for why none of it lives in YAML.
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "build-logic"
