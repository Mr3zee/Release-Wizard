# Release Wizard

A powerful Kotlin release automation tool built with Compose Multiplatform. Release Wizard helps you create, manage, and execute complex release pipelines using a visual block-based editor.

## Features

- **Visual Pipeline Builder**: Drag-and-drop interface for creating release workflows
- **Block-Based System**: Pre-built blocks for common tasks (Slack messages, TeamCity builds, GitHub actions, etc.)
- **Multi-Platform Support**: Desktop, Web, and Mobile applications
- **Real-time Monitoring**: Live updates during release execution
- **Connection Management**: Secure integration with external services
- **Flexible Parameters**: Project and block-level parameter system
- **Retry Logic**: Automatic retry and manual restart capabilities

## Architecture

### Project Structure

* `/composeApp` - Compose Multiplatform UI application (Desktop, Web, Mobile)
  - `commonMain` - Shared UI code across all platforms
  - Platform-specific folders for platform-specific implementations

* `/server` - Ktor server application for backend API and web hosting
  - REST API endpoints for project and release management
  - Real-time WebSocket connections for live updates
  - Static file serving for web frontend

* `/shared` - Shared business logic and models
  - Domain models (Project, Release, Block, Connection, User)
  - RPC service interfaces using kotlinx-rpc
  - Common utilities and constants

* `/iosApp` - iOS application entry point

### Tech Stack

- **Frontend**: Compose Multiplatform with Material 3 Design
- **Backend**: Ktor with kotlinx-rpc for type-safe API communication
- **Database**: Exposed ORM with R2DBC for reactive database access
- **Dependency Injection**: Koin
- **Serialization**: kotlinx.serialization
- **Real-time Communication**: kotlinx-rpc with WebSocket support

## Block Types

- **Slack Message**: Send notifications to Slack channels
- **TeamCity Build**: Trigger and monitor TeamCity builds
- **GitHub Action**: Execute GitHub workflow actions
- **GitHub Release**: Create and manage GitHub releases
- **Maven Central**: Monitor publication status on Maven Central
- **User Action**: Pause for manual user confirmation
- **Container**: Group blocks into logical containers

## Running the Application

### Web Application
```bash
# Build and serve the web app
./gradlew server:runWebApp
```

Then open http://localhost:8080 in your browser.

### Desktop Application
```bash
# Run the desktop app
./gradlew composeApp:run
```

### Development Mode (Web)
```bash
# Run with hot reload
./gradlew composeApp:wasmJsBrowserDevelopmentRun
```

### Mobile Development

For iOS development, open the project in Xcode:
```bash
open iosApp/iosApp.xcodeproj
```

## Development

### Building
```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew shared:build
./gradlew composeApp:build
./gradlew server:build
```

### Testing
```bash
# Run all tests
./gradlew test
```

## Configuration

The application supports various connection types that need to be configured:

- **Slack**: Bot tokens and workspace configuration
- **TeamCity**: Server URL and authentication
- **GitHub**: Personal access tokens
- **Maven Central**: Developer credentials

Connections are managed securely with encrypted credential storage.

## Contributing

This project uses Kotlin Multiplatform with Compose Multiplatform for UI development. Key technologies:

- [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
- [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/)
- [Ktor](https://ktor.io/)
- [kotlinx-rpc](https://github.com/Kotlin/kotlinx-rpc)
- [Exposed](https://github.com/JetBrains/Exposed)
