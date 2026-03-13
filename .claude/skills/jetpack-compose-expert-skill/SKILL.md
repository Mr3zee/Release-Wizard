---
name: jetpack-compose-expert-skill
description: >
  Compose Multiplatform & Jetpack Compose expert skill for Kotlin UI development across
  Android, Desktop (JVM), Web (JS/WasmJS), and iOS. Guides state management decisions
  (@Composable, remember, mutableStateOf, derivedStateOf, State hoisting), view composition
  and structure, Modifier chains, lazy lists, navigation, animation, side effects, theming,
  accessibility, and performance optimization. Backed by actual androidx source code analysis.
  Use this skill whenever the user mentions Compose, Compose Multiplatform, KMP Compose,
  @Composable, remember, LaunchedEffect, Scaffold, NavHost, MaterialTheme, LazyColumn,
  Modifier, recomposition, Style, styleable, MutableStyleState, or any Compose API. Also
  trigger when the user says "Android UI", "Kotlin UI", "Desktop UI", "compose layout",
  "compose navigation", "compose animation", "material3", "compose styles", "styles api",
  "multiplatform compose", or asks about modern Kotlin UI development patterns on any platform.
  Even casual mentions like "my compose screen is slow", "how do I pass data between screens",
  or "compose desktop window" should trigger this skill.
---

# Compose Multiplatform & Jetpack Compose Expert Skill

Non-opinionated, practical guidance for writing correct, performant Compose code — covering
both **JetBrains Compose Multiplatform** (Desktop, Web, iOS) and **Android Jetpack Compose**.
Focuses on real pitfalls developers encounter daily, backed by analysis of the actual
`androidx/androidx` source code (branch: `androidx-main`) and JetBrains Compose Multiplatform
extensions.

## Workflow

When helping with Compose code, follow this checklist:

### 1. Understand the request
- What Compose layer is involved? (Runtime, UI, Foundation, Material3, Navigation)
- What platform(s) are targeted? (Android, Desktop/JVM, Web/JS, Web/WasmJS, iOS, or commonMain for all)
- Is this a state problem, layout problem, performance problem, platform-specific problem, or architecture question?

### 2. Consult the right reference
Read the relevant reference file(s) from `references/` before answering:

| Topic | Reference File |
|-------|---------------|
| `@State`, `remember`, `mutableStateOf`, state hoisting, `derivedStateOf`, `snapshotFlow` | `references/state-management.md` |
| Structuring composables, slots, extraction, preview | `references/view-composition.md` |
| Modifier ordering, custom modifiers, `Modifier.Node` | `references/modifiers.md` |
| `LaunchedEffect`, `DisposableEffect`, `SideEffect`, `rememberCoroutineScope` | `references/side-effects.md` |
| `CompositionLocal`, `LocalContext`, `LocalDensity`, custom locals | `references/composition-locals.md` |
| `LazyColumn`, `LazyRow`, `LazyGrid`, `Pager`, keys, content types | `references/lists-scrolling.md` |
| `NavHost`, type-safe routes, deep links, shared element transitions | `references/navigation.md` |
| `animate*AsState`, `AnimatedVisibility`, `Crossfade`, transitions | `references/animation.md` |
| `MaterialTheme`, `ColorScheme`, dynamic color, `Typography`, shapes | `references/theming-material3.md` |
| Recomposition skipping, stability, baseline profiles, benchmarking | `references/performance.md` |
| Semantics, content descriptions, traversal order, testing | `references/accessibility.md` |
| Removed/replaced APIs, migration paths from older Compose versions | `references/deprecated-patterns.md` |
| **Styles API** (experimental): `Style {}`, `MutableStyleState`, `Modifier.styleable()` | `references/styles-experimental.md` |
| **KMP / Compose Multiplatform**: platform differences, expect/actual, Desktop & Web specifics | `references/compose-multiplatform-kmp.md` |

### 3. Apply and verify
- Write code that follows the patterns in the reference
- For KMP projects, prefer `commonMain` code; use `expect`/`actual` only when platform APIs differ
- Flag any anti-patterns you see in the user's existing code
- Suggest the minimal correct solution — don't over-engineer

