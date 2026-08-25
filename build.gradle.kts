plugins {
    // The release tag algebra, from the build-logic included build. It owns the
    // `resolve` task and nothing else: :app takes the name and the number it is
    // given rather than working them out a second time.
    id("dev.boreaslab.boreas.versioning")
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
