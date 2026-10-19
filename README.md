# Thorization

Burp Suite extension for automated authorization, access control, and BOLA security testing built on the Montoya API.

[![Burp Suite Montoya API](https://img.shields.io/badge/Burp_Suite-Montoya_API-orange.svg)](https://portswigger.net/burp)
[![Java 21](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.x-02303A.svg)](https://gradle.org/)
[![Version](https://img.shields.io/badge/Version-1.3.2-brightgreen.svg)](CHANGELOG.md)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## Features

### Safe Request Interception
Drop or pause original client requests before they hit the server, while testing attacker roles in the background. Prevents unwanted state modification on test targets.
* **Drop Original**: Drops original request and executes configured role replays.
* **Replay First, Then Allow**: Executes role replays synchronously before forwarding the original request.
* **Pass Through**: Standard background replay without intercepting original traffic.
* **Scope and Method Filters**: Target state-changing methods only or all HTTP methods.

### Multi-Role Evaluation Matrix
* Evaluate requests across multiple user roles with custom headers, cookies, and tokens.
* Dedicated unauthenticated replay check for every request.
* Sequential role duplication with automatic index naming.
* Configurable role execution order and selective re-testing of captured traffic.

### Session Persistence
* Full JSON export and import preserving complete HTTP request and response payloads.
* Native Burp request and response editors populate automatically upon import.
* Pruned schema without redundant metadata or unedited sample templates.
* Interactive import options: Replace existing session or Append to active table.
* Backwards compatible with earlier session export schemas.

### Detection and Classification
* **BYPASS**: Attacker role accessed authorized content or returned HTTP 200 with matching response.
* **ENFORCED**: Server rejected attacker role with HTTP 401 or 403.
* **MANUAL**: Status code matched but response body differed significantly, flagged for manual review.

### AI Agent Bridge
Embedded REST API server running on `127.0.0.1:1337/api/` for external automation, LLM tools, and MCP agents to query audit records, manage roles, and trigger replays.

### Statistics Dashboard
Live verdict distribution and summary metrics for reporting.

---

## Architecture

```
thorization/
├── ThorizationExtension.java         # Montoya API entry point
├── App.java                          # Standalone GUI launcher
├── model/                            # Domain entities
│   ├── UserRole.java                 # Role credentials and state
│   ├── AuthorizationCheckResult.java # Replay result, verdict, diff scores
│   ├── AuditRecord.java              # Master audit entry and traffic data
│   ├── Verdict.java                  # BYPASS, ENFORCED, INDETERMINABLE
│   ├── InterceptionMode.java         # Interception strategies
│   ├── OriginalAction.java           # Original traffic outcome
│   ├── FilterConfig.java             # Scope, method, and regex rules
│   └── pipeline/                     # Pipeline workflow entities
├── core/                             # Business logic
│   ├── EngineController.java         # State management and listener coordination
│   ├── HttpTrafficHandler.java       # Proxy request and HTTP handler
│   ├── ReplayEngine.java             # Asynchronous multi-role replay executor
│   ├── DetectionEngine.java          # Verdict evaluation engine
│   └── pipeline/                     # Workflow execution engine
├── bridge/                           # Local REST API server
│   ├── AiBridgeServer.java           # HTTP server on localhost port 1337
│   └── handlers/                     # Endpoints for records, roles, status
├── ui/                               # Swing presentation layer
│   ├── MainTabbedPane.java           # Tab container
│   ├── RolesPanel.java               # Roles configuration and filters
│   ├── TablePanel.java               # Interception and analysis table
│   ├── PipelineStudioPanel.java      # Pipeline studio interface
│   ├── StatisticsPanel.java          # Charts and analytics
│   └── components/                   # Renderers, dialogs, viewer panels
├── export/                           # Persistence layer
│   ├── SessionExporter.java          # JSON serialization
│   └── SessionImporter.java          # JSON deserialization with message restoral
└── util/                             # Utilities
    ├── DiffUtils.java                # Similarity and diff algorithms
    └── HeaderUtils.java              # Header and cookie injection
```

---

## Requirements

* Java Development Kit 21 or newer
* Burp Suite Community or Professional 2023.x or newer
* Gradle wrapper included in repository

---

## Build and Installation

### 1. Clone Repository
```bash
git clone https://github.com/ArdhenisAflah/Thorization.git
cd Thorization
```

### 2. Build Extension JAR
Build the standalone Fat-JAR with all bundled dependencies:

```bash
# Linux / macOS
./gradlew shadowJar

# Windows
.\gradlew.bat shadowJar
```

Output file:
```text
app/build/libs/Thorization.jar
```

### 3. Load into Burp Suite
1. Open Burp Suite.
2. Navigate to **Extensions** > **Installed**.
3. Click **Add**.
4. Set **Extension type** to `Java`.
5. Select `app/build/libs/Thorization.jar`.
6. Click **Next** to initialize the extension.

---

## Usage

### 1. Configure Roles
1. Open the **Thorization** tab > **Roles Manager**.
2. Enter role name, cookie header, and custom authorization headers.
3. Click **Save Role Changes**.
4. Select the desired interception mode under Safe Request Interception.

### 2. Intercept and Analyze
1. In **Interception & Analysis Table**, toggle the engine to **ON**.
2. Route target traffic through Burp Proxy.
3. Review verdicts in the table:
   * Red: Bypass detected
   * Green: Access enforced
   * Orange: Manual review needed
4. Select any row to inspect original and replayed requests and responses side-by-side.

### 3. Session Management
* **Export**: Save captured records, verdicts, and role matrices to JSON.
* **Import**: Load previous session files. Choose **Replace** to clear current table or **Append** to merge with active records.

---

## Tests

Execute the unit test suite:

```bash
./gradlew test
```

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
