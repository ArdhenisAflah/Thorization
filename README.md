# ⚡ Thorization — Advanced Burp Suite Authorization & BOLA Testing Extension

<p align="center">
  <img src="https://img.shields.io/badge/Burp_Suite-Montoya_API-orange.svg" alt="Montoya API" />
  <img src="https://img.shields.io/badge/Java-21-blue.svg" alt="Java 21" />
  <img src="https://img.shields.io/badge/Gradle-9.x-02303A.svg" alt="Gradle" />
  <img src="https://img.shields.io/badge/Version-1.3.2-brightgreen.svg" alt="Version 1.3.2" />
  <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="License" />
</p>

**Thorization** is a high-performance, modern authorization security testing extension for **Burp Suite** built natively on the **Montoya API**. Designed for professional penetration testers, bug bounty hunters, and security engineers, Thorization solves the critical limitations of legacy extensions like *Autorize* and *Auth Analyzer* by introducing non-destructive request interception, multi-role state matrixing, AI integration, and robust session persistence.

---

## 🚀 Key Features & Unique Selling Points (USPs)

### 1. 🛡️ Safe Request Interception & Drop Control
* **Destructive Action Prevention**: Automatically drop or pause state-changing client requests (`POST`, `PUT`, `DELETE`, `PATCH`) in Burp Proxy before they reach the server, while safely replaying authorization checks for configured attacker roles.
* **Flexible Interception Modes**:
  * `Drop Original (Safe)`: Drops the victim socket request in Proxy and tests attacker roles in the background without modifying production/client state.
  * `Replay First, Then Allow`: Executes attacker tests synchronously first, then allows the original client request to proceed.
  * `Pass Through (Standard)`: Standard asynchronous background authorization testing.
* **Granular Scope & Method Filters**: Toggle between *State-Changing Only* or *Drop ALL Requests (including GET)*.

### 2. 👥 Dynamic Multi-Role Access Control Matrix
* Configure **$N$ independent user roles** (e.g., *Admin*, *Manager*, *Doctor*, *Patient*, *Attacker*) with custom HTTP headers, authorization tokens, and session cookies.
* **Sequential Role Duplication**: Duplicate existing roles with smart incremental naming (`User`, `User1`, `User2`) while preserving headers and cookie states.
* **Dedicated Unauthenticated Replay**: Always verify if endpoints are accessible anonymously without authentication headers.
* **Role Priority & Execution Sequencing**: Configure the exact sequential order in which roles are evaluated (`[▲ Up]`, `[▼ Down]`).
* **Selective Re-Testing**: Re-test specific selected URLs or all captured requests for newly added or updated roles via an interactive checklist modal.

### 3. 💾 Full-Fidelity Session Persistence & Portability (Enhanced in v1.3.2)
* **Native Request & Response Restoral**: Exported sessions (`.json`) preserve complete raw HTTP requests and responses. Importing a session immediately restores full interactive editors across Original Traffic, each Role Replay, and Unauthenticated tests.
* **Zero-Bloat JSON Schema**: Redundant keys (such as `path`, `isStateChanging`, duplicate IDs, and empty execution traces) are pruned. Auto-generated sample pipelines are excluded unless customized by the user.
* **Interactive Import Modes**:
  * **Replace Mode**: Clears existing captured records for a clean workspace and loads imported data.
  * **Append Mode**: Merges imported session records and unique roles into the active table without overwriting in-flight testing.
* **Rock-Solid Backwards Compatibility**: Reads legacy Thorization session exports transparently without breaking older test archives.
* **Thread-Safe UI Loading**: Safe asynchronous EDT row selection prevents `Row index out of range` exceptions even under active table search filters.

### 4. 🤖 AI Agent Bridge (Local REST API Server)
* **Embedded Local HTTP Server**: Runs directly on `127.0.0.1:1337/api/` with zero external dependencies.
* **Autonomous LLM / MCP Integration**: Allows local AI agents, scripts, and automation tools to programmatically query audit records, manage user roles, and trigger authorization replays.
* **Strict Security Isolation**: Bound exclusively to the loopback interface (`localhost`) with complete CORS header handling for safe browser and tool interactions.

### 5. 🧩 Pipeline Studio & Block-Based State Chains (WIP)
* **Visual Stateful BOLA Workflows**: Build complex multi-step authorization test chains visually:
  * 🟦 **`RequestStep`**: Custom HTTP templates with dynamic `{{VAR_NAME}}` placeholders and role credential binding.
  * 🟨 **`ConditionBranch` (IF / ELSE)**: Conditional branches evaluating HTTP status codes, body text, or regex matchers.
  * 🖥️ **`PipelineConsole`**: Step-by-step execution trace and resolved variables inspection.
* **Cross-Role Variable Extraction**: Extract IDs, CSRF tokens, or headers from Role A's responses and inject them into Role B's request payloads.

### 6. 📊 Real-Time Analytics & Statistics Dashboard
* Interactive dark/light-mode aware **JFreeChart Pie Chart** visualizing the live ratio of **Bypass**, **Enforced**, and **Manual** endpoints.
* Metric summary cards for rapid executive reporting and pentest documentation.

---

## 🏛️ System Architecture

