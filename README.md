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
Embedded REST API on `http://127.0.0.1:1337/api/` enabling external LLM agents, security automation scripts, and Model Context Protocol servers to query audit records, configure roles, and dispatch authorization checks.

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

## AI Bridge REST API and MCP Integration

Thorization includes an embedded HTTP REST service binding to `http://127.0.0.1:1337/api/`. This interface connects external LLM agents, security automation pipelines, and Model Context Protocol servers directly to the core authorization replay engine.

All endpoints communicate via `application/json`.

```
http://127.0.0.1:1337/api/
├── GET    /status          # Extension state, counters, active filter rules
├── GET    /roles           # List all configured test roles
├── GET    /roles/{id}      # Get single role by ID
├── POST   /roles           # Create new test role
├── PUT    /roles/{id}      # Update existing role
├── DELETE /roles/{id}      # Remove role
├── POST   /analyze         # Replay and analyze request across all roles
├── GET    /records         # Query audit records with optional filters
└── GET    /records/{id}    # Retrieve full replay detail for record
```

---

### Endpoints Specification

#### 1. GET /api/status

Retrieves extension engine state, interception strategy, filter rules, and counters.

* **Request Parameters**: None.
* **Response Status**: `200 OK`
* **Response Body**:
  * `status` (string): Service health state, e.g. `"online"`.
  * `version` (string): Extension release version.
  * `engineEnabled` (boolean): Replay engine toggle status.
  * `interceptionMode` (string): Active interception mode (`DROP_ORIGINAL`, `REPLAY_FIRST`, `PASS_THROUGH`).
  * `dropAllRequests` (boolean): Whether all matched requests are dropped.
  * `activeRolesCount` (integer): Total enabled roles.
  * `totalRecordsCount` (integer): Total audit entries in memory.
  * `pipelineCount` (integer): Configured pipelines count.
  * `filterConfig` (object):
    * `onlyInScope` (boolean): Restrict analysis to Burp target scope.
    * `ignoreStaticFiles` (boolean): Skip static asset extensions.
    * `testUnauthenticated` (boolean): Include unauthenticated check.
    * `targetMethods` (array of strings): HTTP methods evaluated.
    * `stateChangingMethods` (array of strings): Methods subject to drop or replay-first delay.
    * `urlIncludeRegex` (string): Regex pattern for URL inclusion.
    * `urlExcludeRegex` (string): Regex pattern for URL exclusion.

```json
{
  "status": "online",
  "version": "1.3.2",
  "engineEnabled": true,
  "interceptionMode": "DROP_ORIGINAL",
  "dropAllRequests": false,
  "activeRolesCount": 2,
  "totalRecordsCount": 45,
  "pipelineCount": 0,
  "filterConfig": {
    "onlyInScope": true,
    "ignoreStaticFiles": true,
    "testUnauthenticated": true,
    "targetMethods": ["GET", "POST", "PUT", "DELETE", "PATCH"],
    "stateChangingMethods": ["POST", "PUT", "DELETE", "PATCH"],
    "urlIncludeRegex": "",
    "urlExcludeRegex": ""
  }
}
```

---

#### 2. GET /api/roles

Lists all configured test roles.

* **Request Parameters**: None.
* **Response Status**: `200 OK`
* **Response Body**: Array of role objects.
  * `id` (string): Role UUID.
  * `name` (string): Role label.
  * `headers` (string): Raw multiline HTTP headers.
  * `headersMap` (object): Key-value map of headers.
  * `cookies` (string): Raw cookie header value.
  * `cookiesMap` (object): Key-value map of cookies.
  * `enabled` (boolean): Role activation state.

```json
[
  {
    "id": "e4b52df3-8d48-4cb9-9529-5d2ea99a6cfb",
    "name": "Attacker_UserB",
    "headers": "X-Custom-Auth: token_user_b",
    "headersMap": {
      "X-Custom-Auth": "token_user_b"
    },
    "cookies": "session=attacker_session_token",
    "cookiesMap": {
      "session": "attacker_session_token"
    },
    "enabled": true
  }
]
```

---

#### 3. GET /api/roles/{id}

Retrieves details for a specific role.

* **Path Parameters**:
  * `id` (string, required): Role UUID.
* **Response Status**:
  * `200 OK`: Role object.
  * `404 Not Found`: Role does not exist.

---

#### 4. POST /api/roles

Creates a new test role.

* **Request Body** (`application/json`):
  * `name` (string, required): Role label.
  * `headers` (string, optional): Raw multiline header string.
  * `headersMap` (object, optional): Key-value map of headers.
  * `cookies` (string, optional): Raw cookie string.
  * `cookiesMap` (object, optional): Key-value map of cookies.
  * `enabled` (boolean, optional, default `true`): Role active state.
* **Response Status**:
  * `201 Created`: Created role object with generated `id`.
  * `400 Bad Request`: Role name is missing.

