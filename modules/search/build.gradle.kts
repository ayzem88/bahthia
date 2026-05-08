plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

dependencies {
    api(project(":domain"))

    api(libs.kotlin.stdlib)
    api(libs.kotlinx.coroutines)
    api(libs.bundles.lucene) // api لأنّ تواقيع BahthiaIndexer تكشف Analyzer
    implementation(libs.sqlite.jdbc) // لقراءة Map.db
    implementation(libs.slf4j.api)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.logback.classic)
}
