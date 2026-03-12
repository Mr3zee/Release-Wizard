# Testing

Follow these testing rules for server code:

- Never use mocks. Use real dependencies where possible.
- Wire tests through the actual production Koin modules.

## Core tools

- Test framework: `kotlin-test` with JUnit
- Ktor test runtime: `ktor-server-test-host`
- DI in tests: `koin-test`
- Test DB: H2 in PostgreSQL mode

## Preferred: test through production Koin modules

**Always wire tests using the real Koin modules.** This validates the actual dependency graph and catches wiring mistakes that in-memory fakes would hide.

```kotlin
class ProjectsRoutesTest {
    private fun Application.testModule() {
        install(Koin) {
            slf4jLogger()
            allowOverride(true)  // required in Koin 4.x for test overrides
            modules(
                appModule,
                projectsModule,
                module {
                    single<Config> { testDbConfig() }  // override Config
                },
            )
        }
        install(ContentNegotiation) { json(AppJson) }
        install(StatusPages) { ... }
        configureRouting()
    }

    @Test
    fun `list projects returns empty`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val response = client.get("/api/v1/projects")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
```

### Test DB configuration

Use H2 in-memory with PostgreSQL compatibility mode. **Use unique DB URLs per test** to ensure isolation:

```kotlin
private fun testDbConfig() = Config(
    database = DatabaseConfig(
        url = "jdbc:h2:mem:test_${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        user = "sa",
        password = "",
        driver = "org.h2.Driver",
    ),
)
```

### Test client with JSON

Use aliased imports to avoid server/client ContentNegotiation conflicts:

```kotlin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

private fun ApplicationTestBuilder.jsonClient() = createClient {
    install(ClientContentNegotiation) {
        json(AppJson)
    }
}
```

## Integration app tests

- Use `testApplication { ... }` from `ktor-server-test-host`.
- Wire the real Koin modules — do not substitute feature services.
- Define a `Application.testModule()` extension function for consistent setup.

## What NOT to do

- **Do not create `InMemoryRepository` fakes.** They duplicate production logic, diverge silently, and give false confidence. Use the real repository backed by a real database.
- **Do not bypass Koin in tests** by instantiating services directly with hand-rolled dependencies. Go through the Koin modules so the wiring is tested too.
- **Do not share DB state between tests.** Each test gets its own in-memory H2 database via unique URL.
- **Do not use `@Serializable` test JSON configs.** Use plain `Config()` constructors.

## Edge cases to test

- Malformed UUID path parameters → 400
- Nonexistent resources for GET, PUT, DELETE → 404
- Update preserves `createdAt`, changes `updatedAt`
- Blank/empty required fields → 400

## Commands

- Run all tests: `./gradlew :server:test`
- Run selected tests:
  - `./gradlew :server:cleanTest :server:test --tests "com.github.mr3zee.SomeTest" --no-build-cache`
