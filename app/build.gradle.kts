plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kapt)
    alias(libs.plugins.navigation)
    //alias(libs.android-extensions)
}

android {
    namespace = "mu.location.savmed"
    compileSdk = 34

    viewBinding {
        enable = true
    }

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
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.retrofit)
    implementation(libs.coverterGson)
    implementation(libs.playServicesLocation)

    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.coil.video)

    implementation ("androidx.databinding:databinding-runtime:7.3.1")
    //implementation("com.nbsp:library:1.8")
    //implementation(libs.linphone-sdk)
    //implementation("org.linphone:linphone-sdk-android-debug:5.4.+")
    implementation("org.greenrobot:eventbus:3.3.1")
    implementation ("androidx.navigation:navigation-compose:2.8.3")

    // Views/Fragments Integration
    implementation ("androidx.navigation:navigation-fragment:2.8.3")
    implementation ("androidx.navigation:navigation-ui:2.8.3")

    // Feature module support for Fragments
    implementation ("androidx.navigation:navigation-dynamic-features-fragment:2.8.3")

    // librarey for circular view of porfile photo in conatct list
    implementation (libs.circleimageview)
    implementation(libs.google.flexbox)
    implementation(libs.linphone)
    // Testing Navigation
    androidTestImplementation ("androidx.navigation:navigation-testing:2.8.3")

    //ripple effect homepage
    implementation ("com.skyfishjy.ripplebackground:library:1.0.1")

}