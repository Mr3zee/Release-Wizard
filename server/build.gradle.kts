plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kxrpc)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "io.github.mr3zee.rwizard"
version = "1.0.0"
application {
    mainClass.set("io.github.mr3zee.rwizard.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    
    // Exposed
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.json)
    
    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    
    // Ktor Client for integrations
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation("io.ktor:ktor-client-content-negotiation:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${libs.versions.ktor.get()}")
    
    // Kotlinx libraries
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    
    // Database
    implementation("com.h2database:h2:2.2.220")
    
    // Security
    implementation("io.ktor:ktor-server-auth-jvm:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-websockets-jvm:${libs.versions.ktor.get()}")
    implementation("org.mindrot:jbcrypt:0.4")

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.koin.test)
}

// Custom task to compile wasm frontend and prepare it for serving statically
val prepareWebApp = tasks.register<Copy>("prepareWebApp") {
    description = "Compiles wasm frontend in production mode and copies it to server's resources"
    group = "distribution"

    val composeApp = project.rootProject.project("composeApp")

    // Depend on the wasm frontend production build task
    dependsOn(composeApp.tasks.named("wasmJsBrowserDistribution"))

    // Define the task inputs and outputs for better caching
    val sourceDir = composeApp.layout.buildDirectory.dir("dist/wasmJs/productionExecutable")
    val targetDir = project.layout.projectDirectory.dir("src/main/resources/static")
    
    // Configure the copy task
    from(sourceDir)
    into(targetDir)
    
    // Log after copying
    doLast {
        logger.lifecycle("Wasm frontend files copied to ${targetDir.asFile.absolutePath}")
    }
}

// Task to run the web app (prepare + run server)
tasks.register("runWebApp") {
    description = "Compiles wasm frontend in production mode and serves it statically on root"
    group = "application"
    
    // Just depend on the run task, which now depends on prepareWebApp
    dependsOn(prepareWebApp)
}
