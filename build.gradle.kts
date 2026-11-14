buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 uses built-in Kotlin. Pin KGP so the Compose compiler plugin
        // and the Kotlin version used by the app always match.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
