plugins {
    id("com.android.application") version "7.4.2"
    id("org.jetbrains.kotlin.android") version "1.8.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.0"
}

android {
    namespace = "eu.kanade.tachiyomi.extension.es.megabanana"
    compileSdk = 34

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.es.megabanana"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.4.1"
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
    compileOnly("com.github.tachiyomiorg:extensions-lib:1.4.4")
    // Jsoup y OkHttp son proveidos por la app, por eso son compileOnly
    compileOnly("org.jsoup:jsoup:1.17.2")
    compileOnly("com.squareup.okhttp3:okhttp:5.0.0-alpha.12")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    compileOnly("uy.kohesive.injekt:injekt-core:1.16.1")
}
