# Service Architecture

Use layered architecture with Koin dependency injection in the server module.

## Module structure

The server package (`server/src/main/kotlin/com/github/mr3zee/`) contains:

- `Application.kt`: entrypoint, Ktor plugin setup, and Koin installation
- `Config.kt`: plain data classes + `loadConfig()` function for env vars with defaults
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

Install Koin via `install(Koin)` in the Ktor application:

```kotlin
fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0") {
        install(Koin) {
            slf4jLogger()
            modules(appModule, releasesModule, connectionsModule, blocksModule)
        }
        configureRouting()
    }.start(wait = true)
}
```

Routes inject services using `val service by inject<ReleasesService>()` from the Ktor `Route`/`Application` scope.

**Koin version requirement**: Use **Koin 4.1.1+** for Ktor 3.x compatibility. Koin 4.0.x targets Ktor 2.x and will fail at runtime with `NoClassDefFoundError: io/ktor/server/routing/RoutingKt`.

## Dependency wiring with Koin modules

Organize Koin modules by feature. Each feature defines its own `org.koin.dsl.module { }`.

**Shared infrastructure module** — database, HTTP client, config:

```kotlin
val appModule = module {
    single { loadConfig() }
    single<DataSource> { dataSource(get<Config>().database) }
    single<Database> { initDatabase(get<DataSource>()) }
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

- Model config as **plain data classes** (not `@Serializable` — they have no serialization use case).
- Read environment variables in a dedicated `loadConfig()` function, not in default parameter values.
- Provide config as a Koin singleton so features can inject and read their subsection.

```kotlin
data class Config(
    val database: DatabaseConfig = DatabaseConfig(),
    val server: ServerConfig = ServerConfig(),
)

fun loadConfig(): Config {
    return Config(
        database = DatabaseConfig(
            url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/release_wizard",
            ...
        ),
    )
}
```

## Persistence

- Use Exposed with JDBC (blocking) wrapped in `newSuspendedTransaction(Dispatchers.IO, db)`.
- **Never use bare `transaction { }`** in suspend functions — it blocks coroutine threads.
- Repositories accept `Database` via constructor and pass it to `newSuspendedTransaction`.
- Use a `dbQuery` helper in repositories:

```kotlin
class ExposedProjectsRepository(private val db: Database) : ProjectsRepository {
    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, db) { block() }

    override suspend fun findAll(): List<Project> = dbQuery {
        ProjectTable.selectAll().map { it.toProject() }
    }
}
```

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

### Application-level background tasks

The only acceptable place for a standalone `CoroutineScope` is application-level background work (e.g., periodic cleanup). Tie it to the Ktor lifecycle:

```kotlin
fun Application.configureBackgroundTasks() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    environment.monitor.subscribe(ApplicationStopped) { scope.cancel() }
    scope.launch { periodicCleanup() }
}
```
