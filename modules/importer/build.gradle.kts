plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":persistence"))
    implementation(project(":search"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.poi.ooxml)
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic) // للسجلات أثناء التشغيل من CLI

    testImplementation(libs.bundles.testing)
    testImplementation(libs.logback.classic)
}

application {
    mainClass.set("com.bahthia.importer.migration.MigrationToolKt")
}

// Gradle task: ./gradlew :importer:migrate --args="<shards> <output>"
tasks.register<JavaExec>("migrate") {
    group = "bahthia"
    description = "ترحيل بيانات Python sharded SQLite إلى فهرس Lucene"
    mainClass.set("com.bahthia.importer.migration.MigrationToolKt")
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}
