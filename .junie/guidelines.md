# Development Guidelines

## Philosophy

### Core Beliefs

- **Incremental progress over big bangs** — Small changes that compile and pass tests
- **Learning from existing code** — Study and plan before implementing
- **Pragmatic over dogmatic** — Adapt to project reality
- **Clear intent over clever code** — Be boring and obvious

### Simplicity Means

- Single responsibility per function/class
- Avoid premature abstractions
- No clever tricks — choose the boring solution
- If you need to explain it, it's too complex

## Process

### 1. Planning & Staging

Break complex work into 3-5 stages. Document in `IMPLEMENTATION_PLAN.md`:

```markdown
## Stage N: [Name]
**Goal**: [Specific deliverable]
**Success Criteria**: [Testable outcomes]
**Tests**: [Specific test cases]
**Status**: [Not Started|In Progress|Complete]
```
- Update status as you progress
- Remove file when all stages are done

### 2. Implementation Flow

1. **Understand** — Study existing patterns in the codebase
2. **Test** — Write test first (red)
3. **Implement** — Minimal code to pass (green)
4. **Refactor** — Clean up with tests passing
5. **Finish** — Update the status in the plan and communicate that the work is done

### 3. When Stuck (After 3 Attempts)

**CRITICAL**: Maximum 3 attempts per issue, then STOP.

1. **Document what failed**:
    - What you tried
    - Specific error messages
    - Why you think it failed

2. **Research alternatives**:
    - Find 2-3 similar implementations
    - Note different approaches used

3. **Question fundamentals**:
    - Is this the right abstraction level?
    - Can this be split into smaller problems?
    - Is there a simpler approach entirely?

4. **Try different angle**:
    - Different library/framework feature?
    - Different architectural pattern?
    - Remove abstraction instead of adding?

## Technical Standards

### Architecture Principles

- **Composition over inheritance** — Use dependency injection
- **Interfaces over singletons** — Enable testing and flexibility
- **Explicit over implicit** — Clear data flow and dependencies
- **Test-driven when possible** — Never disable tests, fix them

### Code Quality

- **Every completed task must**:
    - Compile successfully
    - Pass all existing tests
    - Include tests for new functionality
    - Follow project formatting/linting

- **Before committing**:
    - Run formatters/linters
    - Self-review changes
    - Ensure commit message explains "why"

## Project Integration

### Learning the Codebase

- Find 3 similar features/components
- Identify common patterns and conventions
- Use the same libraries/utilities when possible
- Follow existing test patterns

## Quality Gates

### Definition of Done

- [ ] Tests are written and passing
- [ ] Code follows project conventions
- [ ] No linter/formatter warnings
- [ ] Implementation matches plan
- [ ] No TODOs without issue numbers

### Test Guidelines

- Test behaviour, not implementation
- Clear test names describing a scenario
- Use existing test utilities/helpers
- Tests should be deterministic

## Important Reminders

**NEVER**:
- Disable tests instead of fixing them
- Finish when the code that doesn't compile
- Make assumptions — verify with existing code

**ALWAYS**:
- Update plan documentation as you go
- Learn from existing implementations
- Stop after 3 failed attempts and reassess

# About the project

## Overview 

This is an application that will help with Kotlin library releases. 
It is called Release Wizard. It should be a builder application, 
where a user can build the release pipeline from several available blocks and connections in between them.

## Blocks

Block connections can be of three types:
- sequential — a block can only be executed after a successful execution of all previous blocks
- parallel — a block is executed in parallel with other blocks
- container — a block can contain other blocks, it does not have any actions associated with it. 
Blocks inside a container can't connect with blocks outside that container.

### Block attributes

Action blocks are not containers.
Each action block has:
- Execution status during a release: 
  - waiting, 
  - running, 
  - failed, 
  - succeeded, 
  - waiting for input
- Block specific information on view
- It's parameters. Parameters can also be inherited from a project or entered specifically for the block.
- Outputs. Outputs can be used by the following builds as input parameters. 
All outputs must be defined by a user and are specific to the block type
- Optional timeout

### Block types

Types of the blocks that are available:
- Slack message. Sends a message to a Slack channel. A channel must be specified. A message can be from a template (templates are stored in a project, they can use project parameters in the template's text), or can be written in the block itself. When the message is sent — block is finished successfully
- TeamCity build. When the block is started — it starts a corresponding teamcity build. Then the build status is tracked and synced with the block status. The whole build chain is displayed with statuses for each started build. The block finishes when the whole chain is finished. This block can have output parameters of two types: build parameters — user can ask to output a parameter value from this build; artifacts — artifacts from the build are output as a link to them
- Maven Central Portal Publication status. This build accepts a publication id and follows its status on Maven Central Portal until it is either published or failed.
- GitHub Action. Triggers the specified action on the GitHub, optionally providing parameters to it.
- GitHub Publication. Creates a publication, auto generates release notes using previous and current tag and a release branch. Pauses for confirmation and optional editing of the release notes.
- User action. Pauses execution by asking the user to review its current status, displaying its input parameters. Finished when the user accepts or restarts a previous block.


### Other block features

Each block can be restarted/paused/stopped at any time. 

Block editor should be intuitive, allow one to make easy connections between blocks in UI using drag and drop.

So all together the block system forms a direct acyclic graph, 
where each node is either a container (which contains another graph with the same rules) 
or a simple block of one of the available types.

## Projects

One full block system forms a project template.
Each project then can be used to start a release process.
The started project is called a release.
Project can have parameters which can be used in any block.
Before starting a release, each non-optional input parameter must be specified for each block.

## Connections

Project can have connections. The same connections can be reused between different projects. Connection types are:
- Slack. A connection with an organization. Needs credentials, they are stored.
- TeamCity. A connection with an instance
- GitHub. Uses a token to perform requests.
- Maven Central Portal. Developer credentials to query publication status.

# Technical stack

## Dependencies

Ask you if you need to use any additional libraries or to upgrade existing ones.

## Build Script

Don't change build scripts during normal development.
If you need a change — ask for it in the issue.

## Services and communication — kotlinx.rpc

Use kotlinx.rpc for communications between services.
A simple service can be implemented like this:
```Kotlin
// shared code
@Rpc
interface ServiceExample {
    suspend fun noStreaming(param: String): String
    fun streaming(param: String): Flow<String>
    suspend fun clientStreaming(flow: Flow<String>): String
}
```

- Prefer multiple services over one big service.
- Prefer multiple arguments over one big argument.

## Exposed

Use exposed for database access:
- Use only async queries from `1.0.0-beta-5` and higher versions. They use R2DBC and all have
`org.jetbrains.exposed.v1.r2dbc` in their package name
- Use `tr` function to wrap suspending transactions

## Compose Multiplatform
- Use Compose Multiplatform for UI code. Focus on Desktop and Web platforms, but make sure Mobile (iOS) is not a complete mess too
- Only write compose code in `commonMain`
- Create UIs that feel native on the macOS/iOS platforms, even for Web

## Other
- Securely store credentials for connections
- Support authentication system. Use auth on the Ktor level, not the RPC level
