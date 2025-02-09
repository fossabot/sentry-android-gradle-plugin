plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdk = 30
    defaultConfig {
        minSdk = 21
        targetSdk = 30
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}

dependencies {
    implementation(Samples.Coroutines.core)
    implementation(Samples.Coroutines.android)

    implementation(Samples.Room.runtime)
    implementation(Samples.Room.ktx)

    // this is here for test purposes, to ensure that transitive dependencies are also recognized
    // by our auto-installation
    api(Samples.Retrofit.retrofit)
    api(Samples.Retrofit.retrofitGson)
}