```
thorization/
├── ThorizationExtension.java         # BurpExtension entry point (Montoya API registration)
├── App.java                          # Standalone GUI preview launcher
├── model/                            # Data Models & Entities
│   ├── UserRole.java                 # Role entity (headers map, raw cookies, toggles)
│   ├── AuthorizationCheckResult.java # Scan result per role (verdict, status, similarity, length)
│   ├── AuditRecord.java              # Master transaction record (Source, Action, Trace)
│   ├── Verdict.java                  # Enum: BYPASS (Red), ENFORCED (Green), INDETERMINABLE (Orange)
│   ├── InterceptionMode.java         # Drop / Replay-First / Pass-Through modes
│   ├── OriginalAction.java           # DROPPED / PASSED / HELD_THEN_PASSED
│   ├── FilterConfig.java             # Scope, method, drop control, regex filters
│   └── pipeline/                     # Pipeline Studio domain entities
│       ├── Pipeline.java             # Pipeline scenario (steps, variables, sample flag)
│       ├── RequestStep.java          # Template with {{VAR}} placeholder support
│       ├── ExtractionRule.java       # Variable extraction rule (JSONPath, Regex, Header)
│       └── ConditionBranch.java      # IF/ELSE conditional branch
├── core/                             # Business Logic & Replay Engine
│   ├── EngineController.java         # Coordinator (thread pool, sequencing, replace/append import)
│   ├── HttpTrafficHandler.java       # Montoya ProxyRequestHandler & HttpHandler
│   ├── ReplayEngine.java             # Multi-role asynchronous replay dispatcher
│   ├── DetectionEngine.java          # Hybrid classification (Status, Length %, Regex)
│   └── pipeline/                     # Pipeline execution engines
│       ├── PipelineExecutor.java     # Asynchronous stateful chain executor
│       ├── VariableResolver.java     # {{VAR}} placeholder string substitution
│       └── JsonPathExtractor.java    # High-performance JSONPath & Regex parser
├── bridge/                           # AI Agent Bridge REST API
│   ├── AiBridgeServer.java           # Embedded localhost HTTP REST API (port 1337)
│   └── handlers/                     # REST API route handlers (/records, /roles, /status)
├── ui/                               # Java Swing Presentation Layer
│   ├── MainTabbedPane.java           # Main Tab Container
│   ├── RolesPanel.java               # Tab 1: Roles Manager & Interception Controls
│   ├── TablePanel.java               # Tab 2: Interception & Analysis Table
│   ├── PipelineStudioPanel.java      # Tab 3: Pipeline Studio (WIP)
│   ├── StatisticsPanel.java          # Tab 4: Statistics Dashboard
│   └── components/                   # Renderers, dialogs, viewers, table models
├── export/                           # Persistence & Session State
│   ├── SessionExporter.java          # Serializes pruned, clean session to JSON
│   └── SessionImporter.java          # Deserializes session JSON with lazy reconstruction
└── util/                             # Helpers
    ├── DiffUtils.java                # Similarity & length difference calculation
    └── HeaderUtils.java              # Header & Cookie parsing and injection
```

---

## 📋 Prerequisites

* **Java Development Kit (JDK)**: Java 21 or newer (Temurin, OpenJDK, Oracle).
* **Burp Suite**: Community or Professional Edition (`2023.x` or newer supporting Montoya API).
* **Gradle**: Built-in `gradlew` wrapper included in the repository.

---

## 🛠️ Build & Installation

### 1. Clone the Repository
```bash
git clone https://github.com/ArdhenisAflah/Thorization.git
cd Thorization
```

### 2. Build the Bundled Extension (ShadowJar)
Burp Suite runs extensions in isolated classloaders. Always build the bundled Fat-JAR using Gradle:

* **On Linux / macOS**:
  ```bash
  ./gradlew shadowJar
  ```
* **On Windows**:
  ```powershell
  .\gradlew.bat shadowJar
  ```

The generated JAR will be located at:
```text
app/build/libs/Thorization.jar  (~5.3 MB)
```

### 3. Load into Burp Suite
1. In Burp Suite, navigate to **Extensions** ➔ **Installed**.
2. Click **Add**.
3. Under **Extension Details**, choose:
   - **Extension type**: `Java`
   - **Extension file (.jar)**: `app/build/libs/Thorization.jar`
4. Click **Next**. The extension will load, and a top-level **Thorization** tab will appear!

---

## 📖 Quick Start Guide

### Step 1: Configure Your Roles (Tab 1: Roles Manager)
1. Open **Thorization** ➔ **Roles Manager**.
2. Type your attacker's role credentials directly into the form (e.g. `Role Name: Attacker B`, custom cookies, and auth headers).
3. Click **Save Role Changes**.
4. Choose your **Interception Mode** (e.g., `Drop Original (Safe)` to prevent test traffic from altering backend application state).

### Step 2: Intercept & Replay (Tab 2: Interception & Analysis Table)
1. In **Interception & Analysis Table**, toggle the power button to **ON**.
2. Browse your target web application through Burp Proxy.
3. Matching requests are evaluated against all configured roles and unauthenticated replay in real-time.
4. Review color-coded verdicts:
   - 🔴 **BYPASS**: Attacker role received authorized response content (`200 OK`).
   - 🟢 **ENFORCED**: Server rejected attacker role (`401 Unauthorized` / `403 Forbidden`).
   - 🟠 **MANUAL**: Status code matched but response body changed significantly, requiring tester review.
5. Click any row to inspect original vs. replayed requests and responses side-by-side in the native viewer editors.

### Step 3: Export & Share Sessions
1. Click **Export** to save captured requests, verdicts, and role settings to a `.json` file.
2. Click **Import** at any time to reload a previous session. Choose **Replace** for a fresh view or **Append** to merge with current traffic.

---

## 🧪 Running Unit Tests

Thorization includes a comprehensive unit test suite covering detection engines, drop controllers, session serialization, and thread safety:

```bash
./gradlew test
```

---

## 📄 License

This project is licensed under the **MIT License**.
See the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Crafted with ⚡ for advanced API & Authorization Security Testing.
</p>
