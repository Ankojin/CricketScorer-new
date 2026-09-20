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

    signingConfigs {
        create("release") {
            storeFile = file("../release.jks")
            storePassword = "cricscore_pass"
            keyAlias = "cricscore"
            keyPassword = "cricscore_pass"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
        // v2.33.15: Safety check - only auto-increment for Release builds to avoid version-drift in debug. 🏏🚀⚖️🏅
        // Manual bump: To manually change the version, edit 'VERSION_NAME' and 'VERSION_CODE' in version.properties.
        val isRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
        if (!isRelease) return@doLast

        val versionPropsFile = rootProject.file("version.properties")
        if (versionPropsFile.exists()) {
            val versionProps = Properties()
            versionPropsFile.inputStream().use { versionProps.load(it) }
            
            // Increment Version Code
            val currentVCode = versionProps.getProperty("VERSION_CODE", "40").toInt()
            versionProps.setProperty("VERSION_CODE", (currentVCode + 1).toString())
            
            // Increment Version Name (patch version)
            val currentVName = versionProps.getProperty("VERSION_NAME", "2.33.0")
            val parts = currentVName.split(".").toMutableList()
            if (parts.size >= 3) {
                val patch = parts.last().toInt()
                parts[parts.size - 1] = (patch + 1).toString()
                val newVName = parts.joinToString(".")
                versionProps.setProperty("VERSION_NAME", newVName)
            }
            
            versionPropsFile.outputStream().use { versionProps.store(it, null) }
            println("Release Build detected: Version properties updated.")
        }
    }
}

tasks.configureEach {
    val isBuildTask = name.startsWith("assemble") || name.startsWith("bundle")
    // v2.26.47: Prevent incrementing on minor IDE tasks/syncs
    if (isBuildTask && !name.contains("Test")) {
        dependsOn("incrementVersionCode")
        finalizedBy("copyApkToRoot")
    }
}

tasks.register("copyApkToRoot") {
    doLast {
        // v2.33.15: Use the build configuration values directly to ensure the APK name matches the installed app. 🏏🚀⚖️🏅
        val vName = android.defaultConfig.versionName
        
        val apkFile = file("${layout.buildDirectory.get().asFile}/outputs/apk/debug/app-debug.apk")
        
        if (apkFile.exists()) {
            // Automated Cleanup - Delete old APKs to prevent root clutter
            project.fileTree(rootProject.projectDir)
                .matching { include("cricscore_v*.apk") }
                .forEach { it.delete() }

            copy {
                from(apkFile)
                into(rootProject.projectDir)
                rename { "cricscore_v$vName.apk" }
            }
            println("APK copied to root: cricscore_v$vName.apk matching internal version $vName")
        }
    }
}
