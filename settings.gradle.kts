/*
 * المكتبة البحثيّة — Bahthia Library
 * إعدادات المشروع المتعدّد الموديولات
 */

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "Bahthia_Library"

// الموديولات السبعة وفق خطّة الترحيل
include(":domain")
include(":search")
include(":persistence")
include(":importer")
include(":lifecycle")
include(":i18n")
include(":app-desktop")

project(":domain").projectDir       = file("modules/domain")
project(":search").projectDir       = file("modules/search")
project(":persistence").projectDir  = file("modules/persistence")
project(":importer").projectDir     = file("modules/importer")
project(":lifecycle").projectDir    = file("modules/lifecycle")
project(":i18n").projectDir         = file("modules/i18n")
project(":app-desktop").projectDir  = file("modules/app-desktop")
