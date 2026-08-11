pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    val localProperties = java.util.Properties().apply {
        val file = settingsDir.resolve("local.properties")
        if (file.isFile) file.inputStream().use(::load)
    }
    val githubUsername = System.getenv("GITHUB_USERNAME")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: localProperties.getProperty("gpr.user")?.trim()?.takeIf(String::isNotEmpty)
    val githubToken = System.getenv("GITHUB_TOKEN")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: localProperties.getProperty("gpr.key")?.trim()?.takeIf(String::isNotEmpty)

    require(githubUsername != null && githubToken != null) {
        "Telegram Login SDK requires GitHub Packages credentials. Set gpr.user and gpr.key " +
            "in the Git-ignored local.properties file, or set GITHUB_USERNAME and GITHUB_TOKEN."
    }

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/TelegramMessenger/telegram-login-android")
            credentials {
                username = githubUsername
                password = githubToken
            }
        }
    }
}

rootProject.name = "TelegramLoginAndroidDemo"
include(":app")
