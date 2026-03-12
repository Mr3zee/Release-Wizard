# Package Structure

Organize packages by **domain feature**, not by technical layer. Group the vertical slice (routes, service, repository/client,
domain model) for each feature together in one package.

## Guiding principles

- **Feature-first**: top-level packages inside the server represent domain features or sub-domains, not layers like
  `routes/`, `service/`, `repository/`, `client/`.
- **Feature-prefixed file names**: use `ReleasesRoutes.kt`, `ReleasesService.kt` — not generic `Routes.kt`, `Service.kt`.
  Avoids ambiguous IDE tabs.
- **Split interfaces per feature**: when a service has multiple features, split the service interface so each feature
  owns its contract.
- **Split repositories per feature**: each feature defines its own repository interface **and its own Exposed repository
  implementation**. Do not create a single shared repository that implements multiple feature interfaces.
- **Shared repository for cross-cutting operations**: when multiple features need the same operations on a shared
  aggregate (e.g. `findById`, `update`), extract a shared repository interface and implementation into `persistence/`.
- **Exposed table objects in `persistence/`**: Exposed `object` table definitions are shared infrastructure. They live
  in `persistence/<Aggregate>Tables.kt`. Feature repositories import from `persistence/` — they do not own or duplicate
  table definitions.
- **Per-feature Koin modules**: each feature defines its own Koin `module { }` in `<Feature>Module.kt`. All feature
  modules are composed together in `Application.kt` via `install(Koin) { modules(...) }`.
- **Shared domain models at root**: domain models used across features live in `Model.kt` at the server root package.
- **Bootstrap at root**: `Application.kt` stays at the server root. It installs Koin and composes all feature modules.
- **Config split**: app-level config (`host`, `port`, `database`) stays in root `Config.kt`.
  Feature-specific config lives in the feature package.
- **Tests mirror main**: test packages mirror the main source tree exactly.

## When to apply feature packages

Apply feature-based sub-packages when the server has **multiple distinct responsibilities** or sub-domains (typically 2+
independent route groups or distinct business processes).

Keep a **flat structure** when the server is too small to benefit from sub-packages (< 5 source files).

## Feature-packaged service structure

```
com.github.mr3zee/
├── Application.kt                         # Entrypoint — install(Koin), configure routing
├── AppModule.kt                           # Koin module for shared infra (config, database, HTTP client)
├── Config.kt                              # App-level config (host, port, db)
├── Model.kt                               # Domain models shared across features
├── persistence/                           # Shared Exposed tables and cross-cutting repositories
│   ├── <Aggregate>Tables.kt
│   └── <Aggregate>Repository.kt
├── <featureA>/
│   ├── <FeatureA>Module.kt               # Koin module: binds repos, services for this feature
│   ├── <FeatureA>Routes.kt               # HTTP endpoints for this feature
│   ├── <FeatureA>Service.kt              # Business logic
│   ├── <FeatureA>Repository.kt           # Feature-specific data access interface
│   ├── Exposed<FeatureA>Repository.kt    # Exposed impl, imports from persistence/
│   ├── <FeatureA>Client.kt              # Optional: interface for external service communication
│   └── <FeatureA>Requests.kt            # Request/response DTOs
├── <featureB>/
│   ├── <FeatureB>Module.kt              # Koin module for this feature
│   ├── <FeatureB>Routes.kt
│   ├── <FeatureB>Service.kt
│   ├── <FeatureB>Repository.kt
│   └── Exposed<FeatureB>Repository.kt
```

## Flat service structure

```
com.github.mr3zee/
├── Application.kt                         # Entrypoint — install(Koin), configure routing
├── AppModule.kt                           # Koin module: binds config, database, services, repos
├── Config.kt
├── Domain.kt                              # Domain models + DTOs + validation
├── Routes.kt
├── Service.kt
├── Repository.kt
```

### Decision heuristic: where does an operation belong?

| Question                                                       | Answer                                                                                           |
|----------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| Is this a DB operation used by 2+ features?                    | Put the interface + impl in `persistence/`.                                                      |
| Is this a DB operation specific to one feature?                | Put it in that feature's repository interface and `Exposed<Feature>Repository`.                  |
| Is this an external service call needed by one feature?        | Put it in that feature package as `<Feature>Client`.                                            |
| Does a table have a FK to another table?                       | Both tables stay in `persistence/` — the FK creates a compile-time dependency.                  |
| Is a table only referenced by one feature's repository?        | It still lives in `persistence/` — table objects are always shared infrastructure.              |
