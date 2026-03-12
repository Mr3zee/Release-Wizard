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
