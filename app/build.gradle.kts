import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use(::load)
}

fun projectConfig(localKey: String, environmentKey: String): String =
    providers.environmentVariable(environmentKey).orNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: localProperties.getProperty(localKey)?.trim().orEmpty()

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val telegramClientId = projectConfig("telegram.clientId", "TELEGRAM_CLIENT_ID")
val telegramRedirectHost = projectConfig("telegram.redirectHost", "TELEGRAM_REDIRECT_HOST")
val telegramBackendUrl = projectConfig("telegram.backendUrl", "TELEGRAM_BACKEND_URL")
val appToken = projectConfig("app.token", "APP_TOKEN")
val missingTelegramConfiguration = buildList {
    if (telegramClientId.isBlank()) add("telegram.clientId / TELEGRAM_CLIENT_ID")
    if (telegramRedirectHost.isBlank()) add("telegram.redirectHost / TELEGRAM_REDIRECT_HOST")
    if (telegramBackendUrl.isBlank()) add("telegram.backendUrl / TELEGRAM_BACKEND_URL")
    if (appToken.isBlank()) add("app.token / APP_TOKEN")
}

require(missingTelegramConfiguration.isEmpty()) {
    "Telegram Login configuration is required. Missing: " +
        missingTelegramConfiguration.joinToString() +
        ". Copy local.properties.example values to the Git-ignored local.properties file or set the matching environment variables."
}

val signingStoreFile = providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: keystoreProperties.getProperty("storeFile")?.trim()?.takeIf(String::isNotEmpty)
val signingStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
    ?.takeIf(String::isNotEmpty)
    ?: keystoreProperties.getProperty("storePassword")?.takeIf(String::isNotEmpty)
val signingKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: keystoreProperties.getProperty("keyAlias")?.trim()?.takeIf(String::isNotEmpty)
val signingKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
    ?.takeIf(String::isNotEmpty)
    ?: keystoreProperties.getProperty("keyPassword")?.takeIf(String::isNotEmpty)
val releaseSigningValues = listOf(
    signingStoreFile,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword
)
require(releaseSigningValues.all { it == null } || releaseSigningValues.all { it != null }) {
    "Release signing is partially configured. Set ANDROID_KEYSTORE_FILE, " +
            "ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD together."
}
val releaseSigningConfigured = releaseSigningValues.all { it != null }

android {
    namespace = "com.markettwits.devx.tgsignin"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.markettwits.devx.tgsignin"
        minSdk = 24
        targetSdk = 37
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TELEGRAM_SDK_VERSION", "\"${libs.versions.loginSdk.get()}\"")
        buildConfigField("String", "TELEGRAM_CLIENT_ID", telegramClientId.asBuildConfigString())
        buildConfigField("String", "TELEGRAM_BACKEND_URL", telegramBackendUrl.asBuildConfigString())
        buildConfigField("String", "APP_TOKEN", appToken.asBuildConfigString())
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(checkNotNull(signingStoreFile))
                storePassword = checkNotNull(signingStorePassword)
                keyAlias = checkNotNull(signingKeyAlias)
                keyPassword = checkNotNull(signingKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "TELEGRAM_REDIRECT_URI",
                "https://$telegramRedirectHost/tglogin".asBuildConfigString()
            )
            buildConfigField(
                "String",
                "TELEGRAM_REDIRECT_HOST",
                telegramRedirectHost.asBuildConfigString()
            )
            manifestPlaceholders["telegramRedirectHost"] = telegramRedirectHost
        }
        release {
            buildConfigField(
                "String",
                "TELEGRAM_REDIRECT_URI",
                "https://$telegramRedirectHost/tglogin".asBuildConfigString()
            )
            buildConfigField(
                "String",
                "TELEGRAM_REDIRECT_HOST",
                telegramRedirectHost.asBuildConfigString()
            )
            manifestPlaceholders["telegramRedirectHost"] = telegramRedirectHost
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
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

val apkBaseName = "TelegramLoginAndroidDemo"
val outputFileNameGetter = "getOutputFileName"
val unknownBuildType = "unknown"
val unknownVersionName = "unspecified"

extensions.configure<ApplicationAndroidComponentsExtension>("androidComponents") {
    onVariants { variant ->
        val versionName = variant.outputs.singleOrNull()?.versionName?.orNull ?: unknownVersionName
        val buildType = variant.buildType ?: unknownBuildType
        val apkName = "$apkBaseName-$buildType-v$versionName.apk"

        variant.outputs.forEach { output ->
            @Suppress("UNCHECKED_CAST")
            val outputFileNameProperty = output.javaClass.methods
                .firstOrNull { method -> method.name == outputFileNameGetter }
                ?.invoke(output) as? org.gradle.api.provider.Property<String>

            outputFileNameProperty?.set(apkName)
        }
    }
}

dependencies {
    implementation(libs.login.sdk)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.lottie.compose)
    implementation(libs.libphonenumber)
    implementation(libs.androidx.dataStore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
