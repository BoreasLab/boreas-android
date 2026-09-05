import com.android.build.api.dsl.ManagedVirtualDevice
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * The ABIs this app ships, and the single place the set is decided.
 *
 * Three, not four. `armeabi-v7a` is deliberately absent from the contract
 * (api/android.md#no-32-bit-arm), and Google's 64-bit requirement runs one way
 * only: 32-bit obliges 64-bit, never the reverse.
 *
 * The pinned archive nevertheless *contains* an `armeabi-v7a` build, which the
 * contract says it does not. Naming the three here is therefore load-bearing
 * rather than decorative: it is what keeps the fourth out of the APK, both by
 * filtering the unpack below and by filtering the packaging step. Both derive
 * from this one list, so the two cannot disagree.
 */
val boreasAbis = listOf("arm64-v8a", "x86_64", "x86")

/** The pinned release. Its digest is checked on every fetch. */
val boreasPin = Properties().apply {
    rootProject.file("gradle/boreas-core.properties").inputStream().use(::load)
}

fun pinned(key: String): String =
    requireNotNull(boreasPin.getProperty(key)) { "gradle/boreas-core.properties has no '$key'" }

/**
 * Downloads the pinned core archive, checks its digest, and unpacks exactly the
 * three ABIs above plus the header.
 *
 * The archive is a build input like any other, so it is verified on the way in
 * rather than trusted because it arrived. A digest mismatch deletes the file and
 * fails: a corrupted download must not become a cached one. Build provenance is
 * the stronger check and runs in CI, where the workflow that produced the
 * archive can be named; see .github/workflows/ci.yml.
 */
abstract class FetchBoreasCore : DefaultTask() {

    @get:Input
    abstract val tag: Property<String>

    @get:Input
    abstract val archive: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:Input
    abstract val abis: ListProperty<String>

    /** Asserted against the header in the archive, so the pin cannot drift from it. */
    @get:Input
    abstract val abiVersion: Property<Int>

    /** Survives `clean`; the archive is 23 MB and its identity is its digest. */
    @get:Internal
    abstract val cacheDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val destination: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun fetch() {
        val expected = sha256.get()
        val cached = cacheDirectory.get().asFile.resolve("${tag.get()}/${archive.get()}")

        if (!cached.isFile || digestOf(cached) != expected) {
            cached.parentFile.mkdirs()
            val url = "https://github.com/BoreasLab/boreas-core/releases/download/" +
                "${tag.get()}/${archive.get()}"
            logger.lifecycle("Fetching $url")
            val partial = File(cached.parentFile, "${cached.name}.part")
            URI(url).toURL().openStream().use { source ->
                partial.outputStream().use(source::copyTo)
            }
            // Rename only after the whole body arrived, so an interrupted download
            // cannot be mistaken for a cached one on the next build.
            partial.renameTo(cached)
        }

        val actual = digestOf(cached)
        if (actual != expected) {
            cached.delete()
            throw GradleException(
                "Boreas core archive digest mismatch for ${tag.get()}\n" +
                    "  expected $expected\n" +
                    "  actual   $actual\n" +
                    "The file has been deleted. Check gradle/boreas-core.properties.",
            )
        }

        val staging = temporaryDir.resolve("unpacked")
        fs.delete { delete(staging, destination) }
        fs.copy {
            from(archives.tarTree(archives.gzip(cached)))
            into(staging)
        }

        val out = destination.get().asFile
        // Named one by one rather than copied wholesale: the set of ABIs shipped is
        // a decision, and a fourth appearing in the archive must not silently ship.
        for (abi in abis.get()) {
            for (name in NATIVE_LIBRARIES) {
                val library = staging.resolve("jniLibs/$abi/$name")
                if (!library.isFile) {
                    throw GradleException("${archive.get()} carries no jniLibs/$abi/$name")
                }
                library.copyTo(out.resolve("jniLibs/$abi/$name"), overwrite = true)
            }
        }

        val header = staging.resolve("include/boreas.h")
        if (!header.isFile) {
            throw GradleException("${archive.get()} carries no include/boreas.h")
        }

        // The header is the authority for the ABI number; the pin file only records
        // it so that it is available before the archive exists. Disagreement here
        // means the pin was updated without adopting the ABI change, which would
        // compile a stale constant into the startup check that exists to catch
        // exactly that.
        val declared = Regex("""#define\s+BOREAS_ABI_VERSION\s+(\d+)u?""")
            .find(header.readText())?.groupValues?.get(1)?.toInt()
            ?: throw GradleException("boreas.h in ${archive.get()} defines no BOREAS_ABI_VERSION")
        if (declared != abiVersion.get()) {
            throw GradleException(
                "Boreas ABI version mismatch for ${tag.get()}\n" +
                    "  gradle/boreas-core.properties says abiVersion=${abiVersion.get()}\n" +
                    "  the shipped boreas.h says              $declared\n" +
                    "Read api/stability.md, then update the pin in the same commit.",
            )
        }

        header.copyTo(out.resolve("include/boreas.h"), overwrite = true)
    }

    private fun digestOf(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        /**
         * `libboreas.so` records `libc++_shared.so` as a `NEEDED` entry. The linker
         * that resolves it runs on the device, so a missing one is a `dlopen` failure
         * after shipping rather than a build error here. Hence the assertion.
         */
        val NATIVE_LIBRARIES = listOf("libboreas.so", "libc++_shared.so")
    }
}

val boreasCoreDirectory: Provider<Directory> = layout.buildDirectory.dir("boreas/core")

val fetchBoreasCore = tasks.register<FetchBoreasCore>("fetchBoreasCore") {
    description = "Downloads, verifies, and unpacks the pinned Boreas core release."
    group = "build setup"
    tag.set(pinned("tag"))
    archive.set(pinned("archive"))
    sha256.set(pinned("sha256"))
    abiVersion.set(pinned("abiVersion").toInt())
    abis.set(boreasAbis)
    cacheDirectory.set(File(gradle.gradleUserHomeDir, "boreas-core"))
    destination.set(boreasCoreDirectory)
}

/**
 * The unpacked libraries are a prebuilt input rather than something this project
 * generates, so they are registered as a static source directory and filled in by
 * `fetchBoreasCore`. AGP checks that the folder exists while it configures, which
 * is earlier than any task runs, so it is created here.
 */
val boreasJniLibs: File = boreasCoreDirectory.get().asFile.resolve("jniLibs").apply { mkdirs() }

/*
 * The name and the number, taken rather than derived.
 *
 * `resolve` decides both once, in the release workflow, and passes them down. The
 * tag it produces carries a timestamp, so a second job that recomputed the
 * identity would agree with the first only by luck.
 *
 * The fallbacks are for a build with no tag and no pipeline: a developer's
 * machine, and CI's own artifact build. `version` is the literal 0.0.0 committed
 * in gradle.properties, which is visibly a placeholder rather than a claim, and
 * `1` is the lowest versionCode Android accepts. Neither is ever published:
 * publishing goes through the workflow, which always injects.
 */
val injectedVersionName: String = providers.gradleProperty("boreas.versionName")
    .getOrElse(version.toString())

val injectedVersionCode: Int = providers.gradleProperty("boreas.versionCode").orNull
    ?.let { declared ->
        declared.toIntOrNull()
            ?: throw GradleException("-Pboreas.versionCode=$declared is not an integer")
    }
    ?: 1

val injectedProvenance: String = providers.gradleProperty("boreas.provenance")
    .getOrElse("local build")

/**
 * The release signing key, when the environment carries one.
 *
 * Absence is a state rather than a failure: this repository can be built by
 * somebody who has no key and wants an artefact to inspect, and CI's own
 * per-push build is exactly that. What must not happen is a half-configured
 * signing config, so the three secrets beside the keystore are demanded the
 * moment the keystore appears rather than defaulted to empty and discovered by
 * `apksigner`.
 *
 * The release workflow names its assets `-unsigned` when this is absent, so a
 * tester learns before `adb install` does.
 */
val signingKeystore: String? = providers.environmentVariable("BOREAS_KEYSTORE")
    .orNull
    ?.takeIf(String::isNotEmpty)

fun signingSecret(name: String): String = providers.environmentVariable(name).orNull
    ?: throw GradleException(
        "BOREAS_KEYSTORE is set, so $name must be too. Signing is all four values or none.",
    )

/**
 * Which build type the instrumented tests instrument.
 *
 * Release is what ships, and R8 shrinking is proven only by running the shrunk
 * artefact. Parsed against the closed set here: an unknown name would otherwise
 * select a variant that does not exist and fail deep inside AGP.
 */
val instrumentedBuildType: String =
    when (val requested = providers.gradleProperty("boreas.testBuildType").getOrElse("debug")) {
        "debug", "release" -> requested
        else -> throw GradleException("boreas.testBuildType is debug or release, not '$requested'")
    }

// Android installs nothing unsigned, and only debug carries a key of its own.
require(instrumentedBuildType == "debug" || signingKeystore != null) {
    "release instrumentation needs BOREAS_KEYSTORE; .github/scripts/ephemeral-key.sh makes a throwaway one"
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
        //
        // It also clears the core's own floor twice over: api/android.md asks for
        // minSdk >= 23, and the shipped binaries are built against API 26.
        minSdk = 29
        targetSdk = 36
        versionCode = injectedVersionCode
        versionName = injectedVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            //noinspection ChromeOsAbiSupport -- see boreasAbis: three is the contract.
            abiFilters += boreasAbis
        }

        // Read at load and compared with boreas_abi_version(). See BoreasLibrary.
        buildConfigField("int", "BOREAS_ABI_VERSION", pinned("abiVersion"))
        buildConfigField("String", "BOREAS_CORE_TAG", "\"${pinned("tag")}\"")

        // What a 30-bit integer cannot say. A bug report has to map to one
        // (app version, core version) pair or it maps to nothing, and the pair is
        // only legible if the app half names the release it is an offset from.
        buildConfigField("String", "BOREAS_APP_PROVENANCE", "\"$injectedProvenance\"")
    }

    signingConfigs {
        if (signingKeystore != null) {
            create("release") {
                storeFile = file(signingKeystore)
                storePassword = signingSecret("BOREAS_KEYSTORE_PASSWORD")
                keyAlias = signingSecret("BOREAS_KEY_ALIAS")
                keyPassword = signingSecret("BOREAS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // The simulated session exists only here. See EngineHost.
            buildConfigField("boolean", "SIMULATION_AVAILABLE", "true")
        }
        release {
            // Null when no key was supplied, which leaves the artefact unsigned
            // rather than failing. See signingKeystore.
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Only while this build type is the one under instrumentation. See
            // the file: renaming has to go off in the app, not in the test APK.
            if (instrumentedBuildType == "release") {
                proguardFiles("proguard-under-test.pro")
            }
            testProguardFiles("proguard-test-rules.pro")
            buildConfigField("boolean", "SIMULATION_AVAILABLE", "false")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testBuildType = instrumentedBuildType

    /**
     * The virtual devices the instrumented tests run on.
     *
     * Declared here rather than in a workflow, so one Gradle task reproduces a CI
     * result and no third-party action owns the emulator: Gradle downloads the
     * image, boots it, installs, runs, and tears down.
     *
     * Two API levels bracket every level-dependent branch this app has. 29 is the
     * floor; CA install changes at 30, the notification permission at 33, and
     * foreground-service types at 34, all under the ceiling. aosp rather than
     * google_apis because nothing here calls Play services, and the smaller image
     * boots faster.
     */
    testOptions {
        managedDevices {
            allDevices {
                create<ManagedVirtualDevice>("api29") {
                    device = "Pixel 2"
                    apiLevel = 29
                    systemImageSource = "aosp"
                    // Without this, 29 resolves to a 32-bit x86 image, and x86 is
                    // the one shipped ABI no phone runs.
                    require64Bit = true
                }
                create<ManagedVirtualDevice>("api36") {
                    device = "Pixel 6"
                    apiLevel = 36
                    systemImageSource = "aosp"
                    require64Bit = true
                }
            }
        }
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

        // The one exemption, named rather than absorbed by a baseline.
        //
        // OldTargetApi wants targetSdk raised to compileSdk. Holding it one behind
        // is the decision recorded in docs/build-inputs.md: compiling against a
        // newer API is not the same as opting in to its runtime behavior changes,
        // and for a VPN service those changes reach the foreground-service and
        // background-execution rules the tunnel's whole lifecycle rests on. There
        // is no device here to observe them on. Raise targetSdk with a device
        // result attached and delete this line in the same commit.
        disable += "OldTargetApi"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // useLegacyPackaging is deliberately unset. With minSdk >= 23 that stores
        // .so files uncompressed and page-aligned, which is what both 16 KB
        // compliance and a direct mmap out of the APK need. api/android.md asks
        // for exactly this, and android:extractNativeLibs must stay out of the
        // manifest because AGP replaced it with this DSL in 4.2.0.
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addStaticSourceDirectory(boreasJniLibs.absolutePath)
    }
}

// A static source directory carries no task dependency, so the fetch is ordered
// here. preBuild precedes every variant task, which is what the jniLibs merge and
// the packaging step both need.
tasks.named("preBuild") { dependsOn(fetchBoreasCore) }

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

    // The C boundary is the interface (api/README.md), and Kotlin cannot produce a
    // C function pointer. JNA builds the two vtables' trampolines; the alternative
    // is a JNI shim, which needs the NDK. See docs/platform-integration.md.
    implementation(libs.jna) { artifact { type = "aar" } }

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Plain JVM tests only. The pure parts of this module, route and token sets,
    // need no device and no Robolectric. Asserting a rendered composable needs one
    // or the other, and that is a dependency worth deciding on rather than
    // acquiring as a side effect of wanting a first test here.
    testImplementation(libs.junit)

    // The same JNA, as the desktop jar rather than the aar, so its own native
    // library is present on a plain JVM. That is what lets the struct layouts be
    // asserted here instead of only on a device: boreas.h pins every offset from
    // the C side, and BoreasLayoutTest pins the same numbers from this side,
    // against the same JNA that computes them at run time. Nothing in these tests
    // loads libboreas.
    testImplementation(libs.jna)

    // The device lane, asserting what no JVM test can see: that the shipped .so
    // links, that consent gates the native start, and that a stopped session
    // leaves no descriptor behind. See docs/platform-integration.md.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.kotlinx.coroutines.core)
}
