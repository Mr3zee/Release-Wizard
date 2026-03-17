rootProject.name = "ReleaseWizard"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // ngrok binary download — Ivy repo mapping to ngrok's CDN
        // URL pattern: https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-<platform>-<arch>.zip
        ivy {
            name = "ngrok-cdn"
            url = uri("https://bin.equinox.io/c/bNyj1mQVY4c")
            patternLayout {
                artifact("[module]-v3-stable-[classifier].[ext]")
            }
            metadataSources { artifact() }
            content { includeGroup("ngrok") }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")
include(":server")
include(":shared")