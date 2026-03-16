plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
}

group = "com.github.mr3zee"
version = "1.0.0"
application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)

    // Ktor server
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverConfigYaml)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.ktor.serverAuth)
    implementation(libs.ktor.serverSessions)
    implementation(libs.ktor.serverCallLogging)
    implementation(libs.ktor.serverWebsockets)
    implementation(libs.ktor.serverDefaultHeaders)
    implementation(libs.ktor.serverRateLimit)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.serializationKotlinxJson)

    // Ktor client (for outbound API calls to external services)
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientContentNegotiation)
    implementation(libs.ktor.clientSerializationKotlinxJson)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.json)
    implementation(libs.exposed.kotlinDatetime)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    // Security
    implementation(libs.argon2)

    // Scheduling
    implementation(libs.cronUtils)

    // YAML parsing (for GitHub Actions workflow file input discovery)
    implementation(libs.snakeyaml)

    // DI
    implementation(libs.koin.ktor)
    implementation(libs.koin.loggerSlf4j)

    // Test
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.ktor.clientContentNegotiation)
    testImplementation(libs.ktor.clientSerializationKotlinxJson)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.koin.test)
    testImplementation(libs.ktor.clientWebsockets)
    testImplementation(libs.ktor.clientMock)
    testImplementation(libs.h2)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
}

sourceSets {
    create("integrationTest") {
        kotlin.srcDir("src/integrationTest/kotlin")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
    }
}

configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

tasks.register<Test>("integrationTest") {
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnit()
}
