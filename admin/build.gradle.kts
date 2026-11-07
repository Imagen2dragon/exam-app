import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
    id("org.jetbrains.compose") version "1.9.3"
}

// Single source of truth for the version, shared with the Android app.
version = rootProject.file("../version.txt").readText().trim()

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("net.java.dev.jna:jna-platform:5.18.1")   // Windows DPAPI: encrypts the admin key on disk
    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

compose.desktop {
    application {
        mainClass = "et.tibeb.admin.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Tibeb Admin"
            packageVersion = project.version.toString()
            description = "Tibeb admin: follow a student's exam preparation and AI use"
            vendor = "Tibeb"
            includeAllModules = true
            windows {
                menuGroup = "Tibeb"
                shortcut = true
                dirChooser = true
                perUserInstall = true            // no administrator rights needed to install
                upgradeUuid = "6f1c2a8e-3b7d-4c55-9a1e-2d7f0b9c4e31"   // keep fixed: lets new versions upgrade in place
            }
        }
    }
}

tasks.test { useJUnit() }