### 4. Cite the source
When referencing Compose internals, point to the exact source file:
```
// See: compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/Composer.kt
```

## Compose Multiplatform (KMP) Guidance

For KMP / Compose Multiplatform topics (platform differences, `expect`/`actual`, Desktop window
management, Web entry points, iOS integration, resources, lifecycle, navigation, testing),
read the full reference: **`references/compose-multiplatform-kmp.md`**

Quick summary: Compose Multiplatform shares the same compiler and runtime as Android Jetpack
Compose. Core APIs are identical — write all UI in `commonMain`, use `expect`/`actual` only
for platform-specific APIs.

## Key Principles

1. **Compose thinks in three phases**: Composition → Layout → Drawing. State reads in each
   phase only trigger work for that phase and later ones.

2. **Recomposition is frequent and cheap** — but only if you help the compiler skip unchanged
   scopes. Use stable types, avoid allocations in composable bodies.

3. **Modifier order matters**. `Modifier.padding(16.dp).background(Color.Red)` is visually
   different from `Modifier.background(Color.Red).padding(16.dp)`.

4. **State should live as low as possible** and be hoisted only as high as needed. Don't put
   everything in a ViewModel just because you can.

5. **Side effects exist to bridge Compose's declarative world with imperative APIs**. Use the
   right one for the job — misusing them causes bugs that are hard to trace.

6. **Prefer `commonMain` over platform source sets**. The vast majority of Compose code is
   platform-agnostic. Only split to `jvmMain`/`jsMain`/`wasmJsMain`/`iosMain` when calling
   platform-specific APIs.

7. **Enforce Kotlin coroutines structured concurrency**. Never create stray `CoroutineScope()`
   or unmanaged `Job` instances. In Compose, always use the scopes the framework provides:
   `LaunchedEffect`, `rememberCoroutineScope`, or `produceState`. In plain Kotlin, prefer
   the lexical `coroutineScope { }` / `supervisorScope { }` suspend builders over constructing
   `CoroutineScope(...)` manually. Stray scopes and jobs leak coroutines, break cancellation
   propagation, and make code harder to reason about. See `references/side-effects.md` for
   Compose-specific patterns.

## Canvas Editor Conventions (Release Wizard specific)

The DAG editor (`composeApp/.../editor/`) uses a pure Canvas approach:
- **Coordinate system**: Block positions stored as dp in `DagGraph.positions`. Canvas converts to pixels via `CanvasTransform(zoom, panOffset, density)`.
- **Pointer handling**: All interactions (select, drag, pan, zoom, connect) in `pointerInput` on the Canvas. Never use `remember`ed derived values inside `pointerInput` lambdas — create `CanvasTransform` inline from state reads to avoid stale captures.
- **Undo/redo**: Structural changes (add/remove block/edge, move) push to undo stack. Property changes (name, params, timeout) use `updateGraphSilent` to avoid per-keystroke undo entries. Type changes are discrete and do push undo.
- **Color mapping**: `blockTypeColor()` in `DagCanvas.kt` is the single source of truth, shared with `EditorToolbar.kt`.
- **Keyboard shortcuts**: Use `isCtrlPressed || isMetaPressed` for cross-platform Ctrl/Cmd.

## UI Testing

This project has two layers of UI testing:

### 1. Manual verification with compose-ui-test-server (fast iteration)
Use during development to visually verify UI via HTTP endpoints. See the `compose-ui-test-server` skill for details. This is the **first step** when building or changing UI — iterate quickly with screenshots before writing automated tests.

### 2. Automated Compose UI tests (regression suite)
Once the UI behavior is correct, write automated `runComposeUiTest` tests in `composeApp/src/jvmTest/`. These run via `./gradlew :composeApp:jvmTest` with no server needed.