```json
{
  "name": "LowPrivUser",
  "headersMap": {
    "Authorization": "Bearer eyJhbGciOi..."
  },
  "cookiesMap": {
    "account_type": "standard"
  },
  "enabled": true
}
```

---

#### 5. PUT /api/roles/{id}

Updates an existing role.

* **Path Parameters**:
  * `id` (string, required): Role UUID.
* **Request Body** (`application/json`): Same fields as `POST /api/roles`.
* **Response Status**:
  * `200 OK`: Updated role object.
  * `400 Bad Request`: Payload missing or invalid.
  * `404 Not Found`: Role ID not found.

---

#### 6. DELETE /api/roles/{id}

Deletes a test role by UUID.

* **Path Parameters**:
  * `id` (string, required): Role UUID.
* **Response Status**:
  * `200 OK`: `{"success": true, "message": "Role deleted successfully."}`
  * `404 Not Found`: Role ID not found.

---

#### 7. POST /api/analyze

Submits an HTTP request to evaluate across configured roles and unauthenticated state. Original request is dispatched, responses are captured, and differential verdicts are generated.

* **Request Body** (`application/json`):
  * `url` (string, required if `rawRequest` omitted): Target URL.
  * `method` (string, optional, default `"GET"`): HTTP method.
  * `headers` (object, optional): Key-value map of request headers.
  * `rawHeaders` (string, optional): Multiline raw headers string.
  * `body` (string, optional): Request body payload.
  * `rawRequest` (string, optional): Complete raw RFC HTTP request string. Overrides separate method, headers, and body if provided.
  * `bypassFilter` (boolean, optional, default `false`): Skip scope and static asset filter checks.
  * `testUnauthenticated` (boolean, optional, default `true`): Replay without credentials.
  * `targetRoleIds` (array of strings, optional): Subset of role IDs to replay against. Defaults to all active roles.
  * `sync` (boolean, optional, default `true`): Block until all role replays and verdict calculations finish.
  * `sourceTool` (string, optional, default `"AI-Bridge"`): Source label recorded in audit log.

```json
{
  "url": "https://api.target.com/v1/users/1337/profile",
  "method": "GET",
  "headers": {
    "Authorization": "Bearer victim_token_here",
    "Accept": "application/json"
  },
  "bypassFilter": true,
  "testUnauthenticated": true
}
```

* **Response Status**: `200 OK`
* **Response Body**:
  * `success` (boolean): Analysis completion status.
  * `message` (string): Execution status note.
  * `recordId` (integer): ID assigned in audit table.
  * `timestamp` (integer): Millisecond timestamp.
  * `url` (string): Tested URL.
  * `method` (string): HTTP method.
  * `originalStatusCode` (integer): Status code from original request.
  * `originalResponseLength` (integer): Body length of original response.
  * `filteredOut` (boolean): Whether request was skipped by filter rules.
  * `filterReason` (string, nullable): Skip explanation when filtered out.
  * `vulnerabilityDetected` (boolean): High-level finding indicator. True if any role resulted in `BYPASS`.
  * `vulnerableRoles` (array of strings): Names of roles that accessed unauthorized resources.
  * `summary` (string): Plaintext summary of security verdict.
  * `roleVerdicts` (object): Map of role UUIDs to evaluation records:
    * `roleId` (string): Role UUID.
    * `roleName` (string): Role label.
    * `verdict` (string): Verdict string (`BYPASS`, `ENFORCED`, `INDETERMINABLE`).
    * `statusCode` (integer): Replayed HTTP status code.
    * `responseLength` (integer): Replayed response body byte length.
    * `similarityScore` (number): Similarity ratio compared to original response body (0.0 to 1.0).
    * `responseTimeMs` (integer): Response time in milliseconds.
    * `note` (string): Rule and status differential explanation.

```json
{
  "success": true,
  "message": "Analysis completed. 1 potential vulnerability detected.",
  "recordId": 104,
  "timestamp": 1740000000000,
  "url": "https://api.target.com/v1/users/1337/profile",
  "method": "GET",
  "originalStatusCode": 200,
  "originalResponseLength": 452,
  "filteredOut": false,
  "vulnerabilityDetected": true,
  "vulnerableRoles": [
    "Attacker_UserB"
  ],
  "summary": "VULNERABILITY DETECTED: 1 roles had unauthorized access (BYPASS).",
  "roleVerdicts": {
    "unauthenticated": {
      "roleId": "unauthenticated",
      "roleName": "Unauthenticated",
      "verdict": "ENFORCED",
      "statusCode": 401,
      "responseLength": 64,
      "similarityScore": 0.12,
      "responseTimeMs": 48,
      "note": "Replay returned 401 (Enforced)"
    },
    "e4b52df3-8d48-4cb9-9529-5d2ea99a6cfb": {
      "roleId": "e4b52df3-8d48-4cb9-9529-5d2ea99a6cfb",
      "roleName": "Attacker_UserB",
      "verdict": "BYPASS",
      "statusCode": 200,
      "responseLength": 450,
      "similarityScore": 0.99,
      "responseTimeMs": 52,
      "note": "Replay returned 200 matching original response (Bypass)"
    }
  }
}
```

