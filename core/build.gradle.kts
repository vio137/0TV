plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Tests read the upstream lists straight from the submodule.
tasks.test {
    systemProperty("tidytv.upstream", rootProject.file("upstream/cleanyourtv").absolutePath)
    systemProperty("tidytv.icons", rootProject.file("app/src/main/assets/icons.json").absolutePath)
}
