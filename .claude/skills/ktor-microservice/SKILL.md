---
name: ktor-microservice
description: >
  Build and modify Ktor server code in the Release Wizard repository using the established project conventions.
  Use when working on server module tasks involving Ktor app setup, Koin dependency injection,
  route-service-repository design, Exposed persistence, and integration testing.
---

# Release Wizard Ktor Skill

Apply these conventions when implementing or reviewing Ktor code in this repository. Load only the references needed for the current task.

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
