# ⚡ Thorization — Advanced Burp Suite Authorization & BOLA Testing Extension

<p align="center">
  <img src="https://img.shields.io/badge/Burp_Suite-Montoya_API-orange.svg" alt="Montoya API" />
  <img src="https://img.shields.io/badge/Java-21-blue.svg" alt="Java 21" />
  <img src="https://img.shields.io/badge/Gradle-8.x-02303A.svg" alt="Gradle" />
  <img src="https://img.shields.io/badge/Version-1.2.0-brightgreen.svg" alt="Version 1.2.0" />
  <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="License" />
</p>

**Thorization** is a high-performance, modern authorization security testing extension for **Burp Suite** built natively on the **Montoya API**. Designed for professional penetration testers, bug bounty hunters, and security engineers, Thorization solves the critical limitations of legacy extensions like *Autorize* and *Auth Analyzer*.

---

## 🚀 Key Features & Unique Selling Points (USPs)

### 1. 🛡️ Safe Request Interception & Drop Control
* **Destructive Action Prevention**: Automatically drop or pause state-changing client requests (`POST`, `PUT`, `DELETE`, `PATCH`) in Burp Proxy before they reach the server, while safely replaying authorization checks for configured attacker roles.
* **Flexible Interception Modes**:
  * `Drop Original & Replay (Safe Mode)`: Drops the victim socket request in Proxy and tests attacker roles in the background without modifying production/client state.
  * `Replay First, Then Allow`: Executes attacker tests synchronously first, then allows the original client request to proceed.
  * `Pass Through & Replay`: Standard asynchronous background authorization testing.
* **Granular Scope & Method Filters**: Toggle between *State-Changing Only* or *Drop ALL Requests (including GET)*.

### 2. 👥 Dynamic Multi-Role Access Control Matrix
* Configure **$N$ independent user roles** (e.g., *Admin*, *Manager*, *Doctor*, *Patient*, *Attacker*) with custom HTTP headers, authorization tokens, and session cookies.
* Dedicated **Unauthenticated Replay Toggle** to detect unauthenticated access / missing authentication filters.
* **Role Priority & Execution Sequencing**: Configure the exact sequential order in which roles are evaluated (`[▲ Up]`, `[▼ Down]`).
* **Selective Re-Testing**: Re-test specific selected URLs or all captured requests for newly added roles via an interactive role checklist modal.

### 3. 🧩 Pipeline Studio & Block-Based State Chains (USP 2 & 3)
* **Visual Scratch-Style Workflow Builder**: Build stateful BOLA (Broken Object Level Authorization) chains visually using modular blocks:
  * 🟦 **`RequestBlock`**: Custom HTTP templates with dynamic `{{VAR_NAME}}` placeholders and role credential binding.
  * 🟨 **`BranchBlock` (IF / ELSE)**: Conditional branches evaluating HTTP status codes, body text, regex matchers, or variable non-emptiness.
  * 🖥️ **`PipelineConsole`**: Real-time step-by-step execution trace and resolved variables inspection.
* **Cross-Role Variable Chaining**: Extract values (e.g. `$.id`, `$.id_unique`, `$.token`, `Location` headers) from Role A's request/response and automatically inject them into Role B's target parameters.
* **"⚡ Send to Pipeline Studio"**: Right-click any requests in the Analysis Table to auto-generate stateful workflow pipelines.

### 4. 📊 Real-Time Analytics & Statistics Dashboard
* Interactive dark/light-mode aware **JFreeChart Pie Chart** visualizing the live ratio of **Bypass**, **Enforced**, and **Manual** endpoints.
* Metric summary cards for rapid executive reporting.

### 5. 💾 Session State Persistence & Portability
* Full JSON **Session Export & Import** support. Save entire test sessions—including captured records, role matrices, custom pipelines, and execution traces—to `.json` files to share with team members or attach to pentest reports.

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
│       ├── Pipeline.java             # Pipeline scenario (steps, variables)
│       ├── RequestStep.java          # Template with {{VAR}} placeholder support
│       ├── ExtractionRule.java       # Variable extraction rule (JSONPath, Regex, Header)
│       └── ConditionBranch.java      # IF/ELSE conditional branch
├── core/                             # Business Logic & Replay Engine
│   ├── EngineController.java         # Coordinator (thread pool, sequencing, observers)
│   ├── HttpTrafficHandler.java       # Montoya ProxyRequestHandler & HttpHandler
│   ├── ReplayEngine.java             # Multi-role asynchronous replay dispatcher
│   ├── DetectionEngine.java          # Hybrid classification (Status, Length %, Regex)
│   └── pipeline/                     # Pipeline execution engines
│       ├── PipelineExecutor.java     # Asynchronous stateful chain executor
│       ├── VariableResolver.java     # {{VAR}} placeholder string substitution
│       └── JsonPathExtractor.java    # High-performance JSONPath & Regex parser
├── ui/                               # Java Swing Presentation Layer (4 Tabs)
│   ├── MainTabbedPane.java           # Main 4-Tab Container
│   ├── RolesPanel.java               # Tab 1: Roles Manager & Interception Controls
│   ├── TablePanel.java               # Tab 2: Interception & Analysis Table
│   ├── PipelineStudioPanel.java      # Tab 3: Pipeline Studio (Chains)
│   ├── StatisticsPanel.java          # Tab 4: Statistics Dashboard
│   └── components/                   # Renderers, dialogs, viewers, block cards
├── export/                           # Persistence & Session State
│   ├── SessionExporter.java          # Serializes session to JSON
│   └── SessionImporter.java          # Deserializes session from JSON
└── util/                             # Helpers
    ├── DiffUtils.java                # Similarity & length difference calculation
    └── HeaderUtils.java              # Header & Cookie parsing and injection
