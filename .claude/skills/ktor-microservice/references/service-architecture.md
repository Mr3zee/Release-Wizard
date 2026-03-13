# Service Architecture

Use layered architecture with Koin dependency injection in the server module.

## Module structure

The server package (`server/src/main/kotlin/com/github/mr3zee/`) contains:

- `Application.kt`: entrypoint, Ktor plugin setup, and Koin installation
- `Config.kt`: plain data classes + `ApplicationConfig.databaseConfig()` extension for reading from YAML
- `<Feature>Module.kt`: Koin module definitions per feature
- `Routes.kt`: HTTP route definitions
- `Service.kt`: business logic layer
- `Repository.kt`: data access layer (Exposed)

Keep the boundary explicit:

```
Routes -> Service -> Repository -> Exposed
                  -> Client -> Ktor HttpClient
```

## App bootstrap pattern

The server uses **`EngineMain`** as the entry point (configured in `build.gradle.kts`). Ktor reads `application.yaml` automatically and calls the module function specified in `ktor.application.modules`.

Install Koin via `install(Koin)` inside the module function:

```kotlin
// Application.kt — no main(), EngineMain handles startup
fun Application.module() {
    val dbConfig = environment.config.databaseConfig()

    install(Koin) {
        slf4jLogger()
        modules(appModule(dbConfig), projectsModule /*, releasesModule, connectionsModule */)
    }
    configureRouting()
}
```

Routes inject services using `val service by inject<ProjectsService>()` from the Ktor `Route`/`Application` scope.

**Koin version requirement**: Use **Koin 4.1.1+** for Ktor 3.x compatibility. Koin 4.0.x targets Ktor 2.x and will fail at runtime with `NoClassDefFoundError: io/ktor/server/routing/RoutingKt`.

## Dependency wiring with Koin modules

Organize Koin modules by feature. Each feature defines its own `org.koin.dsl.module { }`.

**Shared infrastructure module** — database, HTTP client, config:

```kotlin
fun appModule(dbConfig: DatabaseConfig) = module {
    single { dbConfig }
    single<DataSource> { dataSource(get()) }
    single<Database> { initDatabase(get()) }
}
```

**Feature Koin modules** — one per domain feature:

```kotlin
val projectsModule = module {
    single<ProjectsRepository> { ExposedProjectsRepository(get()) }  // get() resolves Database
    single<ProjectsService> { DefaultProjectsService(get()) }
}
```

Rules:
- Shared infrastructure (data source, HTTP client, config) is defined **once** in `appModule`.
- Feature modules declare only their own repository, client, and service bindings.
- Bind by interface (`single<ProjectsService> { DefaultProjectsService(...) }`) to allow test overrides.
- **Repositories receive `Database` via constructor** — never rely on Exposed's global state.
- When a service needs capabilities from multiple repositories, inject those repositories separately
  instead of repository-interface inheritance.
- Route handlers inject services, never repositories directly.

## Injecting in routes

Use Koin's Ktor integration to inject dependencies in route handlers:

```kotlin
fun Application.configureRouting() {
    val releasesService by inject<ReleasesService>()

    routing {
        releaseRoutes(releasesService)
    }
}
```

Or inject directly in route extensions:

```kotlin
fun Route.releaseRoutes() {
    val service by inject<ReleasesService>()

    route("/releases") {
        get { ... }
        post { ... }
    }
}
```

## Configuration pattern

- Server uses **`application.yaml`** + **`EngineMain`** (Ktor handles host/port from `ktor.deployment.*`).
- Custom config (e.g. database) lives under `app.*` in the YAML and is read via `environment.config` extension functions.
- Config data classes are **plain** (not `@Serializable`). Extension functions on `ApplicationConfig` extract them.
- The extracted config is passed to `appModule(dbConfig)` which registers it as a Koin singleton.

```yaml
# application.yaml
ktor:
  deployment:
    port: 8080
    host: 0.0.0.0
  application:
    modules:
      - com.github.mr3zee.ApplicationKt.module

app:
  database:
    url: "jdbc:postgresql://localhost:5432/release_wizard"
    user: "postgres"
    password: "postgres"
    driver: "org.postgresql.Driver"
```

