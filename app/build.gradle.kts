plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kapt)
    alias(libs.plugins.navigation)
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin)
    //alias(libs.android-extensions)
}

//kotlin {
//    sourceSets.all {
//        languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
//    }
//}

android {
    namespace = "mu.location.savmed"
    compileSdk = 35

    defaultConfig {
        applicationId = "mu.location.savmed"
        minSdk = 24
        //noinspection OldTargetApi
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    viewBinding {
        enable = true
    }

    buildTypes {
//        release {
//            isShrinkResources = true
//            isMinifyEnabled = true
////            proguardFiles(
////                getDefaultProguardFile("proguard-android-optimize.txt"),
////                "proguard-rules.pro"
////            )
//        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        dataBinding = true
        viewBinding = true
        buildConfig = true
    }
    splits {
        // Configures multiple APKs based on ABI.
        abi {

            // Enables building multiple APKs per ABI.
            isEnable = true

            // By default all ABIs are included, so use reset() and include
            // to specify that we only want APKs for x86, armeabi-v7a, and mips.

            // Resets the list of ABIs that Gradle should create APKs for to none.
            reset()

            // Specifies a list of ABIs that Gradle should create APKs for.
            include("x86", "x86_64", "arm64-v8a", "armeabi-v7a")

            // Specifies that we do not want to generate a universal APK
            // that includes all ABIs.
            isUniversalApk = false
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.mediarouter)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.androidx.emoji2.emojipicker)
    implementation(libs.play.services.maps)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.transport.api)
    implementation(libs.transport.api)
    implementation(libs.transport.api)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // For API
    implementation(libs.retrofit)
    implementation(libs.coverterGson)

    // For Location Extraction
    implementation(libs.playServicesLocation)

    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.coil.video)
    implementation (libs.android.maps.utils)

    implementation (libs.androidx.databinding.runtime)
    implementation (libs.androidx.navigation.compose)

    // Linphone Recommended Media Library to Manage Audio
    implementation ("androidx.media:media:1.7.0")

    // Views/Fragments Integration
    implementation (libs.androidx.navigation.fragment)
    implementation (libs.androidx.navigation.ui)

    // Feature module support for Fragments
    implementation (libs.androidx.navigation.dynamic.features.fragment)

    // Library for circular view of Profile photo in Contact list
    implementation (libs.circleimageview)
    implementation(libs.google.flexbox)
    implementation(libs.linphone)
    implementation(libs.klaxon)

    // Testing Navigation
    androidTestImplementation (libs.androidx.navigation.testing)

    // Ripple effect homepage
    implementation (libs.library)

    // For Work Manager
    implementation(libs.androidx.work.runtime.ktx)

    // Creating Event Bus to Flood Data on the App
    //implementation(libs.eventbus)
}