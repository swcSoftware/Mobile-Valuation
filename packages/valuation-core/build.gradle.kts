import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.library")
}

/*
 * ValueLens valuation core — pure Kotlin, no platform APIs, no networking.
 * The host app supplies a blocking `Fetcher` (HTTP GET) and a `KeyValueCache`; the core does
 * SEC EDGAR parsing, normalization, Model A/B, data checks and beta. Reference implementation:
 * services/valuation-engine (Python) — see the oracle diff test in commonTest.
 */
kotlin {
    androidTarget { compilations.all { compileTaskProvider.configure { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } } } }
    jvm("desktop")  // for fast unit tests without an emulator
    val xcf = XCFramework("ValuationCore")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework { baseName = "ValuationCore"; isStatic = true; xcf.add(this) }
    }
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

/*
 * The oracle fixtures and the Python tag map are read by tests but live outside this module, so
 * Gradle would consider the test task up to date after they change (and silently skip it).
 */
tasks.withType<Test>().configureEach {
    inputs.dir(project.file("../../services/valuation-engine/tests/fixtures")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(project.file("../../services/valuation-engine/valuation_engine/normalize/tags.py")).withPathSensitivity(PathSensitivity.RELATIVE)
}

android {
    namespace = "com.swcsoftware.valuelens.core"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
