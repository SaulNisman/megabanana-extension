plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "1.9.22"
}

android {
    namespace = "eu.kanade.tachiyomi.extension.es.megabanana"
    compileSdk = 34

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.es.megabanana"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // API base de Tachiyomi/Mihon
    compileOnly("com.github.keiyoushi:extensions-lib:1.4")
    // Jsoup y OkHttp son proveidos por la app, por eso son compileOnly
    compileOnly("org.jsoup:jsoup:1.17.2")
    compileOnly("com.squareup.okhttp3:okhttp:5.0.0-alpha.12")
}
