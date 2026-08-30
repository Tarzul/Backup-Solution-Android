plugins {
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20-RC2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20-RC2" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
    id("com.google.devtools.ksp") version "2.4.20-1.0.25" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}