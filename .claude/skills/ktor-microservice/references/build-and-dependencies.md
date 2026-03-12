# Build and Dependencies

## Required conventions

- Use Gradle Kotlin DSL (`build.gradle.kts`).
- Use version catalogs only — `libs` from `gradle/libs.versions.toml`.
- Never hardcode dependency versions or coordinates in module build files.

## Server build file

```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
}

application { mainClass.set("io.ktor.server.netty.EngineMain") }

dependencies {
    implementation(projects.shared)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverConfigYaml)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.json)
    implementation(libs.exposed.kotlinDatetime)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.koin.ktor)
    implementation(libs.koin.loggerSlf4j)
    implementation(libs.logback)

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.ktor.clientContentNegotiation)
    testImplementation(libs.ktor.clientSerializationKotlinxJson)
    testImplementation(libs.koin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.h2)
}
```

## Key version constraints

- **Koin 4.1.1+** required for Ktor 3.x. Koin 4.0.x targets Ktor 2.x and fails at runtime.
- **Exposed 1.1.1** — uses `org.jetbrains.exposed.v1.*` packages (not the old `org.jetbrains.exposed.sql.*`). See `service-architecture.md` for the full import mapping.
- **kotlinx-datetime 0.7.1** — `kotlinx.datetime.Instant` is a typealias to `kotlin.time.Instant`. Use `kotlin.time.Clock` for `Clock.System.now()` (the old `kotlinx.datetime.Clock.System` was removed).
- **kotlinx-serialization 1.9.0** — Compose Multiplatform and Ktor transitively pull this version; declare it explicitly to avoid version drift.
- Server tests need `ktor-client-content-negotiation` and `ktor-serialization-kotlinx-json` as **test** dependencies for the test client JSON serialization.

## Adding dependencies

1. Add the version to `[versions]` in `gradle/libs.versions.toml`.
2. Add the library to `[libraries]` using `version.ref`.
3. Reference it in `build.gradle.kts` as `libs.<alias>`.

## Module registration

- Add `include(":module")` in `settings.gradle.kts`.

## Build and test commands

- Build one module: `./gradlew :<module>:build`
- Run module tests: `./gradlew :<module>:test`
- Run selected tests:
  - `./gradlew :<module>:cleanTest :<module>:test --tests "com.github.mr3zee.SomeTest" --no-build-cache`
