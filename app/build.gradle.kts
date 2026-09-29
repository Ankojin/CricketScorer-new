import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "in.nrkmart.cricscore"
    compileSdk = 37

    val versionPropsFile = rootProject.file("version.properties")
    val versionProps = Properties()
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { stream ->
            versionProps.load(stream)
        }
    }
    
    val vCode = versionProps.getProperty("VERSION_CODE", "273").toInt()
    val vName = versionProps.getProperty("VERSION_NAME", "2.33.38")

    defaultConfig {
        applicationId = "in.nrkmart.cricscore"
        minSdk = 24
        targetSdk = 36
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

    lint {
        checkReleaseBuilds = false
        abortOnError = false
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
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
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
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}



tasks.register("verifyVersionProperties") {
    doFirst {
        val versionPropsFile = rootProject.file("version.properties")
        if (versionPropsFile.exists()) {
            val props = Properties()
            versionPropsFile.inputStream().use { props.load(it) }
            val code = props.getProperty("VERSION_CODE", "Unknown")
            val name = props.getProperty("VERSION_NAME", "Unknown")
            println("\n=======================================================")
            println("🚀 [Gradle Pre-Build Version Check]")
            println("   Reading fresh version.properties before build:")
            println("   ➜ VERSION_CODE = $code")
            println("   ➜ VERSION_NAME = $name")
            println("=======================================================\n")
        }
    }
}

tasks.configureEach {
    val isBuildTask = name.startsWith("assemble") || name.startsWith("bundle")
    if (isBuildTask && !name.contains("Test")) {
        dependsOn("verifyVersionProperties")
        finalizedBy("copyApkToRoot")
    }
}

tasks.register("copyApkToRoot") {
    doLast {
        val vName = android.defaultConfig.versionName
        val buildDir = layout.buildDirectory.get().asFile
        
        val releaseApk = file("$buildDir/outputs/apk/release/app-release.apk")
        val debugApk = file("$buildDir/outputs/apk/debug/app-debug.apk")
        val releaseAab = file("$buildDir/outputs/bundle/release/app-release.aab")
        
        val apkFile = if (releaseApk.exists()) releaseApk else debugApk
        
        if (apkFile.exists()) {
            val isRelease = apkFile == releaseApk
            val suffix = if (isRelease) "" else "-debug"
            
            // Automated Cleanup
            project.fileTree(rootProject.projectDir)
                .matching { include("cricscore_v*.apk", "cricscore_v*.aab", "cricleague_v*.apk", "cricleague_v*.aab") }
                .forEach { it.delete() }

            copy {
                from(apkFile)
                into(rootProject.projectDir)
                rename { "cricleague_v$vName$suffix.apk" }
            }
            println("APK copied to root: cricleague_v$vName$suffix.apk (Source: ${if (isRelease) "Release" else "Debug"})")
        }

        if (releaseAab.exists()) {
            copy {
                from(releaseAab)
                into(rootProject.projectDir)
                rename { "cricleague_v$vName.aab" }
            }
            println("AAB copied to root: cricleague_v$vName.aab (Source: Release Bundle)")
        }
    }
}
