plugins {
    `java-library`
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation("org.ow2.asm:asm:9.7.1")
    testImplementation("junit:junit:4.13.2")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

// Exercise the actual runtime bridge on the JVM, including missing platform members.
sourceSets.test {
    java.srcDir("../app/src/main/java/de/haberland/meicaller/compat")
}
