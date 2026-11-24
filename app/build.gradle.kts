import java.security.SecureRandom
import java.util.Properties
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.gradle)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

val appVersion = Properties().apply {
    load(
        providers.fileContents(
            layout.projectDirectory.file("version.properties")
        ).asText.get().reader()
    )
}
val releaseSigning = providers.gradleProperty("releaseSigning").orNull == "true"
val signingProperties = Properties()
if (releaseSigning) {
    signingProperties.load(
        providers.fileContents(layout.projectDirectory.file("release-signing.properties"))
            .asText.get().reader()
    )
}

android {
    namespace = "com.redloon.prm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.redloon.prm"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersion.getProperty("versionCode").toInt()
        versionName = appVersion.getProperty("versionName")

        testInstrumentationRunner = "com.redloon.prm.HiltTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    if (releaseSigning) {
        signingConfigs {
            create("localRelease") {
                storeFile = file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (releaseSigning) signingConfig = signingConfigs.getByName("localRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
        disable += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable"
        )
        warningsAsErrors = true
    }

    sourceSets {
        getByName("androidTest") {
            // P4 schema discipline: androidx.room:room-testing's MigrationTestHelper
            // reads exported schema JSONs from the *test APK's* assets, keyed by the
            // database class's canonical name. Ship app/schemas there so migration
            // tests always validate against the real committed schemas.
            // (Test-only scratch schemas for AppDatabaseMigrationTest's worked
            // example live separately in androidTest/assets/scratch-schemas/.)
            assets.srcDir("$projectDir/schemas")
        }
    }
}

// P4 schema discipline: Room exports schema JSONs to app/schemas/<db-class>/<version>.json
// on every build. Commit them — migration tests read them back as test-APK assets.
// (Same effect as the Room Gradle plugin's `room { schemaDirectory(...) }`, via the
// plain KSP config this project already uses.)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {

    // P4: room-testing's schema-JSON parsing needs kotlinx-serialization 1.8.1 (the version
    // room-migration 2.8.4 is compiled against). navigation/lifecycle request 1.7.3
    // transitively, and AGP pins androidTest to the app's resolved versions (consistent
    // resolution) — the mixed runtime crashes MigrationTestHelper with AbstractMethodError.
    // Align every configuration on 1.8.1 (compatible minor bump for the app classpath).
    constraints {
        implementation(libs.kotlinx.serialization.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlinx.serialization.json.okio)
    }
    implementation(libs.kotlinx.serialization.json)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android dependencies
    implementation(libs.androidx.work.runtime.ktx)
    coreLibraryDesugaring(libs.android.desugar)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // Hilt and instrumented tests.
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    // Hilt and Robolectric tests.
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.android.compiler)

    // Arch Components
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // Migration tests (MigrationTestHelper) — instrumented only.
    androidTestImplementation(libs.androidx.room.testing)

    // Preferences DataStore (SettingsRepository)
    implementation(libs.androidx.datastore.preferences)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Material Icons
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    // Coil for image loading (contact photos)
    implementation(libs.coil.compose)
    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Local tests: jUnit, coroutines, Android runner
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    lintChecks(libs.slack.compose.lint)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests: jUnit rules and runners

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}

ktlint {
    version.set(libs.versions.ktlint.get())
    baseline.set(file("ktlint-baseline.xml"))
    filter {
        exclude("**/build/**")
    }
}

// Hilt rewrites generated Java directories. Lint's Kotlin analysis can inspect
// test roots too, so finish scheduled Hilt writers before any lint reader starts.
tasks.matching {
    it.name.startsWith("lintAnalyze") || it.name.startsWith("lintVitalAnalyze")
}.configureEach {
    mustRunAfter(tasks.matching { it.name.startsWith("hiltJavaCompile") })
}

abstract class InvocationSeed : ValueSource<String, ValueSourceParameters.None> {
    override fun obtain(): String = SecureRandom().nextLong().toString()
}

val replaySeed = providers.gradleProperty("testSeed")
    .orElse(providers.environmentVariable("TEST_SEED"))
    .orElse(providers.of(InvocationSeed::class) {})

tasks.withType<Test>().configureEach {
    val invocationSeed = replaySeed
    useJUnitPlatform()
    maxParallelForks = 1
    inputs.property("testSeed", invocationSeed)
    systemProperty("junit.jupiter.execution.parallel.enabled", "false")
    systemProperty(
        "junit.jupiter.testclass.order.default",
        "org.junit.jupiter.api.ClassOrderer\$Random"
    )
    systemProperty(
        "junit.jupiter.testmethod.order.default",
        "org.junit.jupiter.api.MethodOrderer\$Random"
    )
    doFirst {
        val seed = invocationSeed.get()
        require(seed.toLongOrNull() != null) { "testSeed must be a signed 64-bit integer" }
        systemProperty("junit.jupiter.execution.order.random.seed", seed)
        logger.lifecycle("JVM shuffle seed: $seed (replay with -PtestSeed=$seed)")
    }
    if (providers.gradleProperty("testOrderLog").isPresent) {
        val testLogger = logger
        beforeTest(
            closureOf<org.gradle.api.tasks.testing.TestDescriptor> {
                testLogger.lifecycle("JVM test: $className#$name")
            }
        )
    }
}
