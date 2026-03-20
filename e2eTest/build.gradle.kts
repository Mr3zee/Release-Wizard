plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    // ComposeApp (JVM variant from KMP)
    testImplementation(project(":composeApp"))

    // Server + shared test infra (testModule, testDbConfig, StubBlockExecutor, etc.)
    testImplementation(project(":server"))
    testImplementation(testFixtures(project(":server")))

    // Shared module (domain model types)
    testImplementation(project(":shared"))

    // Compose UI test framework
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
    testImplementation(compose.desktop.currentOs)

    // Embedded server engines
    testImplementation(libs.ktor.serverCio)
    testImplementation(libs.ktor.serverNetty)

    // CIO client engine (composeApp's implementation deps aren't transitive)
    testImplementation(libs.ktor.clientCio)

    // Ktor client content negotiation + JSON serialization (for direct HTTP client)
    testImplementation(libs.ktor.clientContentNegotiation)
    testImplementation(libs.ktor.clientSerializationKotlinxJson)

    // Swing dispatcher for Compose Desktop
    testImplementation(libs.kotlinx.coroutinesSwing)

    // H2 in-memory database
    testImplementation(libs.h2)

    // Test framework
    testImplementation(libs.kotlin.testJunit)
}

tasks.test {
    useJUnit()
    // Disable Ktor's shutdown hook — tests manage server lifecycle
    systemProperty("io.ktor.server.engine.ShutdownHook", "false")
    // Run AWT in headless mode — prevents macOS focus stealing (no visible window)
    systemProperty("java.awt.headless", "true")
    // Fork per test class — PlatformUrl.jvm.kt uses `lazy val` cached per JVM
    forkEvery = 1
}
