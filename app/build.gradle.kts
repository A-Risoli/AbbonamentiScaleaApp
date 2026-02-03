plugins {
    alias(libs.plugins.android.application)
    kotlin("plugin.compose") version "2.0.0"
}

import java.util.Properties
import java.io.FileInputStream

android {
    namespace = "com.arisoli.parcheggiscaleacheck"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.arisoli.parcheggiscaleacheck"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        val localProps = Properties().apply {
            val localPropsFile = rootProject.file("local.properties")
            if (localPropsFile.exists()) {
                load(FileInputStream(localPropsFile))
            }
        }

        val botUsername = localProps.getProperty("BOT_USERNAME", "")
            .trim()
            .trim('"')
        val apiId = localProps.getProperty("API_ID", "0")
            .trim()
            .trim('"')
        val apiHash = localProps.getProperty("API_HASH", "")
            .trim()
            .trim('"')

        if (apiId == "0" || apiHash.isBlank() || botUsername.isBlank()) {
            throw GradleException(
                "Credenziali mancanti in local.properties. " +
                "Imposta BOT_USERNAME, API_ID e API_HASH (vedi local.properties.example)."
            )
        }

        buildConfigField("String", "BOT_USERNAME", "\"$botUsername\"")
        buildConfigField("int", "API_ID", apiId)
        buildConfigField("String", "API_HASH", "\"$apiHash\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs += listOf(
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:generateFunctionKeyMetaAnnotations=true",
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:featureFlag=IntrinsicRemember",
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:featureFlag=OptimizeNonSkippingGroups",
                "-P",
                "plugin:androidx.compose.compiler.plugins.kotlin:featureFlag=StrongSkipping",
                "-Xsuppress-version-warnings"
            )
        }
    }
    
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    
    sourceSets {
        getByName("main") {
            java {
                setSrcDirs(listOf("src/main/java", "src/main/jniLibs/java"))
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    // Gson for JSON
    implementation(libs.gson)
    
    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    
    // CameraX
    implementation("androidx.camera:camera-core:1.4.0")
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    
    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.1")
    
    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}