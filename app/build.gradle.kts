plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber = (System.getenv("VERSION_CODE") ?: "1").toInt()

android {
    namespace = "app.monbureau"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.monbureau"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.$buildNumber"
    }

    // One fixed key kept in the repository, so every build installs over the previous one
    // without losing the data stored on the phone.
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("bureau.keystore")
            storePassword = "monbureau"
            keyAlias = "bureau"
            keyPassword = "monbureau"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("shared") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
