# Testing

Follow these testing rules for server code:

- Never use mocks. Use real dependencies where possible.
- Wire tests through the actual production Koin modules.

## Core tools

- Test framework: `kotlin-test` with JUnit
- Ktor test runtime: `ktor-server-test-host`
- DI in tests: `koin-test`

## Preferred: test through production Koin modules

**Always wire tests using the real Koin modules.** This validates the actual dependency graph and catches wiring mistakes that in-memory fakes would hide.

```kotlin
class ReleaseRoutesTest : KoinTest {
    @Test
    fun `should create release`() = testApplication {
        application {
            install(Koin) {
                modules(appModule, releasesModule)
            }
            configureRouting()
        }
        val response = client.post("/releases") { ... }
        assertEquals(HttpStatusCode.Created, response.status)
    }
}
```

For tests needing a different config (e.g. test database), override the specific binding:

```kotlin
application {
    install(Koin) {
        modules(appModule, releasesModule, module {
            single { testConfig() }
        })
    }
    configureRouting()
}
```

## Integration app tests

- Use `testApplication { ... }` from `ktor-server-test-host`.
- Wire the real Koin modules — do not substitute feature services.

## Koin module verification

Use Koin's `checkModules` to verify DI graph completeness:

```kotlin
@Test
fun `verify Koin modules`() {
    koinApplication {
        modules(appModule, releasesModule, connectionsModule, blocksModule)
        checkModules()
    }
}
```

## Database tests

- Keep tests isolated: truncate or insert fresh rows per test rather than sharing state.

## What NOT to do

- **Do not create `InMemoryRepository` fakes.** They duplicate production logic, diverge silently, and give false confidence. Use the real repository backed by a real database.
- **Do not bypass Koin in tests** by instantiating services directly with hand-rolled dependencies. Go through the Koin modules so the wiring is tested too.

## Commands

- Run all tests: `./gradlew :server:test`
- Run selected tests:
  - `./gradlew :server:cleanTest :server:test --tests "com.github.mr3zee.SomeTest" --no-build-cache`
