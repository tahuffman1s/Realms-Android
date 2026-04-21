import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    namespace = "com.realmsoffate.game"
    compileSdk = 34
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.realmsoffate.game"
        minSdk = 26
        targetSdk = 34
        versionCode = 100
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            storeFile = file(
                keystoreProperties.getProperty("storeFile")
                    ?: System.getenv("KEYSTORE_FILE")
                    ?: "missing.jks"
            )
            storePassword = keystoreProperties.getProperty("storePassword")
                ?: System.getenv("KEYSTORE_PASSWORD")
            keyAlias = keystoreProperties.getProperty("keyAlias")
                ?: System.getenv("KEY_ALIAS")
            keyPassword = keystoreProperties.getProperty("keyPassword")
                ?: System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (signingConfigs["release"].storeFile?.exists() == true) {
                signingConfigs["release"]
            } else {
                null
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/io.netty.versions.properties"
            )
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// Generate a source index mapping UI text literals → file:line for the debug annotated endpoint
val generateDebugSourceIndex by tasks.registering {
    val srcDir = file("src/main/kotlin")
    val outFile = file("src/debug/kotlin/com/realmsoffate/game/debug/SourceIndex.kt")
    inputs.dir(srcDir)
    outputs.file(outFile)
    doLast {
        val index = mutableMapOf<String, String>()
        // Match various UI text patterns
        val patterns = listOf(
            Regex("""(?:Text|text)\(\s*(?:text\s*=\s*)?"([^"$]{2,})""""),
            Regex("""(?:content[Dd]escription|label|title|placeholder)\s*=\s*"([^"$]{2,})""""),
            Regex("""(?:Text|Button|Tab|Icon)\([^)]*"([^"$]{3,})""""),
        )
        // Separate pattern for lines with UI composable keywords
        val uiKeywords = Regex("""(?:Text|Button|Tab|NavigationBarItem|TopAppBar|BottomAppBar|Scaffold)\s*\(""")
        val anyLiteral = Regex(""""([^"$]{3,})"""")

        srcDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { lineIdx, line ->
                // Match specific patterns
                for (p in patterns) {
                    for (m in p.findAll(line)) {
                        val t = m.groupValues[1]
                        if (t.length >= 3 && !t.contains("\\u") && !t.startsWith("//"))
                            index.putIfAbsent(t, "${file.name}:${lineIdx + 1}")
                    }
                }
                // On lines with UI keywords, also capture any string literal >= 3 chars
                if (uiKeywords.containsMatchIn(line)) {
                    for (m in anyLiteral.findAll(line)) {
                        val t = m.groupValues[1]
                        if (t.length >= 3 && !t.contains("\\u") && !t.startsWith("//") && !t.contains("."))
                            index.putIfAbsent(t, "${file.name}:${lineIdx + 1}")
                    }
                }
            }
        }
        val entries = index.entries.sortedBy { it.value }.joinToString(",\n        ") { (text, src) ->
            """"${text.replace("\\", "\\\\").replace("\"", "\\\"")}" to "$src""""
        }
        outFile.parentFile.mkdirs()
        outFile.writeText(buildString {
            appendLine("package com.realmsoffate.game.debug")
            appendLine()
            appendLine("/** Auto-generated source index — maps UI text literals to source file:line. */")
            appendLine("object SourceIndex {")
            appendLine("    val textToSource = mapOf(")
            appendLine("        $entries")
            appendLine("    )")
            appendLine("}")
        })
    }
}
tasks.matching { it.name == "compileDebugKotlin" }.configureEach { dependsOn(generateDebugSourceIndex) }
tasks.matching { it.name.startsWith("ksp") && it.name.contains("Debug") }.configureEach { dependsOn(generateDebugSourceIndex) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    // Bundles Noto Color Emoji so emoji render the same on every device.
    implementation("androidx.emoji2:emoji2-bundled:1.5.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-text-google-fonts:1.7.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.2")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    testImplementation("androidx.room:room-testing:2.6.1")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}