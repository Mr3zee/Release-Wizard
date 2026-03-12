# Service Architecture

Use layered architecture with Koin dependency injection in the server module.

## Module structure

The server package (`server/src/main/kotlin/com/github/mr3zee/`) contains:

- `Application.kt`: entrypoint, Ktor plugin setup, and Koin installation
- `Config.kt`: `@Serializable` config types loaded from application config
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
            modules(appModule, releasesModule, connectionsModule, blocksModule)
        }
        configureRouting()
    }.start(wait = true)
}
```

Routes inject services using `val service by inject<ReleasesService>()` from the Ktor `Route`/`Application` scope.

## Dependency wiring with Koin modules

Organize Koin modules by feature. Each feature defines its own `org.koin.dsl.module { }`.

**Shared infrastructure module** — database, HTTP client, config:

```kotlin
val appModule = module {
    single { loadConfig() }
    single { dataSource(get<Config>().database) }
}
```

**Feature Koin modules** — one per domain feature:

```kotlin
val releasesModule = module {
    single<ReleasesRepository> { ExposedReleasesRepository(get()) }
    single<ReleasesService> { DefaultReleasesService(get(), get<Config>().releases) }
}

val connectionsModule = module {
    single<ConnectionsRepository> { ExposedConnectionsRepository(get()) }
    single<ConnectionsService> { DefaultConnectionsService(get()) }
}
```

Rules:
- Shared infrastructure (data source, HTTP client, config) is defined **once** in `appModule`.
- Feature modules declare only their own repository, client, and service bindings.
- Bind by interface (`single<ReleasesService> { DefaultReleasesService(...) }`) to allow test overrides.
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

- Model config as `@Serializable` data classes.
- Support environment variable overrides.
- Provide config as a Koin singleton so features can inject and read their subsection.

## Persistence

- Use Exposed with R2DBC for repositories.
- Keep schema evolution in migration files local to the module.

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