---

#### 8. GET /api/records

Queries audit records and vulnerability findings.

* **Query Parameters**:
  * `limit` (integer, optional): Maximum number of entries returned.
  * `vulnerableOnly` (boolean, optional, default `false`): Filter results to records where `vulnerabilityDetected` is true.
* **Response Status**: `200 OK`
* **Response Body**: Array of record summary items:
  * `id` (integer): Audit record ID.
  * `timestamp` (integer): Millisecond timestamp.
  * `method` (string): HTTP method.
  * `url` (string): Target URL.
  * `path` (string): URL path.
  * `sourceTool` (string): Source origin (`Proxy`, `Repeater`, `AI-Bridge`).
  * `action` (string): Traffic action (`FORWARDED`, `DROPPED`, `REPLAY_FIRST`).
  * `originalStatus` (integer): Original HTTP response code.
  * `originalLength` (integer): Original response body length.
  * `vulnerabilityDetected` (boolean): Whether authorization bypass was detected.
  * `vulnerableRoles` (array of strings): Roles that bypassed access control.
  * `verdicts` (object): Map of role names to verdict strings.

```json
[
  {
    "id": 104,
    "timestamp": 1740000000000,
    "method": "GET",
    "url": "https://api.target.com/v1/users/1337/profile",
    "path": "/v1/users/1337/profile",
    "sourceTool": "AI-Bridge",
    "action": "FORWARDED",
    "originalStatus": 200,
    "originalLength": 452,
    "vulnerabilityDetected": true,
    "vulnerableRoles": ["Attacker_UserB"],
    "verdicts": {
      "Unauthenticated": "ENFORCED",
      "Attacker_UserB": "BYPASS"
    }
  }
]
```

---

#### 9. GET /api/records/{id}

Retrieves full replay data and granular role verdicts for a specific audit entry.

* **Path Parameters**:
  * `id` (integer, required): Audit record ID.
* **Response Status**:
  * `200 OK`: Full `AnalyzeResponseDto` matching `POST /api/analyze` response schema.
  * `404 Not Found`: Record ID does not exist.

---

### Model Context Protocol (MCP) Integration

Thorization functions as an MCP backend. AI assistants can register and call the following tool definitions to conduct access control testing autonomously.

#### Tool Mapping

| MCP Tool Name | HTTP Method | Endpoint | Description |
|---|---|---|---|
| `thorization_get_status` | `GET` | `/api/status` | Read engine status, interception mode, and filter rules. |
| `thorization_list_roles` | `GET` | `/api/roles` | List all attacker and peer test roles. |
| `thorization_create_role` | `POST` | `/api/roles` | Create a test role with custom headers or cookies. |
| `thorization_analyze_request` | `POST` | `/api/analyze` | Replay HTTP request against roles and evaluate access control verdicts. |
| `thorization_get_records` | `GET` | `/api/records` | Query past audit records with optional `vulnerableOnly` filter. |
| `thorization_get_record_detail` | `GET` | `/api/records/{id}` | Inspect full differential verdicts and notes for a specific record. |

#### Example MCP Tools Configuration

Standard JSON tool schemas for AI orchestrators:

```json
{
  "tools": [
    {
      "name": "thorization_get_status",
      "description": "Get Thorization Burp extension status, active filters, and role counts.",
      "inputSchema": {
        "type": "object",
        "properties": {}
      }
    },
    {
      "name": "thorization_analyze_request",
      "description": "Analyze an HTTP request across configured user roles to detect authorization bypasses (BOLA/IDOR).",
      "inputSchema": {
        "type": "object",
        "properties": {
          "url": { "type": "string", "description": "Target endpoint URL" },
          "method": { "type": "string", "description": "HTTP method (GET, POST, PUT, DELETE)", "default": "GET" },
          "headers": { "type": "object", "description": "Original request headers key-value pairs" },
          "body": { "type": "string", "description": "Request body payload" },
          "bypassFilter": { "type": "boolean", "description": "Bypass scope and static asset filters", "default": true },
          "testUnauthenticated": { "type": "boolean", "description": "Test unauthenticated access", "default": true }
        },
        "required": ["url"]
      }
    },
    {
      "name": "thorization_get_records",
      "description": "Query audit records and identify endpoints vulnerable to authorization bypass.",
      "inputSchema": {
        "type": "object",
        "properties": {
          "limit": { "type": "integer", "description": "Maximum number of records to return" },
          "vulnerableOnly": { "type": "boolean", "description": "Filter to vulnerable records only", "default": true }
        }
      }
    }
  ]
}
```

---

## Tests

Execute the unit test suite:

```bash
./gradlew test
```

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
