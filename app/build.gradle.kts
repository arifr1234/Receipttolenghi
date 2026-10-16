import org.gradle.kotlin.dsl.java
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

val googleFontsApiKey: String = System.getenv("GOOGLE_FONTS_API_KEY") 
    ?: localProperties.getProperty("GOOGLE_FONTS_API_KEY") 
    ?: ""

val keystorePath: String? = System.getenv("KEYSTORE_PATH") ?: localProperties.getProperty("KEYSTORE_PATH")
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD") ?: localProperties.getProperty("KEYSTORE_PASSWORD")
val keyAliasStr: String? = System.getenv("KEY_ALIAS") ?: localProperties.getProperty("KEY_ALIAS")
val keyPasswordStr: String? = System.getenv("KEY_PASSWORD") ?: localProperties.getProperty("KEY_PASSWORD")

android {
    namespace = "com.arifr1234.receipttolenghi"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.arifr1234.receipttolenghi"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        buildConfigField("String", "GOOGLE_FONTS_API_KEY", "\"$googleFontsApiKey\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                val keystoreFile = rootProject.file(keystorePath).takeIf { it.exists() } 
                    ?: file(keystorePath).takeIf { it.exists() }
                if (keystoreFile != null) {
                    storeFile = keystoreFile
                    storePassword = keystorePassword
                    keyAlias = keyAliasStr
                    keyPassword = keyPasswordStr
                }
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appName"] = "(Debug) Receipttolenghi"
        }
        release {
            manifestPlaceholders["appName"] = "@string/app_name"
            val isReleaseKeystoreAvailable = keystorePath != null && (
                rootProject.file(keystorePath).exists() || file(keystorePath).exists()
            )
            signingConfig = if (isReleaseKeystoreAvailable) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.escpos.thermalprinter.android)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.gson)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}