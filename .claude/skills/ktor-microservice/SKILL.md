---
name: ktor-microservice
description: >
  Build and modify Ktor server code in the Release Wizard repository using the established project conventions.
  Use when working on server module tasks involving Ktor app setup, Koin dependency injection,
  route-service-repository design, Exposed persistence, and integration testing.
---

# Release Wizard Ktor Skill

Apply these conventions when implementing or reviewing Ktor code in this repository. Load only the references needed for the current task.

## Kotlin Coroutines — Structured Concurrency (MANDATORY)

All coroutine usage in the server module **must** follow structured concurrency. This is a hard rule, not a suggestion.

### Rules

1. **Never create stray `CoroutineScope()`**. Ktor route handlers and service methods already run inside a coroutine context. Use the lexical `coroutineScope { }` or `supervisorScope { }` suspend builders when you need child coroutines.
2. **Never create unmanaged `Job()` or `SupervisorJob()` instances**. A `Job` that isn't a child of a parent scope breaks cancellation propagation and leaks coroutines on server shutdown.
3. **Prefer `coroutineScope { }` over `CoroutineScope(...)`**. The suspend builder ties child coroutines to the caller's lifecycle — if the caller is cancelled, children are cancelled too. `CoroutineScope(...)` is a standalone scope that must be manually closed, which is almost always wrong in request-handling or service code.
4. **Parallel decomposition** — when you need to run tasks concurrently within a request, use `coroutineScope { }` + `launch`/`async` inside it:
   ```kotlin
   suspend fun fetchDashboard(userId: String): Dashboard = coroutineScope {
       val profile = async { profileRepo.get(userId) }
       val releases = async { releasesRepo.listByUser(userId) }
       Dashboard(profile.await(), releases.await())
   }
   ```
5. **Long-lived scopes** are acceptable only for application-level background work (e.g., scheduled tasks). In that case, tie the scope to the Ktor `Application` lifecycle and cancel it on shutdown via `environment.monitor.subscribe(ApplicationStopped) { scope.cancel() }`.

### Anti-patterns to reject during code review

```kotlin
// BAD: stray scope, leaks on cancellation
fun doWork() {
    CoroutineScope(Dispatchers.IO).launch { /* fire and forget */ }
}

// BAD: stray Job, no parent, breaks structured concurrency
val job = Job()
val scope = CoroutineScope(Dispatchers.Default + job)

// BAD: GlobalScope — never use
GlobalScope.launch { /* ... */ }
```

## Build and dependencies

Read [references/build-and-dependencies.md](references/build-and-dependencies.md) when adding modules, configuring plugins, or changing dependencies and build tasks.

## Service architecture and wiring

Read [references/service-architecture.md](references/service-architecture.md) when changing application bootstrap, Koin module wiring, or persistence integration.

## Package structure

Read [references/package-structure.md](references/package-structure.md) when creating, moving, or reorganizing files within the server module. Follow domain-driven feature packages, not technical layers.

## Routes and validation

Read [references/routes-and-validation.md](references/routes-and-validation.md) when editing HTTP contracts, route handlers, validation, or error mapping.

## Testing

Read [references/testing.md](references/testing.md) when writing or updating tests. Prefer integration tests with real dependencies.
