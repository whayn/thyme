plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}


android {
    namespace = "dev.whayn.thyme"
    compileSdk {
        version = release(37)
    }

    // MigrationTestHelper reads the exported schemas from the test APK's assets,
    // not from the build directory, so they have to be packaged in.
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }

    defaultConfig {
        applicationId = "dev.whayn.thyme"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// room-testing parses schema bundles with kotlinx-serialization and is built
// against 1.8.x, but a transitive `strictly 1.7.3` constraint drags the runtime
// back to 1.7.3 - and its generated serializers then hit AbstractMethodError on
// GeneratedSerializer.typeParametersSerializers(). `force` is what overrides a
// `strictly`. It has to be applied app-wide, not just to the test configuration:
// an instrumentation test shares the app's classloader, so the app APK's copy of
// the classes wins and a test-only bump is invisible at runtime.
configurations.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    }
}

// Room writes the schema JSON here on every build. MigrationTestHelper reads it
// to stand up an old database, so migrations can be tested before they ever meet
// real data - and a botched dose_logs rebuild has no undo.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
}