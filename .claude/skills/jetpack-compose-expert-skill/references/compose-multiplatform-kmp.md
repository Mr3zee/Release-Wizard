# Compose Multiplatform (KMP) Reference

JetBrains **Compose Multiplatform** shares the same Compose compiler and runtime as Android
Jetpack Compose, but targets Desktop (JVM), Web (JS/WasmJS), and iOS in addition to Android.
The core APIs (`@Composable`, `remember`, `mutableStateOf`, `LaunchedEffect`, `Modifier`,
Material3, Foundation, etc.) are **identical across all platforms** — write them in `commonMain`.

---

## Key differences from Android-only Compose

| Area | Android (Jetpack Compose) | Compose Multiplatform (KMP) |
|------|--------------------------|----------------------------|
| Entry point | `setContent {}` in `Activity` | Desktop: `application { Window { ... } }`, Web: `CanvasBasedWindow {}` or `ComposeViewport` |
| Window management | Managed by Android OS | Desktop: `Window()`, `DialogWindow()`, `Tray()`, menu bars via `MenuBar {}` |
| Resources | `painterResource(R.drawable.x)` | `org.jetbrains.compose.resources`: `Res.drawable.x`, `stringResource(Res.string.x)` |
| Navigation | AndroidX Navigation Compose | Use Compose Multiplatform-compatible navigation (e.g., Decompose, Voyager, or JetBrains' navigation-compose for KMP) |
| Platform APIs | Direct Android SDK access | Use `expect`/`actual` declarations for platform-specific code |
| ViewModel | AndroidX `ViewModel` | Use `expect`/`actual`, or multiplatform alternatives (e.g., KMP-ViewModel, manual DI) |
| File I/O, networking | Android APIs | Use Ktor, Okio, kotlinx-io, or `expect`/`actual` |
| Context | `LocalContext.current` | Not available in `commonMain`; use `expect`/`actual` or `CompositionLocal` to provide platform context |

---

## Platform-specific patterns

### Desktop (JVM)

**Entry point:**
```kotlin
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "My App",
    ) {
        App() // your commonMain composable
    }
}
```

**Window management:**
- `Window()` — main application window with title, icon, size, position
- `DialogWindow()` — modal/non-modal dialog windows
- `Tray(icon, menu = { Item("Quit", onClick = ::exitApplication) })` — system tray
- `MenuBar { Menu("File") { Item("Open", onClick = { ... }) } }` — native menu bars

**Desktop-specific APIs:**
- Keyboard shortcuts: `Modifier.onKeyEvent {}`, `Modifier.onPreviewKeyEvent {}`
- Mouse hover: `Modifier.onPointerEvent(PointerEventType.Enter) {}` / `PointerEventType.Exit`
- Window state: `rememberWindowState(width = 800.dp, height = 600.dp)`
- Right-click context menus: `ContextMenuArea(items = { ... }) { ... }`
- Tooltips: `TooltipArea(tooltip = { ... }) { ... }`
- Scrollbars: `VerticalScrollbar(adapter = rememberScrollbarAdapter(scrollState))`
- File dialogs: use `java.awt.FileDialog` or `javax.swing.JFileChooser` via `expect`/`actual`

**Desktop `CompositionLocal` values:**
- `LocalWindowInfo` — window focus state
- `LocalDensity` — screen density (may differ per monitor)

### Web (JS / WasmJS)

**Entry point (WasmJS — preferred for modern browsers):**
```kotlin
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        App()
    }
}
```

**Entry point (JS — legacy browser support):**
```kotlin
fun main() {
    ComposeViewport(document.body) {
        App()
    }
}
```

**Web-specific considerations:**
- No file system access — use browser APIs via `expect`/`actual` or `external` declarations
- No native window management — single canvas-based rendering
- WasmJS is the preferred target for modern browsers; JS for legacy support
- Canvas-based rendering means **no DOM access** from Compose — the entire UI is drawn on a `<canvas>` element
- For DOM-based web UI, use Compose HTML (`org.jetbrains.compose.html`) — this is a separate framework, not canvas-based
- Browser back/forward navigation requires manual integration with `window.history`

### iOS

**Entry point (integrated into SwiftUI):**
```kotlin
// In iosMain
fun MainViewController() = ComposeUIViewController {
    App()
}
```

```swift
// In Swift
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
}
```

**iOS-specific considerations:**
- Supports iOS gestures (swipe, pinch, long press) through Compose gesture APIs
- Safe area insets via `WindowInsets.safeDrawing`
- System appearance (dark/light mode) via `isSystemInDarkTheme()`
- Status bar, navigation bar styling requires SwiftUI/UIKit bridge code

---

## Resources (org.jetbrains.compose.resources)

Compose Multiplatform has its own resource system that replaces Android's `R.*`:

**Directory structure:**
```
commonMain/
└── composeResources/
    ├── drawable/       # images (PNG, SVG, XML vector)
    ├── values/
    │   └── strings.xml # string resources
    ├── font/           # custom fonts (TTF, OTF)
    └── files/          # raw files
```

**Usage in composables:**
```kotlin
// Images
Image(
    painter = painterResource(Res.drawable.my_image),
    contentDescription = "description"
)

// Strings
Text(stringResource(Res.string.greeting, "World"))

// Fonts
val fontFamily = FontFamily(Font(Res.font.my_custom_font))
```

**Gradle setup:**
```kotlin
// build.gradle.kts
plugins {
    id("org.jetbrains.compose")
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.example.app"
    generateResClass = auto
}
```

---

## expect/actual patterns

Use `expect`/`actual` to bridge platform-specific APIs while keeping UI in `commonMain`:

```kotlin
// commonMain
expect class PlatformContext

expect fun getPlatformName(): String

@Composable
expect fun PlatformFilePicker(
    show: Boolean,
    onResult: (String?) -> Unit,
)
```

```kotlin
// jvmMain (Desktop)
actual class PlatformContext

actual fun getPlatformName(): String = "Desktop"

@Composable
actual fun PlatformFilePicker(show: Boolean, onResult: (String?) -> Unit) {
    // Use java.awt.FileDialog or javax.swing.JFileChooser
}
```

```kotlin
// wasmJsMain (Web)
actual class PlatformContext

actual fun getPlatformName(): String = "Web"

@Composable
actual fun PlatformFilePicker(show: Boolean, onResult: (String?) -> Unit) {
    // Use browser file input via external JS interop
}
```

**When to use expect/actual vs CompositionLocal:**
- `expect`/`actual` — for platform API implementations (file I/O, notifications, system features)
- `CompositionLocal` — for providing platform-derived values into the composition tree (e.g., a platform-specific theme seed, screen metrics)

---

## Lifecycle differences

Desktop and Web don't have Android's `Activity`/`Fragment` lifecycle. Adapt patterns accordingly:

| Android lifecycle | KMP equivalent |
|-------------------|----------------|
| `onCreate` / `onDestroy` | `LaunchedEffect(Unit)` + `DisposableEffect(Unit) { onDispose { ... } }` |
| `onResume` / `onPause` | Desktop: `LocalWindowInfo.current.isWindowFocused`; Web: `document.visibilityState` via `expect`/`actual` |
| `onConfigurationChanged` | Desktop: `rememberWindowState()` changes; Web: `window.onresize` |
| `ViewModel.onCleared()` | `DisposableEffect` `onDispose`, or `RememberObserver.onAbandoned/onForgotten` |

---

## Navigation in KMP

AndroidX Navigation Compose has experimental KMP support. Alternatives:

| Library | Approach | KMP support |
|---------|----------|------------|
| **JetBrains navigation-compose** | AndroidX Nav adapted for KMP | Official, experimental |
| **Decompose** | Component-based, lifecycle-aware | Full KMP, mature |
| **Voyager** | Screen-based, simple API | Full KMP |
| **Appyx** | Node-based, gesture navigation | Full KMP |

For simple apps, a manual `when`-based screen router in `commonMain` works fine:

```kotlin
@Composable
fun AppNavigation() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    when (currentScreen) {
        Screen.Home -> HomeScreen(onNavigate = { currentScreen = it })
        Screen.Detail -> DetailScreen(onBack = { currentScreen = Screen.Home })
    }
}
```

---

## Testing in KMP

```kotlin
// commonTest
class MyScreenTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testButton() = runComposeUiTest {
        setContent {
            MyScreen()
        }
        onNodeWithText("Click me").performClick()
        onNodeWithText("Clicked!").assertExists()
    }
}
```

Run with:
```bash
./gradlew :composeApp:jvmTest       # Desktop tests
./gradlew :composeApp:jsTest         # JS tests
./gradlew :composeApp:wasmJsTest     # WasmJS tests
```

---

## Best practices

1. **Write all UI in `commonMain`**. Only use `expect`/`actual` for platform APIs (file pickers, notifications, system integration).
2. **Use JetBrains Compose resources** (`org.jetbrains.compose.resources`) instead of Android `R.*` — they work across all platforms.
3. **Avoid Android-specific imports** in `commonMain` (no `android.*`, `androidx.activity.*`). The Compose Foundation and Material3 APIs are fully multiplatform.
4. **Window/lifecycle awareness**: Desktop and Web don't have Android's `Activity`/`Fragment` lifecycle. Use `DisposableEffect` and `LaunchedEffect` for lifecycle-like behavior.
5. **Testing**: Use `@OptIn(ExperimentalTestApi::class) runComposeUiTest { ... }` from `compose.ui.test` in `commonTest` for cross-platform UI tests.
6. **Gradle version catalog**: Always add Compose Multiplatform dependencies through `gradle/libs.versions.toml`, never hardcode versions.
7. **Target-specific source sets**: Keep `jvmMain`, `jsMain`, `wasmJsMain`, `iosMain` minimal — only platform bridge code belongs there.

---

## Common pitfalls

| Pitfall | Fix |
|---------|-----|
| Using `android.content.Context` in `commonMain` | Use `expect`/`actual` or `CompositionLocal` |
| Using `R.drawable.*` / `R.string.*` | Use `Res.drawable.*` / `Res.string.*` from `org.jetbrains.compose.resources` |
| Assuming `Activity` lifecycle exists | Use Compose lifecycle primitives (`LaunchedEffect`, `DisposableEffect`) |
| Using `AndroidView` / `AndroidViewBinding` in shared code | These are Android-only; wrap in `expect`/`actual` if platform embedding is needed |
| Hardcoding `dp` values for specific screen sizes | Use `BoxWithConstraints` or `LocalDensity` for responsive layouts that work across Desktop, Web, and Mobile |
| Forgetting scrollbars on Desktop | Desktop users expect scrollbars — add `VerticalScrollbar` / `HorizontalScrollbar` alongside scroll containers |
| `LazyColumn` inside `DropdownMenu` | Causes `IllegalStateException` ("Asking for intrinsic measurements of SubcomposeLayout layouts is not supported") in `runComposeUiTest`. Use `Column` + `Modifier.verticalScroll()` + `Modifier.heightIn(max = ...)` instead |
| Popup key events in tests | `performKeyInput { pressKey(Key.Escape) }` or `pressKey(Key.Tab)` on a `TextField` may not reach `onPreviewKeyEvent` when a `DropdownMenu` popup is expanded — the popup layer intercepts events. Instead of simulating the key, test the resulting state change directly (e.g., `performTextClearance()` to trigger dismiss logic) |
