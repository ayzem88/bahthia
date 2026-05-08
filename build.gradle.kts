/*
 * Bahthia Library — Root build script
 *
 * إعدادات مشتركة لكل الموديولات: نسخة JVM، compileOptions، اختبارات.
 */

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
}

allprojects {
    group = "com.bahthia"
    // مصدر الحقيقة هو AppMetadata.VERSION (modules/domain). إن غيّرته هنا غيّره هناك أيضاً.
    version = "1.0.0"
}

subprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }

    // إعدادات Kotlin/JVM موحَّدة لكل الموديولات
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            compilerOptions {
                freeCompilerArgs.addAll(
                    "-Xjsr305=strict",
                    "-opt-in=kotlin.RequiresOptIn",
                )
            }
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = false
        }
    }
}
