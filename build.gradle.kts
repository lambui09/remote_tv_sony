// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        jcenter()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:${pluginVersions.agp}")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${pluginVersions.kotlin}")
        classpath("com.google.dagger:hilt-android-gradle-plugin:${pluginVersions.hilt}")
        classpath("com.diffplug.spotless:spotless-plugin-gradle:5.15.0")
        classpath ("com.google.gms:google-services:4.3.10")
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.7.1")
        classpath("com.github.ben-manes:gradle-versions-plugin:0.39.0")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            val version = JavaVersion.VERSION_1_8.toString()
            jvmTarget = version
            sourceCompatibility = version
            targetCompatibility = version
            freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"
            languageVersion = "1.6"
        }
    }

    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        jcenter()
        maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}

subprojects {
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "com.github.ben-manes.versions")

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("**/*.kt")

            ktlint(pluginVersions.ktlint).userData(
                // TODO this should all come from editorconfig https://github.com/diffplug/spotless/issues/142
                mapOf(
                    "indent_size" to "2",
                    "kotlin_imports_layout" to "ascii",
                    "disabled_rules" to "no-wildcard-imports"
                )
            )

            trimTrailingWhitespace()
            indentWithSpaces()
            endWithNewline()
        }

        format("xml") {
            target("**/res/**/*.xml")

            trimTrailingWhitespace()
            indentWithSpaces()
            endWithNewline()
        }

        kotlinGradle {
            target("**/*.gradle.kts", "*.gradle.kts")

            ktlint(pluginVersions.ktlint).userData(
                mapOf(
                    "indent_size" to "2",
                    "kotlin_imports_layout" to "ascii",
                    "disabled_rules" to "no-wildcard-imports"
                )
            )

            trimTrailingWhitespace()
            indentWithSpaces()
            endWithNewline()
        }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
