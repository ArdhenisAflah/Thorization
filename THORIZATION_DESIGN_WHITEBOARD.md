# Thorization — Burp Suite Extension Architecture & Design Whiteboard

## 1. Executive Overview & Mission
**Thorization** is a high-performance authorization testing extension for Burp Suite (built on the modern **Montoya API**). It automates testing for Broken Object Level Authorization (BOLA/IDOR) and Multi-Role Access Control vulnerabilities.

### Unique Selling Points (Roadmap)
- **Batch 1 (Foundation & Multi-Role MVP) [COMPLETED]**:
  - Core Montoya integration, SOLID layout, dynamic role tabs, export/import, JFreeChart dashboard.
- **Batch 2 (Safe Request Interception & Drop Control + Role Sequencing) [COMPLETED]**:
  - Native Proxy request dropping (`ProxyRequestHandler.drop()`), Repeater pass-through, configurable state-changing method filters, traffic source tool tracking (`Proxy` vs `Repeater`), role priority execution ordering, and table Action/Source badges.
- **Batch 3 (USP 2 & 3 - Pipeline Studio, Variable Chaining & Auto Instance Lifecycle) [PAUSED / WIP]**:
  - Visual Scratch-style block builder (Tab 3), `RequestBlockCard` with `{{VAR}}` placeholder substitution, `BranchBlockCard` with `IF/ELSE` condition trees, `JsonPathExtractor` + `VariableResolver`, dynamic table badging (`[⚡ Pipeline]`), and full JSON import/export session compatibility.
  - *Pivot update (v1.3.1)*: Automated execution paused (`[WIP / Future Development]`). Visual scenario design cards retained for reference. Right-click pipeline send action removed from Analysis Table to focus project resources on high-speed core interception and AI MCP integration.
- **Maintenance / v1.2.1 (HiDPI & Responsive Layout Hardening) [COMPLETED]**:
  - Compound borders (`BorderFactory.createCompoundBorder`) across all titled panels to eliminate title/first-row text collision under HiDPI / Linux font renderers.
  - Scrollable `createConfigPanel` via `JScrollPane` and standardized minimum/preferred field heights (`28px`) to ensure zero component squishing across compact or tiled window viewports.
- **Batch 4 / v1.3.0 (AI Agent Bridge — REST API for LLMs & Autonomous Agents) [COMPLETED]**:
  - Dedicated bridge package (`thorization.bridge`) providing an embedded lightweight HTTP REST server (`com.sun.net.httpserver.HttpServer`) strictly bound to `127.0.0.1:1337/api/`.
  - Zero external HTTP dependencies to eliminate Burp Suite classloader conflicts.
  - AI endpoints for state check (`GET /api/status`), role management (`GET, POST, PUT, DELETE /api/roles`), automated request analysis under active rules (`POST /api/analyze`), and audit query (`GET /api/records`).

---

## 2. System Architecture & SOLID Structure

