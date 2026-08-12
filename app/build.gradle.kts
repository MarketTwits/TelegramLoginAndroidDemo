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

fun projectConfig(localKey: String, environmentKey: String): String =
    providers.environmentVariable(environmentKey).orNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: localProperties.getProperty(localKey)?.trim().orEmpty()

fun projectConfig(
    localKey: String,
    environmentKey: String,
    fallbackLocalKey: String,
    fallbackEnvironmentKey: String
): String = projectConfig(localKey, environmentKey)
    .ifBlank { projectConfig(fallbackLocalKey, fallbackEnvironmentKey) }

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val telegramClientId = projectConfig("telegram.clientId", "TELEGRAM_CLIENT_ID")
val telegramDebugRedirectHost = projectConfig(
    localKey = "telegram.redirectHost.debug",
    environmentKey = "TELEGRAM_REDIRECT_HOST_DEBUG",
    fallbackLocalKey = "telegram.redirectHost",
    fallbackEnvironmentKey = "TELEGRAM_REDIRECT_HOST"
)
val telegramReleaseRedirectHost = projectConfig(
    localKey = "telegram.redirectHost.release",
    environmentKey = "TELEGRAM_REDIRECT_HOST_RELEASE",
    fallbackLocalKey = "telegram.redirectHost",
    fallbackEnvironmentKey = "TELEGRAM_REDIRECT_HOST"
)
val telegramBackendUrl = projectConfig("telegram.backendUrl", "TELEGRAM_BACKEND_URL")
val missingTelegramConfiguration = buildList {
    if (telegramClientId.isBlank()) add("telegram.clientId / TELEGRAM_CLIENT_ID")
    if (telegramDebugRedirectHost.isBlank()) {
        add("telegram.redirectHost.debug / TELEGRAM_REDIRECT_HOST_DEBUG")
    }
    if (telegramReleaseRedirectHost.isBlank()) {
        add("telegram.redirectHost.release / TELEGRAM_REDIRECT_HOST_RELEASE")
    }
    if (telegramBackendUrl.isBlank()) add("telegram.backendUrl / TELEGRAM_BACKEND_URL")
}

require(missingTelegramConfiguration.isEmpty()) {
    "Telegram Login configuration is required. Missing: " +
        missingTelegramConfiguration.joinToString() +
        ". Copy local.properties.example values to the Git-ignored local.properties file or set the matching environment variables."
}

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
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TELEGRAM_SDK_VERSION", "\"${libs.versions.loginSdk.get()}\"")
        buildConfigField("String", "TELEGRAM_CLIENT_ID", telegramClientId.asBuildConfigString())
        buildConfigField("String", "TELEGRAM_BACKEND_URL", telegramBackendUrl.asBuildConfigString())
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "TELEGRAM_REDIRECT_URI",
                "https://$telegramDebugRedirectHost/tglogin".asBuildConfigString()
            )
            buildConfigField(
                "String",
                "TELEGRAM_REDIRECT_HOST",
                telegramDebugRedirectHost.asBuildConfigString()
            )
            manifestPlaceholders["telegramRedirectHost"] = telegramDebugRedirectHost
        }
        release {
            buildConfigField(
                "String",
                "TELEGRAM_REDIRECT_URI",
                "https://$telegramReleaseRedirectHost/tglogin".asBuildConfigString()
            )
            buildConfigField(
                "String",
                "TELEGRAM_REDIRECT_HOST",
                telegramReleaseRedirectHost.asBuildConfigString()
            )
            manifestPlaceholders["telegramRedirectHost"] = telegramReleaseRedirectHost
            optimization {
                enable = false
            }
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

val apkBaseName = "TelegramLoginDemo"
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
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