**Test infrastructure:**
- `MockHttpClient.kt` — `mockHttpClient(routes)` creates an `HttpClient` backed by Ktor `MockEngine`. Routes map path suffixes (e.g., `"/projects"`) to `MockRoute(body, status)` via exact `/api/v1` prefix matching. Uses `expectSuccess = true` to match production behavior.
- Tests compose real screens with real ViewModels, passing mock HTTP clients — no fakes for ViewModels themselves.

**Patterns:**
```kotlin
@OptIn(ExperimentalTestApi::class)  // class-level, not per-method
class MyScreenTest {

    @Test
    fun `shows data from API`() = runComposeUiTest {
        val client = mockHttpClient(mapOf("/myroute" to json("""{"items":[]}""")))
        val vm = MyViewModel(MyApiClient(client))
        setContent { MaterialTheme { MyScreen(viewModel = vm, ...) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("expected").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("expected").assertExists()
    }
}
```

**Key rules:**
- `@OptIn(ExperimentalTestApi::class)` at class level, not per method
- Use `waitUntil(timeoutMillis = 3000L) { ... }` (first param is `conditionDescription: String?`, not timeout)
- Never use `delay()` in tests — use `waitUntil` for async state changes
- Always pass all screen callbacks consistently (don't omit nullable ones in some tests)
- Test error states (API 500) alongside happy paths and empty states
- Mock route keys are path suffixes like `"/projects"`, matched against full `/api/v1/projects`

**Existing test files:**
- `LoginScreenTest` — render, button enable/disable, error display
- `ProjectListScreenTest` — data display, empty/error states, dialog, callbacks
- `ConnectionScreensTest` — list, form fields, validation, callbacks
- `AppNavigationTest` — session auto-login, navigation, logout, login flow

## Source Code Receipts

Beyond the guidance docs, this skill bundles the **actual source code** from
`androidx/androidx` (branch: `androidx-main`). When you need to verify how something
works internally, or the user asks "show me the actual implementation", read
the raw source from `references/source-code/`:

| Module | Source Reference | Key Files Inside |
|--------|-----------------|------------------|
| Runtime | `references/source-code/runtime-source.md` | Composer.kt, Recomposer.kt, State.kt, Effects.kt, CompositionLocal.kt, Remember.kt, SlotTable.kt, Snapshot.kt |
| UI | `references/source-code/ui-source.md` | AndroidCompositionLocals.android.kt, Modifier.kt, Layout.kt, LayoutNode.kt, ModifierNodeElement.kt, DrawModifier.kt |
| Foundation | `references/source-code/foundation-source.md` | LazyList.kt, LazyGrid.kt, BasicTextField.kt, Clickable.kt, Scrollable.kt, Pager.kt |
| Material3 | `references/source-code/material3-source.md` | MaterialTheme.kt, ColorScheme.kt, Button.kt, Scaffold.kt, TextField.kt, NavigationBar.kt |
| Navigation | `references/source-code/navigation-source.md` | NavHost.kt, ComposeNavigator.kt, NavGraphBuilder.kt, DialogNavigator.kt |

### Two-layer approach
1. **Start with guidance** — read the topic-specific reference (e.g., `references/state-management.md`)
2. **Go deeper with source** — if the user wants receipts or you need to verify, read from `references/source-code/`

### Source tree map

**androidx/androidx** (Android Jetpack Compose & shared Compose runtime/foundation/material3):
```
compose/
├── runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/
├── ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/
├── ui/ui/src/commonMain/kotlin/androidx/compose/ui/
├── foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/
├── material3/material3/src/commonMain/kotlin/androidx/compose/material3/
└── navigation/navigation-compose/src/commonMain/kotlin/androidx/navigation/compose/
```

**JetBrains/compose-multiplatform** (KMP extensions — Desktop, Web, iOS entry points & platform integration):
```
compose/
├── components/resources/       # Multiplatform resources (Res.drawable, Res.string, etc.)
├── components/ui-tooling-preview/
└── html/                       # Compose HTML (DOM-based, separate from Canvas-based Web)
```
Note: Compose Multiplatform reuses the `commonMain` source from `androidx/compose` for runtime,
UI, foundation, and material3. JetBrains adds platform-specific entry points and integrations.
