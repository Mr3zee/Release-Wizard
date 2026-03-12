# Build and Dependencies

## Required conventions

- Use Gradle Kotlin DSL (`build.gradle.kts`).
- Use version catalogs only — `libs` from `gradle/libs.versions.toml`.
- Never hardcode dependency versions or coordinates in module build files.

## Server build file

```kotlin
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
}

application { mainClass.set("com.github.mr3zee.ApplicationKt") }

dependencies {
    implementation(projects.shared)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.koin.ktor)
    implementation(libs.koin.loggerSlf4j)
    implementation(libs.logback)

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.koin.test)
    testImplementation(libs.kotlin.testJunit)
}
```

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
