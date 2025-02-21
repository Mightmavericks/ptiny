plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.protegotinyever"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.protegotinyever"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }
    dataBinding {
            enable = true
    }

    buildFeatures {
        dataBinding = true
        viewBinding = true
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
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(platform(libs.firebase.bom.v3370))
    implementation (libs.firebase.database.ktx)
    implementation (libs.gson)
    implementation (libs.webrtc)
    implementation (libs.permissionx)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    //Scalable Size Unit(Support for different size screens)
    implementation (libs.sdp.android)

    // Circular Image
    implementation (libs.circleimageview)

    implementation (libs.room.runtime)
    annotationProcessor (libs.room.compiler)

    // Firebase
    implementation (libs.firebase.firestore)
    implementation (libs.firebase.messaging)

    // Multidex
    implementation (libs.multidex)

    //Country code picker
    implementation (libs.ccp)

    // Retrofit
    implementation (libs.retrofit)
    implementation (libs.converter.scalars)

    // Glide for image loading
    implementation (libs.glide)

    // Stories ProgressView
    implementation (libs.storiesprogressview)
}