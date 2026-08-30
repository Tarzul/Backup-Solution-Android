import java.util.Properties
import java.io.FileInputStream

// Загрузка keystore.properties
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.rezerv.upload"
    compileSdk = 36

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storePassword = keystoreProperties["storePassword"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
            }
        }
    }

    defaultConfig {
        applicationId = "com.rezerv.upload"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("int", "MAX_HISTORY_RECORDS", "50")
        buildConfigField("int", "MAX_LOG_LENGTH", "20000")
        buildConfigField("long", "SYNC_WAKELOCK_MS", "14400000L")
        buildConfigField("int", "HTTP_CONNECT_TIMEOUT_SEC", "30")
        buildConfigField("int", "HTTP_READ_TIMEOUT_MIN", "10")
        buildConfigField("int", "HTTP_MAX_RETRIES", "3")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
        compose = true
        viewBinding = false
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "NONE"
            }
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = true
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    // ==================== AndroidX Core ====================
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")

    // ==================== Сеть ====================
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.1.0")

    // ==================== Корутины ====================
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // ==================== Lifecycle (MVVM) ====================
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")

    // ==================== Фоновые задачи ====================
    implementation("androidx.work:work-runtime-ktx:2.10.2")

    // ==================== Работа с файлами ====================
    implementation("androidx.documentfile:documentfile:1.1.0")

    // ==================== UI ====================
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.8")
    implementation("androidx.activity:activity-compose:1.10.1")

    // ==================== Jetpack Compose (BOM) ====================
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime-livedata")

    // Hilt для Compose
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ==================== Безопасность ====================
    implementation("androidx.security:security-crypto:1.1.0-alpha07")

    // ==================== Hilt ====================
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-android-compiler:2.60.1")
    implementation("androidx.hilt:hilt-work:1.4.0")
    ksp("androidx.hilt:hilt-compiler:1.4.0")

    // ==================== Logging ====================
    implementation("com.jakewharton.timber:timber:5.0.1")

    // ==================== Изображения (ТОЛЬКО COIL 3) ====================
    implementation("io.coil-kt.coil3:coil:3.3.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    // ==================== Отладка ====================
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    // ==================== Тесты ====================
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("app.cash.turbine:turbine:1.2.1")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("androidx.test:core:1.6.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.work:work-testing:2.10.2")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:5.1.0")

    // ==================== Room ====================
    val roomVersion = "2.8.0"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ==================== DataStore ====================
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // ==================== Okio ====================
    implementation("com.squareup.okio:okio:3.14.0")
}