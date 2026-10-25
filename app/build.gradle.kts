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
}

android {
    namespace = "com.idontremember.personalcrm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.idontremember.personalcrm"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.idontremember.personalcrm.HiltTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.8.1")
    }

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android dependencies
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
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
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
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    // Coil for image loading (contact photos)
    implementation(libs.coil.compose)
    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Local tests: jUnit, coroutines, Android runner
    testImplementation(libs.junit)
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
