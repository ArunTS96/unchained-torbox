// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    id("com.github.ben-manes.versions") version "0.54.0"
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.plugin)
        classpath(libs.navigation.plugin)
        classpath(libs.hilt.plugin)
        // AGP 9's sdkReleaseDependencyData task needs Tink on the plugin classpath; without it
        // assembleRelease fails at configuration time with "com/google/crypto/tink/hybrid/HybridConfig".
        classpath("com.google.crypto.tink:tink:1.17.0")
        // The protobuf-gradle-plugin pins an older protobuf-java, and AGP 9's
        // collectReleaseDependencies needs the 4.x generated API (mutableCopy(IntList)).
        classpath("com.google.protobuf:protobuf-java:4.28.3")
    }
}

// dependencyUpdates fails in parallel mode with Gradle 9+ (https://github.com/ben-manes/gradle-versions-plugin/issues/968)
tasks.named("dependencyUpdates") {
    doFirst {
        gradle.startParameter.isParallelProjectExecutionEnabled = false
    }
}