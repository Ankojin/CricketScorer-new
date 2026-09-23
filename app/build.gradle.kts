import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.cricketscorer"
    compileSdk = 37

    val versionPropsFile = rootProject.file("version.properties")
    val versionProps = Properties()
    if (versionPropsFile.exists()) {
        val stream = versionPropsFile.inputStream()
        versionProps.load(stream)
        stream.close()
    }
    
    val vCode = versionProps.getProperty("VERSION_CODE", "40").toInt()
    val vName = versionProps.getProperty("VERSION_NAME", "2.28.0")

    defaultConfig {
        applicationId = "com.example.cricketscorer"
        minSdk = 24
        targetSdk = 35
        versionCode = vCode
        versionName = vName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }


    signingConfigs {
        create("release") {
            val ksFile = rootProject.file("keystore/release.jks")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD") ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS") ?: System.getenv("RELEASE_KEY_ALIAS") ?: "cricscore"
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD") ?: System.getenv("RELEASE_KEY_PASSWORD")
            } else {
                storeFile = file("../release.jks")
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD") ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS") ?: System.getenv("RELEASE_KEY_ALIAS") ?: "cricscore"
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD") ?: System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val ksFile = rootProject.file("keystore/release.jks")
            if (ksFile.exists() || file("../release.jks").exists()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        getByName("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("com.google.android.gms:play-services-nearby:19.0.0")
    
    val room_version = "2.8.5"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")
    androidTestImplementation("androidx.room:room-testing:$room_version")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}



tasks.register("incrementVersionCode") {
    doLast {
        // v2.33.18: Only rotate version after a successful RELEASE build. 🏏🚀⚖️🏅
        // This ensures the current APK version matches the properties file exactly.
        val versionPropsFile = rootProject.file("version.properties")
        if (versionPropsFile.exists()) {
            val versionProps = Properties()
            versionPropsFile.inputStream().use { versionProps.load(it) }
            
            val currentVCode = versionProps.getProperty("VERSION_CODE", "100").toInt()
            versionProps.setProperty("VERSION_CODE", (currentVCode + 1).toString())
            
            val currentVName = versionProps.getProperty("VERSION_NAME", "2.33.0")
            val parts = currentVName.split(".").toMutableList()
            if (parts.size >= 3) {
                val patch = parts.last().toInt()
                parts[parts.size - 1] = (patch + 1).toString()
                versionProps.setProperty("VERSION_NAME", parts.joinToString("."))
            }
            
            versionPropsFile.outputStream().use { versionProps.store(it, null) }
            println("Version rotated to prepare for NEXT release.")
        }
    }
}

tasks.configureEach {
    val isBuildTask = name.startsWith("assemble") || name.startsWith("bundle")
    if (isBuildTask && !name.contains("Test")) {
        // v2.33.18: Rotate ONLY on Release. finalizedBy ensures rotation happens AFTER APK is generated. 🏏🚀⚖️🏅
        if (name.contains("Release", ignoreCase = true)) {
            finalizedBy("incrementVersionCode")
        }
        finalizedBy("copyApkToRoot")
    }
}

tasks.register("copyApkToRoot") {
    doLast {
        // v2.33.20: Copy Release APK if it exists, fallback to Debug. 🏏🚀⚖️🏅
        val vName = android.defaultConfig.versionName
        val buildDir = layout.buildDirectory.get().asFile
        
        val releaseApk = file("$buildDir/outputs/apk/release/app-release.apk")
        val debugApk = file("$buildDir/outputs/apk/debug/app-debug.apk")
        
        val apkFile = if (releaseApk.exists()) releaseApk else debugApk
        
        if (apkFile.exists()) {
            val isRelease = apkFile == releaseApk
            val suffix = if (isRelease) "" else "-debug"
            
            // Automated Cleanup
            project.fileTree(rootProject.projectDir)
                .matching { include("cricscore_v*.apk") }
                .forEach { it.delete() }

            copy {
                from(apkFile)
                into(rootProject.projectDir)
                rename { "cricscore_v$vName$suffix.apk" }
            }
            println("APK copied to root: cricscore_v$vName$suffix.apk (Source: ${if (isRelease) "Release" else "Debug"})")
        }
    }
}
