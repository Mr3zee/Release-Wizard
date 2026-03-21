plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.jib)
    application
    `java-test-fixtures`
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
    implementation(libs.ktor.serverCompression)
    implementation(libs.ktor.serverCachingHeaders)
    implementation(libs.ktor.serializationKotlinxJson)

    // Ktor client (for outbound API calls to external services)
    implementation(libs.ktor.clientCore)
    implementation(libs.ktor.clientCio)
    implementation(libs.ktor.clientContentNegotiation)
    implementation(libs.ktor.clientSerializationKotlinxJson)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.migration.jdbc)
    implementation(libs.exposed.json)
    implementation(libs.exposed.kotlinDatetime)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    // Security
    implementation(libs.argon2)

    // Scheduling
    implementation(libs.cronUtils)

    // YAML parsing (for GitHub Actions workflow file input discovery)
    implementation(libs.snakeyaml)

    // DI
    implementation(libs.koin.ktor)
    implementation(libs.koin.loggerSlf4j)

    // Test fixtures (shared between test, integrationTest, and e2eTest module)
    testFixturesImplementation(libs.ktor.serverTestHost)
    testFixturesImplementation(libs.ktor.clientContentNegotiation)
    testFixturesImplementation(libs.ktor.clientSerializationKotlinxJson)
    testFixturesImplementation(libs.koin.test)
    testFixturesImplementation(libs.ktor.clientWebsockets)
    testFixturesImplementation(libs.ktor.clientMock)
    testFixturesImplementation(libs.h2)
    testFixturesImplementation(libs.kotlin.testJunit)

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
    testImplementation(libs.exposed.migration.jdbc)
}

sourceSets {
    create("integrationTest") {
        kotlin.srcDir("src/integrationTest/kotlin")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output + sourceSets["testFixtures"].output
        runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output + sourceSets["testFixtures"].output
    }
}

// testFixtures needs to see main's implementation deps (Ktor plugins, Koin, shared module, etc.)
configurations["testFixturesImplementation"].extendsFrom(configurations["implementation"])

configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

dependencies {
    // CIO server engine for integration tests — daemon threads, clean shutdown (unlike Netty)
    "integrationTestImplementation"(libs.ktor.serverCio)
}

// --- ngrok binary download via Gradle dependency resolution ---

// Determine OS + arch classifier for the ngrok binary
val ngrokClassifier: String by lazy {
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val platform = when {
        osName.contains("mac") || osName.contains("darwin") -> "darwin"
        osName.contains("linux") -> "linux"
        osName.contains("win") -> "windows"
        else -> error("Unsupported OS for ngrok: $osName")
    }
    val arch = when {
        osArch.contains("aarch64") || osArch.contains("arm64") -> "arm64"
        osArch.contains("amd64") || osArch.contains("x86_64") || osArch == "x64" -> "amd64"
        else -> error("Unsupported arch for ngrok: $osArch")
    }
    "$platform-$arch"
}

val ngrokBinaryName: String by lazy {
    if (System.getProperty("os.name").lowercase().contains("win")) "ngrok.exe" else "ngrok"
}

// Ngrok binary resolved as a Gradle dependency via the Ivy repo defined in settings.gradle.kts
val ngrok by configurations.creating {
    isTransitive = false
}

dependencies {
    ngrok("ngrok:ngrok:v3-stable:$ngrokClassifier@zip")
}

// Extract ngrok binary from the resolved zip
val extractNgrok = tasks.register<Copy>("extractNgrok") {
    from(zipTree(ngrok.singleFile))
    into(layout.buildDirectory.dir("ngrok"))
    // Make the binary executable on Unix
    filePermissions {
        unix("rwxr-xr-x")
    }
}

// --- Frontend bundling & Jib container image ---

// Frontend build output directory (classpath: static/*)
val frontendBuildDir = layout.buildDirectory.dir("generated-resources/static")

// Build WasmJS frontend and stage in build directory (not source tree, to avoid stale files in :server:run)
val copyFrontend = tasks.register<Sync>("copyFrontendToResources") {
    dependsOn(":composeApp:wasmJsBrowserDistribution")
    from(rootProject.layout.projectDirectory.dir("composeApp/build/dist/wasmJs/productionExecutable"))
    into(frontendBuildDir)
}

jib {
    from {
        image = "amazoncorretto:21"
    }
    to {
        image = "release-wizard"
        tags = setOf(version.toString(), "latest")
    }
    container {
        mainClass = "io.ktor.server.netty.EngineMain"
        ports = listOf("8080")
        user = "65534" // nobody — non-root, matches Console default runAsUser
        creationTime.set("USE_CURRENT_TIMESTAMP")
        jvmFlags = listOf(
            "-XX:MaxRAMPercentage=75.0",
            "-XX:InitialRAMPercentage=50.0",
        )
    }
    // Include frontend build output on the container classpath
    extraDirectories {
        paths {
            path {
                setFrom(layout.buildDirectory.dir("generated-resources"))
                into = "/app/resources"
            }
        }
    }
}

// runApp = bundled local mode (builds frontend, then runs server)
tasks.register<JavaExec>("runApp") {
    dependsOn(copyFrontend, "classes")
    classpath = sourceSets["main"].runtimeClasspath + files(layout.buildDirectory.dir("generated-resources"))
    mainClass.set("io.ktor.server.netty.EngineMain")
    jvmArgs = application.applicationDefaultJvmArgs.toList()
}

// Jib tasks also need frontend
tasks.named("jib") { dependsOn(copyFrontend) }
tasks.named("jibDockerBuild") { dependsOn(copyFrontend) }
tasks.named("jibBuildTar") { dependsOn(copyFrontend) }

// --- Integration tests ---

tasks.register<Test>("integrationTest") {
    dependsOn(extractNgrok)
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnit()

    // Pass the ngrok binary path to the test runner
    val ngrokPath = layout.buildDirectory.dir("ngrok").map { it.file(ngrokBinaryName).asFile.absolutePath }
    systemProperty("ngrok.path", ngrokPath.get())

    // Disable Ktor's JVM shutdown hook — tests manage server lifecycle explicitly in @After
    systemProperty("io.ktor.server.engine.ShutdownHook", "false")

    // Fork a new JVM per test class — ensures clean shutdown
    forkEvery = 1
}
