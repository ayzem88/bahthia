import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":search"))
    implementation(project(":persistence"))
    implementation(project(":importer"))
    implementation(project(":lifecycle"))
    implementation(project(":i18n"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.swing) // ضروري لـ Dispatchers.Main في Compose Desktop
    implementation(libs.koin.core)
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    // Apache POI لتصدير .docx من ExportDialog
    implementation(libs.poi.ooxml)

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    // SplitPane الرسميّ من JetBrains — لإطار الأعمدة القابلة للسحب
    implementation("org.jetbrains.compose.components:components-splitpane-desktop:1.7.0")

    testImplementation(libs.bundles.testing)
    // Compose UI tests يُضاف في المرحلة ٣ مع باقي الواجهة
}

compose.desktop {
    application {
        mainClass = "com.bahthia.app.MainKt"

        nativeDistributions {
            // كتب العينة الخام في `<rootDir>/resources/Books/*.txt`.
            // مهمة Gradle "syncBundledBooks" أدناه تنسخها إلى البنية التي تتوقعها
            // Compose Desktop (`build/app-resources/common/Books/`).
            // وقت التشغيل: System.getProperty("compose.application.resources.dir") + "/Books"
            appResourcesRootDir.set(layout.buildDirectory.dir("app-resources"))

            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Bahthia Library"
            // packageVersion: شرط jpackage/MSI أن يكون MAJOR.MINOR.BUILD رقمياً (لا suffix).
            // الإصدار المعروض للمستخدم وفي version.json يأتي من AppMetadata.VERSION.
            // نُبقي هذا الرقم ثابتاً عند 1.0.0 (سقف MSI الفنّي) حتى نصل لإصدار 1.0 الحقيقي.
            packageVersion = "1.0.0"
            // ملاحظة: WiX/MSI لا يُرحّب بحروف Unicode في حقول metadata (يفشل light.exe بكود 311).
            // لذلك نستعمل إنجليزيّة هنا. الواجهة العربيّة محفوظة في AppMetadata.
            description = "Bahthia Library — Arabic text search tool"
            copyright = "(c) 2026 Ayman Atieb Ben Nji"
            vendor = "Ayman Atieb Ben Nji"

            windows {
                iconFile.set(project.file("../../resources/icons/bahthia.ico"))
                // GUID صالح ثابت للترقيات (لا يتغيّر بين الإصدارات)
                upgradeUuid = "B4D7B1A1-2026-4A1B-9C3D-7E5A6F2C8D40"
                menuGroup = "Bahthia"
                shortcut = true
                dirChooser = true
                perUserInstall = false
            }

            macOS {
                // iconFile.set(project.file("../../resources/icons/bahthia.icns"))
                // ملاحظة: macOS يَحتاج .icns. نُولّدها لاحقاً عند جاهزيّة جهاز Mac.
                bundleID = "com.bahthia.research-library"
            }

            linux {
                iconFile.set(project.file("../../resources/icons/bahthia.png"))
            }
        }
    }
}

// نسخ كتب العينة من `resources/Books/` إلى الموقع الذي تتوقعه Compose Desktop.
// هذا يحدث تلقائيا قبل البناء أو التشغيل.
val syncBundledBooks by tasks.registering(Sync::class) {
    from(project.rootDir.resolve("resources/Books")) {
        include("*.txt")
    }
    into(layout.buildDirectory.dir("app-resources/common/Books"))
}

// ربط المهمة بكل ما يحتاج المصادر جاهزة (تشغيل + تغليف).
afterEvaluate {
    tasks.matching {
        it.name == "run" ||
        it.name == "runDistributable" ||
        it.name.startsWith("package") ||
        it.name.startsWith("createDistributable")
    }.configureEach { dependsOn(syncBundledBooks) }
}