```kotlin
// Config.kt
data class DatabaseConfig(val url: String, val user: String, val password: String, val driver: String)

fun ApplicationConfig.databaseConfig(): DatabaseConfig {
    return DatabaseConfig(
        url = property("app.database.url").getString(),
        user = property("app.database.user").getString(),
        password = property("app.database.password").getString(),
        driver = property("app.database.driver").getString(),
    )
}

// Application.kt
fun Application.module() {
    val dbConfig = environment.config.databaseConfig()
    install(Koin) {
        modules(appModule(dbConfig), projectsModule)
    }
    ...
}
```

For tests, pass `DatabaseConfig` directly to `appModule()` — no YAML needed.

## Persistence (Exposed 1.x)

- **Package migration**: Exposed 1.x uses `org.jetbrains.exposed.v1.*` packages (NOT `org.jetbrains.exposed.sql.*`):
  - Core: `org.jetbrains.exposed.v1.core.*` (Table, Column, ResultRow, SortOrder, `eq`)
  - JDBC: `org.jetbrains.exposed.v1.jdbc.*` (Database, SchemaUtils, insert, update, deleteWhere, selectAll)
  - Transactions: `org.jetbrains.exposed.v1.jdbc.transactions.transaction`, `...transactions.experimental.newSuspendedTransaction`
  - UUID tables: `org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable` (for `java.util.UUID`)
  - JSON: `org.jetbrains.exposed.v1.json.jsonb`
  - Datetime: `org.jetbrains.exposed.v1.datetime.timestamp`
- Use Exposed with JDBC (blocking) wrapped in `newSuspendedTransaction(Dispatchers.IO, db)`.
- **Never use bare `transaction { }`** in suspend functions — it blocks coroutine threads.
- Repositories accept `Database` via constructor and pass it to `newSuspendedTransaction`.
- Use a `dbQuery` helper in repositories:

```kotlin
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction

class ExposedProjectsRepository(private val db: Database) : ProjectsRepository {
    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, db) { block() }

    override suspend fun findAll(): List<Project> = dbQuery {
        ProjectTable.selectAll().map { it.toProject() }
    }
}
```

### kotlinx-datetime 0.7.x compatibility

- Domain models use `import kotlinx.datetime.Instant` (typealias to `kotlin.time.Instant`)
- For `Clock.System.now()`, always use `import kotlin.time.Clock` — `kotlinx.datetime.Clock.System` was removed in 0.7.x
- Exposed's `timestamp()` column returns `kotlinx.datetime.Instant` which is compatible via typealias

## JSON Configuration

Use the shared `AppJson` instance from `shared/.../JsonConfig.kt` everywhere (server, client, persistence). Do not create separate `Json { }` instances.

## Coroutines — Structured Concurrency (MANDATORY)

All coroutine usage in services and repositories **must** follow structured concurrency.

### In service/repository methods

Ktor route handlers run inside a coroutine context. Service and repository methods should be `suspend` functions. When you need parallel child coroutines, use the lexical `coroutineScope { }` builder:

```kotlin
class DefaultReleasesService(
    private val releasesRepo: ReleasesRepository,
    private val blocksRepo: BlocksRepository,
) : ReleasesService {
    override suspend fun getReleaseWithBlocks(id: ReleaseId): ReleaseWithBlocks = coroutineScope {
        val release = async { releasesRepo.getById(id) }
        val blocks = async { blocksRepo.listByRelease(id) }
        ReleaseWithBlocks(release.await(), blocks.await())
    }
}
```

### Anti-patterns

```kotlin
// BAD: stray scope — leaks coroutines, breaks cancellation on shutdown
class BadService {
    private val scope = CoroutineScope(Dispatchers.IO)
    fun doWork() { scope.launch { /* ... */ } }
}

// BAD: stray Job — no parent, breaks structured concurrency
class BadService2 {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
}

// BAD: GlobalScope — never
fun fireAndForget() { GlobalScope.launch { /* ... */ } }

// BAD: bare transaction {} in suspend function — blocks coroutine thread
suspend fun findAll() = transaction { ... }
```
