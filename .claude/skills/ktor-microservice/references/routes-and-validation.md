# Routes and Validation

Keep transport concerns in routes and business decisions in services.

## Route style

- Define route groups as `Route` extension functions (`fun Route.releaseRoutes(...)`).
- Group endpoints with `route("/resource")`.
- Decode JSON bodies with `call.receive<RequestType>()`.
- Convert domain objects to response DTOs in route layer.

## Response mapping

- Return explicit status codes at the route layer.
- Use nullable or sealed results from the service layer for expected outcomes.
- Avoid using exceptions for normal branches such as not found or forbidden.

Example pattern:

```kotlin
when (val result = releaseService.getRelease(id, principal.userId)) {
    is GetReleaseResult.Success -> call.respond(result.release)
    is GetReleaseResult.NotFound -> call.respond(HttpStatusCode.NotFound, "Release not found")
    is GetReleaseResult.Forbidden -> call.respond(HttpStatusCode.Forbidden, "Access denied")
}
```

## Validation pattern

- Validate request/domain inputs before passing to the service layer.
- Handle validation errors in `StatusPages` and map to `400 Bad Request`.

Pattern:

```kotlin
install(StatusPages) {
    exception<ValidationException> { call, cause ->
        call.respondText(cause.message, status = HttpStatusCode.BadRequest)
    }
}
```

## DTO and domain boundaries

- Keep request/response DTOs separate from persistence models.
- Keep domain models and sealed outcomes in `Domain.kt` or service-level types.
- Keep repository details (Exposed table/result mapping) hidden from route handlers.

## Error handling guidelines

- Keep exception handlers explicit in `StatusPages`.
- Return stable, predictable client errors for validation and malformed input.
- Do not leak secrets, credentials, or internal stack details in error responses or logs.
