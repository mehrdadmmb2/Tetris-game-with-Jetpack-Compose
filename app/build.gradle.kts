plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.neon.tetris"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.neon.tetris"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            // Set to "false" to exercise real billing flows (Google Play, Cafe Bazaar, Myket) from debug builds.
            buildConfigField("boolean", "SIMULATE_PURCHASES", "false")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "SIMULATE_PURCHASES", "false")
        }
    }

    flavorDimensions += "store"
    productFlavors {
        create("google") {
            dimension = "store"
            buildConfigField("String", "STORE_NAME", "\"Google Play\"")
        }
        create("bazaar") {
            dimension = "store"
            buildConfigField("String", "STORE_NAME", "\"Cafe Bazaar\"")
        }
        create("myket") {
            dimension = "store"
            buildConfigField("String", "STORE_NAME", "\"Myket\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    "googleImplementation"(libs.play.billing)
    "bazaarImplementation"(libs.bazaar.poolakey)
    "myketImplementation"(libs.myket.billing)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
