plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.slf4j.api)
    // نَحتاج compose.runtime لكي تَتجاوب الواجهة عند تَبديل اللغة (mutableStateOf).
    implementation(compose.runtime)

    testImplementation(libs.bundles.testing)
}
