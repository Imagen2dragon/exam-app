plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "et.tibeb.examprep"
    compileSdk = 36

    defaultConfig {
        applicationId = "et.tibeb.examprep"
        minSdk = 24          // Android 7.0+: covers almost every phone still in use in Ethiopia
        targetSdk = 36
        // CI passes the version from the git tag (v0.4.0 -> name 0.4.0, code 400); local builds use the defaults.
        versionName = (findProperty("tibebVersionName") as String?) ?: "0.3.0"
        versionCode = (findProperty("tibebVersionCode") as String?)?.toInt() ?: 3
        vectorDrawables.useSupportLibrary = true
    }

    // Release signing key comes only from environment variables (GitHub Actions secrets).
    // It is never stored in this public repository. Without it, release builds fall back
    // to the debug key: fine for local testing, but such APKs must not be published.
    val releaseKey = System.getenv("TIBEB_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKey != null) create("release") {
            storeFile = releaseKey
            storePassword = System.getenv("TIBEB_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("TIBEB_KEY_ALIAS")
            keyPassword = System.getenv("TIBEB_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true      // R8: shrink + obfuscate -> small APK, harder to reverse
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    // One APK per CPU type is not needed: the app has no native code.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    sourceSets { getByName("debug").assets.srcDir("$projectDir/schemas") } // migration tests only; not in release
    packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json") }
    androidResources { localeFilters += listOf("en", "am") }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    ksp("androidx.room:room-compiler:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // Screenshot tests: render real screens on the JVM (no emulator needed)
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.76.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.76.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.room:room-testing:2.8.3")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
