import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
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
        versionCode = 6
        versionName = "Prototype 6 (UX polishing, bit of cleanup)"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }

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
    // Compose nutzt oft UpperCamelCase für @Composable Funktionen
    // und star-imports für androidx.compose.* sind ebenfalls verbreitet.
    additionalEditorconfig.set(
        mapOf(
            // erlaubt PascalCase für Functions (Compose-freundlich)
            "ktlint_standard_function-naming" to "disabled",
            // wenn du star-imports behalten willst:
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
    implementation(platform(libs.androidx.compose.bom.v20260101))
    androidTestImplementation(platform(libs.androidx.compose.bom.v20260101))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)

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