```

---

## 📋 Prerequisites

* **Java Development Kit (JDK)**: Java 21 or newer (Temurin, OpenJDK, Oracle).
* **Burp Suite**: Community or Professional Edition (version `2023.x` or newer supporting Montoya API).
* **Gradle**: Built-in `gradlew` wrapper included in the repository.

---

## 🛠️ Build & Setup Instructions

### 1. Clone the Repository
```bash
git clone https://github.com/<YOUR_USERNAME>/Thorization.git
cd Thorization
```

### 2. Build the Fat JAR (ShadowJar)
Burp Suite runs extensions in isolated classloaders. Thorization must always be built as a bundled **Fat-JAR** via Gradle Shadow:

* **On Windows (PowerShell / Command Prompt)**:
  ```powershell
  .\gradlew.bat clean build
  ```

* **On Linux / macOS**:
  ```bash
  ./gradlew clean build
  ```

### 3. Verify Output
The resulting fat JAR will be located at:
```text
app/build/libs/Thorization.jar  (~5.4 MB with bundled dependencies)
```

---

## 🔌 Installation in Burp Suite

1. Open **Burp Suite** (Professional or Community).
2. Go to the **Extensions** tab ➔ **Installed** sub-tab.
3. Click **Add**.
4. In the *Load Burp Extension* dialog:
   * **Extension type**: Select `Java`.
   * **Extension file (.jar)**: Click *Select file...* and choose `app/build/libs/Thorization.jar`.
5. Click **Next**.
6. You will see `Thorization extension loaded successfully` in the Output tab, and a new top-level **`Thorization`** tab will appear in Burp Suite!

---

## 📖 Quick Start Guide

### Step 1: Configure Your Roles (Tab 1)
1. Open the **Thorization** tab ➔ **Roles Manager**.
2. Click **+ Add New Role** (e.g., `Attacker (User B)`).
3. Paste the authorization headers (e.g., `Authorization: Bearer <token>`) or session cookies (`Cookie: session=xyz`) in the text area.
4. Select your desired **Safe Interception Mode** (e.g. `Drop Original & Replay (Safe Mode)` to avoid modifying test environments).

### Step 2: Intercept & Analyze (Tab 2)
1. In the **Interception & Analysis Table** tab, toggle **Thorization Engine: ACTIVE**.
2. Browse your target web/mobile application through Burp Proxy.
3. Thorization intercepts matching requests, evaluates attacker roles and unauthenticated replay, and displays color-coded verdicts:
   * 🔴 **BYPASS**: Attacker role received authorized content or status `200 OK`.
   * 🟢 **ENFORCED**: Target endpoint properly returned `401 Unauthorized` / `403 Forbidden`.
   * 🟠 **MANUAL**: Ambiguous response length or status requiring tester review.

### Step 3: Test Stateful BOLA Chains with Pipeline Studio (Tab 3)
1. Select 2 or more requests in the Analysis Table ➔ Right-Click ➔ **"⚡ Send to Pipeline Studio"**.
2. In **Pipeline Studio (Chains)**:
   * Set **Step 1** to create an object with **Role A**, extracting `$.id` ➔ `{{OBJECT_ID}}`.
   * Set **Step 2** with an `IF/ELSE` branch checking for `200 OK`.
   * Set **Step 3** to query or delete `{{OBJECT_ID}}` with **Role B**.
3. Click **▶ Run Pipeline** to execute the stateful chain and view the live trace in the console and main table!

---

## 🧪 Running Unit Tests

Run the complete test suite verifying detection algorithms, role sequencing, variable extraction, and session persistence:

```powershell
.\gradlew.bat test
```

---

## 📄 License

This project is licensed under the **MIT License**.
See the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Crafted with ⚡ for advanced API & Authorization Security Testing.
</p>