```
thorization/
├── ThorizationExtension.java         # BurpExtension entry point (Montoya API registration)
├── App.java                          # Standalone GUI preview launcher
├── bridge/                           # Batch 4: AI Agent Bridge (REST API)
│   ├── AiBridgeServer.java           # Embedded HTTP server bound to 127.0.0.1:1337
│   ├── AiBridgeController.java       # Coordinates rules, replay, roles & record conversion
│   ├── dto/                          # Bridge Data Transfer Objects
│   │   ├── StatusResponseDto.java    # Extension state & active filter rules
│   │   ├── RoleDto.java              # Role definition (name, headers, cookies, enabled)
│   │   ├── AnalyzeRequestDto.java    # HTTP request analysis payload (structured or raw)
│   │   ├── AnalyzeResponseDto.java   # Audit outcome, verdicts & similarity per role
│   │   └── AuditRecordSummaryDto.java# Audit table transaction summary
│   └── handlers/                     # Sun JDK HttpServer Endpoints
│       ├── BaseHttpHandler.java      # CORS, JSON serialization & error responses
│       ├── StatusHandler.java        # GET /api/status
│       ├── RoleHandler.java          # GET, POST /api/roles and PUT, DELETE /api/roles/{id}
│       ├── AnalyzeHandler.java       # POST /api/analyze (rule checks & multi-role replay)
│       └── RecordsHandler.java       # GET /api/records and GET /api/records/{id}
├── model/                            # Data Models & Entities
│   ├── UserRole.java                 # Role entity (name, enabled, header map, cookie string)
│   ├── AuthorizationCheckResult.java # Scan result entity per role (verdict, status, length, similarity)
│   ├── AuditRecord.java              # Master record for an intercepted HTTP or Pipeline transaction
│   ├── Verdict.java                  # Enum: BYPASS (Red), ENFORCED (Green), INDETERMINABLE (Orange)
│   ├── InterceptionMode.java         # Enum: DROP_ORIGINAL_AND_REPLAY, REPLAY_FIRST_THEN_ALLOW, PASS_THROUGH_AND_REPLAY
│   ├── OriginalAction.java           # Enum: DROPPED, PASSED, HELD_THEN_PASSED
│   ├── FilterConfig.java             # Scope, method, drop control, and file extension filters
│   └── pipeline/                     # Batch 3: Pipeline Studio Domain Models
│       ├── Pipeline.java             # Pipeline scenario (id, name, description, steps, variables)
│       ├── PipelineStep.java         # Base abstract step
│       ├── RequestStep.java          # HTTP request template with {{VAR}} placeholder support
│       ├── ExtractionRule.java       # Variable extraction rule (JSONPath, Regex, Header)
│       ├── ConditionBranch.java      # IF/ELSE condition logic branch
│       ├── ExtractionSource.java     # Enum: REQUEST_BODY, RESPONSE_BODY, REQUEST_HEADER, etc.
│       ├── ExtractionType.java       # Enum: JSON_PATH, REGEX, EXACT_HEADER
│       ├── StepType.java             # Enum: REQUEST, CONDITION_BRANCH, TEARDOWN
│       ├── StepExecutionRecord.java  # Per-step transaction and variable extraction trace
│       └── PipelineExecutionResult.java # Full pipeline execution outcome and verdict
├── core/                             # Business Logic & Replay Engine
│   ├── EngineController.java         # Master coordinator (toggle, thread pool, sequencing, pipelines)
│   ├── EngineListener.java           # Observer interface for UI reactivity
│   ├── HttpTrafficHandler.java       # Montoya HttpHandler & ProxyRequestHandler (safe drop & pass-through)
│   ├── ReplayEngine.java             # Executes replayed requests for each role & unauth in sequential order
│   ├── DetectionEngine.java          # Hybrid classification logic (Status, Length diff %, Regex matchers)
│   └── pipeline/                     # Batch 3: Pipeline Execution Engines
│       ├── PipelineExecutor.java     # Asynchronous executor running steps in order, resolving {{VAR}}
│       ├── VariableResolver.java     # Replaces {{VAR}} placeholders in URL, headers, and body
│       └── JsonPathExtractor.java    # Fast JSONPath and Regex extractor utility
├── ui/                               # Java Swing Presentation Layer
│   ├── MainTabbedPane.java           # Main 4-Tab Container registered in Burp Suite
│   ├── RolesPanel.java               # Tab 1: Managing N roles, headers, cookies, drop toggles
│   ├── TablePanel.java               # Tab 2: Analysis table, "Send to Pipeline", JSON Import/Export
│   ├── PipelineStudioPanel.java      # Tab 3: Visual Block Editor & Runner (Batch 3)
│   ├── StatisticsPanel.java          # Tab 4: JFreeChart Pie Chart showing authorization metrics
│   └── components/                   # Reusable UI components
│       ├── AuditTableModel.java      # Dynamic JTable model with Source & Action columns
│       ├── RequestResponseViewerPanel.java # Montoya HttpRequestResponse viewer & Pipeline trace
│       ├── RetestRolesDialog.java    # Dynamic modal checklist for selective role re-testing
│       ├── VerdictCellRenderer.java  # Color-coded badges for table verdicts and [⚡ Pipeline] tags
│       └── pipeline/                 # Batch 3: Visual Pipeline Block Cards
│           ├── RequestBlockCard.java # Visual block for HTTP request, role selector & extractions
│           ├── BranchBlockCard.java  # Visual block for IF/ELSE condition rules
│           └── PipelineConsolePanel.java # Live execution trace console & resolved variables table
├── export/                           # Persistence & Session State
│   ├── SessionExporter.java          # Serializes audit records, roles & pipeline scenarios to JSON
│   └── SessionImporter.java          # Deserializes JSON sessions back into table, roles & pipelines
└── util/                             # Helpers
    ├── DiffUtils.java                # Body length & similarity calculation
    └── HeaderUtils.java              # Header & Cookie parsing and replacement
```

---

## 3. Critical Packaging Policy: Fat-JAR Always! ⚠️

> **MANDATORY FOR ALL AGENTS & CONTRIBUTORS:**
>
> Burp Suite uses an isolated `URLClassLoader` per extension. Any dependency not bundled directly into the `.jar` will immediately throw a `ClassNotFoundException` (e.g., JFreeChart, Gson, Guava, or future libraries).

### Rules for Build & Dependencies:
1. **Thin JAR is permanently disabled** (`jar.enabled = false`) in `app/build.gradle`.
2. **Always build via `shadowJar`**: Running `.\gradlew.bat clean build` or `.\gradlew.bat shadowJar` produces **one single self-contained Fat-JAR**:
   ```
   app/build/libs/Thorization.jar (~5.3 MB)
   ```
3. **Montoya API Rule**:
   - `montoya-api` must **ALWAYS** be declared as `compileOnly` (`testImplementation` for unit tests) so it is NOT bundled into the JAR. Burp Suite provides Montoya at runtime.
4. **All Third-Party Libraries**:
   - Must be declared with `implementation` so `shadowJar` bundles them into `Thorization.jar`.

---

## 4. How to Build & Load in Burp Suite

### Build Command:
```powershell
.\gradlew.bat clean build
```

### Output Binary:
```
app/build/libs/Thorization.jar
```

### Loading in Burp Suite:
1. Open Burp Suite.
2. Navigate to **Extensions** tab ➔ **Installed** sub-tab.
3. Click **Add**.
4. Set **Extension Type**: `Java`.
5. Select `d:\Java-Project\Thorization\app\build\libs\Thorization.jar`.
6. Look for the new **Thorization** main tab in Burp Suite!

---
*Created and maintained as a living whiteboard for Thorization development.*
