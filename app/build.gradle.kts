import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

android {
    namespace = "de.haberland.meicaller"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.haberland.meicaller"
        minSdk = 31
        targetSdk = 36
        versionCode = 14
        versionName = "Prototype 14"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { 
        compose = true 
        buildConfig = true // Aktiviert BuildConfig für MeiCallerApp
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ktlint {
    additionalEditorconfig.set(
        mapOf(
            "ktlint_standard_function-naming" to "disabled",
            "ktlint_standard_no-wildcard-imports" to "disabled",
        ),
    )
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$projectDir/config/detekt/detekt.yml"))
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)

    // In-App Updates
    implementation(libs.play.app.update.ktx)

    // Firebase & Crashlytics
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    // Für einstellbare Farben
    implementation(libs.androidx.datastore.preferences)
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs ktlint, detekt and android lint."
    dependsOn(
        ":app:ktlintCheck",
        ":app:detekt",
        ":app:lint",
    )
}
