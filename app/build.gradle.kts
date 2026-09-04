import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
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
    val vName = "2.26.$vCode"

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
        // v2.26.47: Safety check - only increment if this is a real build, not a sync
        val isSync = project.hasProperty("android.injected.build.model.only") || 
                    gradle.startParameter.taskNames.isEmpty()
        if (isSync) return@doLast

        val versionPropsFile = rootProject.file("version.properties")
        if (versionPropsFile.exists()) {
            val versionProps = Properties()
            versionPropsFile.inputStream().use { versionProps.load(it) }
            val currentVCode = versionProps.getProperty("VERSION_CODE", "40").toInt()
            versionProps.setProperty("VERSION_CODE", (currentVCode + 1).toString())
            versionPropsFile.outputStream().use { versionProps.store(it, null) }
            println("Version code incremented to: ${currentVCode + 1}")
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
        // v2.26.49: Fetch EXACT version from the build configuration to prevent 1-off mismatch 🏏🚀⚖️🏅
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
