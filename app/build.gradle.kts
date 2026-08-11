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

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val telegramClientId = projectConfig("telegram.clientId", "TELEGRAM_CLIENT_ID")
val telegramRedirectHost = projectConfig("telegram.redirectHost", "TELEGRAM_REDIRECT_HOST")
val telegramBackendUrl = projectConfig("telegram.backendUrl", "TELEGRAM_BACKEND_URL")
val missingTelegramConfiguration = buildList {
    if (telegramClientId.isBlank()) add("telegram.clientId / TELEGRAM_CLIENT_ID")
    if (telegramRedirectHost.isBlank()) add("telegram.redirectHost / TELEGRAM_REDIRECT_HOST")
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
        buildConfigField(
            "String",
            "TELEGRAM_REDIRECT_URI",
            "https://$telegramRedirectHost/tglogin".asBuildConfigString()
        )
        buildConfigField("String", "TELEGRAM_REDIRECT_HOST", telegramRedirectHost.asBuildConfigString())
        buildConfigField("String", "TELEGRAM_BACKEND_URL", telegramBackendUrl.asBuildConfigString())
        manifestPlaceholders["telegramRedirectHost"] = telegramRedirectHost
    }

    buildTypes {
        release {
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
