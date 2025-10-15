import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.android.application",
                "com.android.library",
                "com.android.test",
                "com.android.dynamic-feature" ->
                    useModule("com.android.tools.build:gradle:${requested.version}")
                "org.jetbrains.kotlin.android" ->
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            }
        }
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "JumpHeightRecorder"
include(":app")
