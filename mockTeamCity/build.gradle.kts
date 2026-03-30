plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(libs.kotlinx.coroutinesSwing)

    // Ktor server (CIO for daemon-thread-friendly shutdown)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverCio)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.serverCallLogging)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation(libs.logback)
}

compose.desktop {
    application {
        mainClass = "com.github.mr3zee.mockteamcity.MainKt"

        nativeDistributions {
            packageName = "MockTeamCity"
            packageVersion = "1.0.0"
        }
    }
}
