import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "dev.tidytv.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.tvtidy.cleaner"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.0-rc6"
    }

    // Release signing comes from keys/signing.properties, a git-ignored folder in the repo.
    // Without it (CI, contributors) release builds fall back to the debug key.
    val signing = rootProject.file("keys/signing.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
    }
    signingConfigs {
        if (signing != null) create("release") {
            storeFile = rootProject.file("keys/" + signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            // Phones are ARM; x86 native libraries (Conscrypt's are ~3 MB each) only serve emulators.
            // Debug builds keep every ABI so any emulator still runs them.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets["main"].assets.directories += "build/generated/lists"
}

// Snapshot of the upstream cleanyourtv lists, bundled so the app works offline.
val copyLists by tasks.registering(Copy::class) {
    from(rootProject.file("upstream/cleanyourtv/examples")) {
        include("*/debloat.txt")
        eachFile { path = "lists/" + file.parentFile.name + ".txt" }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("generated/lists"))
}
// Looks for the Apple TV style home screen (cleanyourtv themes/, MIT): config, wallpapers and a preview.
val copyThemes by tasks.registering(Copy::class) {
    from(rootProject.file("upstream/cleanyourtv/themes")) {
        include("default/**", "aurora/**", "glass/**", "scenes/**")
        exclude("**/*.md")
    }
    // Our own original look (config.json + a generated wallpaper): see app/themes-own/midnight.
    from(file("themes-own")) { include("midnight/**") }
    into(layout.buildDirectory.dir("generated/lists/themes"))
}
tasks.named("preBuild") { dependsOn(copyLists, copyThemes) }

dependencies {
    implementation(project(":adb"))
    implementation(project(":core"))
    // Kadb's Wireless debugging pairing needs TLS 1.3 keying material that the platform Conscrypt hides.
    // Bundling Conscrypt makes Kadb use its own copy, so the hidden-API bypass it also ships is never needed.
    implementation(libs.conscrypt.android)
    // Google Play's in-app review dialog (only used when TidyTV was installed from Play).
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Kadb pulls in a hidden-API bypass for the platform Conscrypt; Play flags it and it is unused with the bundled one.
configurations.configureEach { exclude(group = "org.lsposed.hiddenapibypass") }

// The Android Conscrypt jar has no JNI for the desktop JVM and would shadow Robolectric's own copy,
// so unit tests run without it (the app itself is unchanged).
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    exclude(group = "org.conscrypt", module = "conscrypt-android")
}

// Screenshot and view model tests run once, on the debug variant.
tasks.matching { it.name == "testReleaseUnitTest" }.configureEach { enabled = false }
