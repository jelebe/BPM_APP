plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")

}


android {
    namespace = "com.besos.bpm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.besos.bpm"
        minSdk = 29
        targetSdk = 35
        versionCode = 42
        versionName = "1.4.3"


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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(platform("com.google.firebase:firebase-bom:33.10.0"))

    // Osmdroid para el mapa
    implementation("org.osmdroid:osmdroid-android:6.1.10")

    // Firebase Storage
    implementation("com.google.firebase:firebase-storage-ktx")

    // Firebase Realtime Database
    implementation("com.google.firebase:firebase-database-ktx")

    // Firebase Messaging
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Dependencia para AppCompat (necesaria para AppCompatActivity)
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Dependencia para Material Design (opcional pero recomendada)
    implementation("com.google.android.material:material:1.9.0")

    implementation("com.google.android.gms:play-services-location:21.0.1")

    implementation("com.google.firebase:firebase-auth-ktx")

    implementation("com.google.android.gms:play-services-auth:20.7.0")

    implementation("com.github.bumptech.glide:glide:4.15.1")
    annotationProcessor("com.github.bumptech.glide:compiler:4.15.1")
    implementation("com.github.yalantis:ucrop:2.2.6")

    implementation("com.squareup.picasso:picasso:2.8")

    implementation("androidx.activity:activity-ktx:1.3.0") // Necesario para Activity Result API

    implementation("androidx.activity:activity-ktx:1.6.0") // Si estás en una versión más reciente
    implementation("androidx.fragment:fragment-ktx:1.4.0") // Necesario si usas fragmentos

    implementation("com.squareup.retrofit2:retrofit:2.9.0") // Obtener País y Ciudad
    implementation("com.squareup.retrofit2:converter-gson:2.9.0") // Obtener País y Ciudad


}
