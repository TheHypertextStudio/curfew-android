import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
    id("com.github.jk1.dependency-license-report")
}

android {
    namespace = "studio.hypertext.curfew"
    compileSdk = 37

    defaultConfig {
        applicationId = "studio.hypertext.curfew"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseStoreFile = providers.environmentVariable("CURFEW_ANDROID_KEYSTORE").orNull
    val releaseStorePassword = providers.environmentVariable("CURFEW_ANDROID_STORE_PASSWORD").orNull
    val releaseKeyAlias = providers.environmentVariable("CURFEW_ANDROID_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("CURFEW_ANDROID_KEY_PASSWORD").orNull
    val hasReleaseSigning = listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { !it.isNullOrBlank() }
    signingConfigs {
        create("curfewRelease") {
            if (hasReleaseSigning) {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("plain") {
            dimension = "distribution"
        }
        create("gms") {
            dimension = "distribution"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("curfewRelease")
            } else {
                // Local/CI release artifacts remain installable while production injects the
                // single protected Curfew signing lineage shared by both distributions.
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.browser)
    implementation(libs.okhttp)
    implementation("studio.hypertext.curfew:curfew-protocols:0.2.0")
    ksp(libs.androidx.room.compiler)
    "gmsImplementation"(libs.firebase.messaging)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

room {
    schemaDirectory("$projectDir/schemas")
}

licenseReport {
    configurations = arrayOf("plainReleaseRuntimeClasspath", "gmsReleaseRuntimeClasspath")
    allowedLicensesFile = layout.projectDirectory.file("../config/allowed-licenses.json")
    excludeOwnGroup = true
}

tasks.register("verifyPlainRuntimeIsolation") {
    group = "verification"
    description = "Rejects Google Play Services and Firebase from the plain runtime graph."
    doLast {
        val forbiddenPrefixes = listOf("com.google.android.gms", "com.google.firebase")
        val forbidden = configurations.getByName("plainReleaseRuntimeClasspath")
            .resolvedConfiguration.resolvedArtifacts
            .map { "${it.moduleVersion.id.group}:${it.name}:${it.moduleVersion.id.version}" }
            .filter { coordinate -> forbiddenPrefixes.any { coordinate.startsWith(it) } }
        check(forbidden.isEmpty()) {
            "plain runtime includes forbidden Google dependencies: ${forbidden.joinToString()}"
        }
    }
}

tasks.register("generateCurfewSbom") {
    group = "distribution"
    description = "Writes a CycloneDX-compatible component inventory for both release variants."
    val output = layout.buildDirectory.file("reports/sbom/curfew-android.cdx.json")
    outputs.file(output)
    doLast {
        val configurations = listOf("plainReleaseRuntimeClasspath", "gmsReleaseRuntimeClasspath")
        val components = configurations.flatMap { configurationName ->
            project.configurations.getByName(configurationName)
                .resolvedConfiguration.resolvedArtifacts
                .map {
                    Triple(it.moduleVersion.id.group, it.name, it.moduleVersion.id.version)
                }
        }.distinct().sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
        val componentJson = components.joinToString(",\n") { (group, name, version) ->
            """    {"type":"library","group":"$group","name":"$name","version":"$version","purl":"pkg:maven/$group/$name@$version"}"""
        }
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                """{"bomFormat":"CycloneDX","specVersion":"1.6","version":1,"metadata":{"component":{"type":"application","group":"studio.hypertext.curfew","name":"curfew-android","version":"${android.defaultConfig.versionName}"}},"components":[\n$componentJson\n]}\n""",
            )
        }
    }
}

tasks.register("writeReleaseChecksums") {
    group = "distribution"
    description = "Writes SHA-256 checksums for signed APKs and bundles."
    dependsOn("assemblePlainRelease", "assembleGmsRelease", "bundleGmsRelease")
    val output = layout.buildDirectory.file("reports/checksums/SHA256SUMS")
    outputs.file(output)
    doLast {
        val artifacts = fileTree(layout.buildDirectory.dir("outputs")) {
            include("**/*.apk", "**/*.aab")
        }.files.sortedBy { it.absolutePath }
        check(artifacts.isNotEmpty()) { "no release artifacts were produced" }
        val lines = artifacts.joinToString("\n") { artifact ->
            val digest = MessageDigest.getInstance("SHA-256").digest(artifact.readBytes())
                .joinToString("") { "%02x".format(it) }
            "$digest  ${artifact.relativeTo(projectDir).path}"
        }
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText("$lines\n")
        }
    }
}

tasks.register("writeBuildProvenance") {
    group = "distribution"
    description = "Records reproducible build inputs without embedding secrets."
    val output = layout.buildDirectory.file("reports/provenance/curfew-android.json")
    outputs.file(output)
    doLast {
        val sourceRevision = providers.environmentVariable("GITHUB_SHA").orNull ?: "local"
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                """{"schemaVersion":1,"applicationId":"studio.hypertext.curfew","versionName":"${android.defaultConfig.versionName}","sourceRevision":"$sourceRevision","protocolRevision":"1f7d8f60d1bbe21e5ec352456ecb19aca23f4dee","compileSdk":37,"targetSdk":36,"variants":["plainRelease","gmsRelease"]}\n""",
            )
        }
    }
}
