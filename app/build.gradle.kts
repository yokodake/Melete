import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/** The version people talk about. Changing it starts the build number again from 0. */
val appVersionName = "1.1"

/**
 * One build number for debug and release alike, counting every build of the app: any invocation
 * that assembles, installs, bundles or packages it. Kept in build-number.properties at the root
 * (not versioned — it is this machine's count), and reset to 0 when [appVersionName] changes.
 * Test-only runs and IDE syncs leave it alone.
 */
val buildNumber: Int = run {
    val file = rootProject.file("build-number.properties")
    val stored = Properties().apply { if (file.exists()) file.inputStream().use { load(it) } }
    val sameVersion = stored.getProperty("versionName") == appVersionName
    val previous = if (sameVersion) stored.getProperty("build")?.toIntOrNull() else null
    val building = gradle.startParameter.taskNames.any { requested ->
        val task = requested.substringAfterLast(':')
        listOf("assemble", "install", "bundle", "package").any { task.startsWith(it) }
    }
    val number = when {
        !building -> previous ?: 0
        previous == null -> 0
        else -> previous + 1
    }
    if (building) {
        stored.setProperty("versionName", appVersionName)
        stored.setProperty("build", number.toString())
        file.outputStream().use {
            stored.store(it, "Shared build counter for debug and release; resets when versionName changes.")
        }
    }
    number
}

/** Android's own versionCode has to keep rising across version changes: major, minor, then build. */
val appVersionCode: Int = run {
    val (major, minor) = (appVersionName.split('.') + "0").map { it.toIntOrNull() ?: 0 }
    major * 1_000_000 + minor * 10_000 + buildNumber
}

android {
    namespace = "com.yokodake.melete"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.yokodake.melete"
        minSdk = 33
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("int", "BUILD_NUMBER", buildNumber.toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            optimization {
                enable = false
            }
            // Preserve the signing identity of existing sideloaded releases. Debug now uses a
            // separate application ID; sharing this key does not share their private app data.
            signingConfig = signingConfigs.getByName("debug")
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

// Room schemas are exported so that future phases can write migration tests against the real
// historical schema instead of falling back to a destructive reset.
room {
    schemaDirectory("$projectDir/schemas")
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
