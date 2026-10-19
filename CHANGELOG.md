# Thorization — Patch & Change Tracker (Changelog)

All notable changes, patches, UI improvements, and bug fixes for the **Thorization** project are tracked in this file.

---

## [v1.3.2] - 2026-10-20 (Current)
### 🐛 Fixed & Refined (Session Export / Import & Viewer Data Integrity)
- **Resolved Blank Request & Response Viewers on Import**:
  - Restored full deserialization of HTTP request and response payloads from session JSON into Montoya `HttpRequest` and `HttpResponse` objects.
  - Added lazy reconstruction and raw string fallbacks in `AuthorizationCheckResult` and `AuditRecord`.
  - Preserved original record timestamps (`HH:mm:ss`) upon session import and auto-selected first row for immediate display.
- **Removed Bloat, Redundant Keys & Dummy Data**:
  - Excluded auto-generated sample pipelines (`BOLA Cross-Role Data Verification`) from session export if unedited.
  - Pruned redundant keys from exported JSON (`path`, `isStateChanging`, duplicate `roleId` inside role results map, and `isPipelineExecution` when false).
  - Maintained 100% backwards compatibility for legacy exported files.
- **Interactive Import Confirmation**:
  - Added Replace vs. Append confirmation modal in `TablePanel` before loading session files into an active session.

---

## [v1.3.1] - 2026-10-13
### 🔄 Refactored & Paused (Pipeline Studio Architectural Pivot & UI Streamlining)
- **Pipeline Studio Paused for Future Development**:
  - Re-designated Tab 3 as **"Pipeline Studio (WIP)"** to pivot project focus towards high-efficiency core features (Safe Drop interception, multi-role replay) and AI LLM (MCP) integration.
  - Added a prominent styled notification banner (`[WIP / Future Development]`) at the top of Tab 3 using clean plain text (eliminating unrendered Swing HTML tags in Burp Suite) within an amber theme-aware card.
  - Disabled live execution button (`▶ Run (Paused - WIP)`) with neutral disabled styling to prevent accidental trigger of complex state machine executions.
  - Updated `PipelineConsolePanel` with informative paused notice and `VERDICT: N/A` state.
- **Removed Unprofessional "(USP 1)" Labels**:
  - Cleaned up titled border in `RolesPanel` (`"Safe Request Interception & Drop Control"`), tooltips in `TablePanel`, javadocs, and comments across all core classes.
- **Enhanced URL Regex Filtering**:
  - Upgraded `FilterConfig.matchesUrl` to use case-insensitive `Pattern.compile(...).matcher(...).find()`, supporting flexible substring patterns, anchored patterns, and resilient error recovery.
- **Dedicated Request & Drop Control Unit Test Suite (`RequestDropControlManagerTest.java`)**:
  - Added 11 comprehensive unit tests with realistic dummy traffic data covering URL include/exclude regexes, static file filtering, state-changing drop target logic (POST/PUT/DELETE/PATCH vs Drop ALL), native Proxy drop actions (`DROP_ORIGINAL_AND_REPLAY`, `REPLAY_FIRST_THEN_ALLOW`, `PASS_THROUGH_AND_REPLAY`), Repeater pass-through, proxy message deduplication, and `RolesPanel` UI synchronization.
- **Preserved Core Models & State Persistence**:
  - Maintained full backwards compatibility and serialization support for `Pipeline`, `RequestStep`, `ConditionBranch`, `ExtractionRule`, and session JSON import/export schemas.

---

## [v1.3.0] - 2026-10-01
### 🚀 Added (AI Agent Bridge — REST API for Autonomous LLM Integration)
- **Dedicated Bridge Package (`thorization.bridge`)**:
  - Implemented embedded, zero-external-dependency HTTP REST API server using JDK standard `com.sun.net.httpserver.HttpServer`.
  - Bound strictly to localhost (`127.0.0.1:1337/api/`) to guarantee complete local isolation with zero network exposure.
  - Full CORS headers support (`OPTIONS`, `Access-Control-Allow-Origin: *`, `GET`, `POST`, `PUT`, `DELETE`).
- **REST Endpoints for AI Agents (Antigravity, Claude, Custom Automation)**:
  - **`GET /api/status`**: Reports extension state, version (`v1.3.0`), active rules, scope filters, target methods, regex filters, and role counts.
  - **`GET /api/roles`**: Returns all configured user roles with tokens, headers, cookies, and enabled states.
  - **`POST /api/roles`**: Allows AI agents to dynamically register new user roles with custom authentication headers and cookies.
  - **`PUT /api/roles/{id}`**: Modifies existing role credentials, names, headers, cookies, or activation status.
  - **`DELETE /api/roles/{id}`**: Deletes configured roles by ID.
  - **`POST /api/analyze`**: Submits HTTP requests (supports both structured JSON and raw HTTP text formats) for multi-role authorization audit:
    - Automatically verifies requests against active extension filter rules (scope, method filters, static file ignoring, regex).
    - Returns `filteredOut: true` with explanatory reason if rules drop the request, or supports explicit override via `bypassFilter: true`.
    - Dispatches base request, replays across all active configured roles and unauthenticated state, and returns comprehensive authorization verdicts (`BYPASS`, `ENFORCED`, `SIMILAR_ANOMALOUS`).
    - Supports both synchronous and asynchronous execution modes (`sync: true/false`).
  - **`GET /api/records`**: Queries transaction audit history, verdicts, status codes, and response lengths.
  - **`GET /api/records/{id}`**: Retrieves granular per-role verdict breakdowns, execution times, and similarity scores.
- **Burp Extension Lifecycle Integration**:
  - Automatically starts `AiBridgeServer` on `ThorizationExtension.initialize()` and gracefully tears down server/executors on extension unload.
  - Added startup logging banner displaying API bridge connection URL.
- **Automated Test Coverage**:
  - Added comprehensive unit and integration test suite `AiBridgeTest.java` verifying status, role CRUD, rule validations, raw HTTP request parsing, and end-to-end HTTP client interactions.

---

## [v1.2.1] - 2026-10-01
### 🐛 Fixed & 💅 Polished (HiDPI & Responsive Layout Enhancements)
- **Resolved TitledBorder Text Collision**:
  - Replaced raw `BorderFactory.createTitledBorder(...)` on `RolesPanel`, `PipelineStudioPanel`, and `StatisticsPanel` with `BorderFactory.createCompoundBorder(..., createEmptyBorder(...))` to eliminate text overlapping with the first row of input components across HiDPI and Linux font renderers.
- **Scrollable & Responsive Configuration Layout (Roles Manager)**:
  - Encapsulated the role editor and drop/filter controls (`createConfigPanel()`) in a smooth `JScrollPane` (`UNIT_INCREMENT = 16`).
  - Added explicit preferred heights (`28px`) for input text fields to prevent vertical collapsing/crushing on compact or tiled window sizes (e.g. Hyprland tiling).
  - Shortened left panel title to `Configured Roles` and optimized divider location to prevent title truncation.
- **Analysis Table Toolbar & Responsive Layout**:
  - Replaced `BorderLayout` in `TablePanel` toolbar with `GridBagLayout` (left-anchored engine controls and right-anchored filter/export controls) to eliminate component overlapping when window is tiled or running at 2x DPI scale.
  - Shortened toolbar text while preserving full context: `"Engine: OFF/ON"`, `"● Idle/Active"`, `"Drop Original (Safe)"`, `"Replay First, Then Allow"`, `"Pass Through (Standard)"`, `"Drop All"`, `"Clear"`, `"Search:"`, `"Export"`, and `"Import"`.
  - Added explicit minimum/preferred sizing on `searchFilterField` (`120x26`) and informative tooltips across all controls.
- **Pipeline Studio Overhaul (Dark Theme & Responsive UI)**:
  - Eliminated hardcoded white and light gray backgrounds (`Color.WHITE`, `Color(245, 247, 250)`, `Color(240, 243, 246)`) in `PipelineStudioPanel`, `cardsContainer`, `RequestBlockCard`, `BranchBlockCard`, and `PipelineConsolePanel`.
  - Added `UiUtils.getCardBackground()` providing dynamic, theme-aware elevated surface colors (`#222831` for HTTP requests, `#2a261e` for condition branches in Dark Mode).
  - Streamlined `RequestBlockCard`: Consolidated Headers, Body Template, and Variable Extractions into a 3-tab sub-panel (`payloadTabs`), reducing card height by ~60% and preventing UI cut-offs.
  - Streamlined `BranchBlockCard`: Structured IF conditions, side-by-side THEN/ELSE actions, and failure notes cleanly without overlapping.
  - Reorganized Top Toolbar: Created clean Scenario management on the left (`Scenario:`, `+ New`, `Save`, `Delete`, `▶ Run`) and action buttons on the right (`+ HTTP Step`, `+ Condition`), preventing button truncation.

---

## [v1.2.0] - 2026-09-01
### 🚀 Added (Batch 3 — USP 2 & 3: Pipeline Studio & Block-Based State Chains)
- **Pipeline Studio (Tab 3)**:
  - Visual Scratch-style block builder for designing stateful multi-step authorization tests.
  - 🟦 **`RequestBlockCard`**: Custom HTTP templates, method selection, dynamic `{{VAR_NAME}}` placeholders, role credential binding, and embedded extraction rules.
  - 🟨 **`BranchBlockCard`**: `IF / ELSE` logic branches evaluating status codes, body text, regex, or variable non-emptiness.
  - 🖥️ **`PipelineConsolePanel`**: Live execution trace console, resolved dynamic variables table, and instant BOLA verdict badges.
- **Core Pipeline Engine**:
  - `JsonPathExtractor`: Fast JSONPath evaluator (`$.id`, `$.data.id`, `$[0].id_unique`, `$.items[1].uuid`) and regex capture groups.
  - `VariableResolver`: Dynamic string substitution for `{{VAR}}` placeholders across URLs, headers, and request bodies.
  - `PipelineExecutor`: Asynchronous execution engine running chained steps with role credential injection.
- **Main Table & Viewer Integration**:
  - Added **"⚡ Send to Pipeline Studio"** context menu item on the Analysis Table to auto-populate workflows from selected requests.
  - Added stylized **`[⚡ Pipeline: <Name>]`** badge in the `Source` column rendered with distinct styling.
  - Configured `RequestResponseViewerPanel` to render formatted pipeline execution traces in the audit notes.
- **Import / Export Compatibility**:
  - Extended `SessionExporter` and `SessionImporter` to serialize and deserialize pipeline scenarios, variables, and step execution records.

---

## [v1.1.4] - 2026-09-01
### 💅 Refined (Proxy-Focused Safe Drop Control)
- **Scoped Drop Control Strictly to Proxy Traffic**:
  - Confined socket drop interception (`ProxyRequestReceivedAction.drop()`) specifically to **Burp Proxy** traffic (browser & mobile testing).
  - Configured **Burp Repeater** requests to execute and return responses normally without simulated drops, while maintaining clear `Source: Repeater` tagging and logging in the analysis table.

---

## [v1.1.3] - 2026-09-01
### 🚀 Added & 🐛 Fixed (Repeater Support & Traffic Origin Tracking)
- **Traffic Origin & Source Tool Tracking**:
  - Added `Source` column in the audit table (`Proxy` vs `Repeater`).
  - Added traffic source tracking to the `RequestResponseViewerPanel` audit summary notes.
- **Real-Time Extension Console Logging**:
  - Added informative logging to Burp Suite's extension console (`Extensions ➔ Installed ➔ Thorization ➔ Output`) indicating tool source, method, URL, and drop/pass actions taken.

---

## [v1.1.2] - 2026-09-01
### 🐛 Fixed (Reactive Drop Sync & Table Toolbar Drop Control)
- **Root Cause Resolution for Dropped Requests**:
  - Identified that UI radio buttons previously required an explicit "Apply Settings & Filters" button click, leaving `dropAllRequests = false` if the button was not clicked.
  - Implemented full reactive two-way auto-synchronization: checking or unchecking any mode, drop scope, or method filter immediately updates `FilterConfig` in memory.
- **Quick Drop Scope Toggle in Analysis Table**:
  - Added `[x] Drop All (inc. GET)` checkbox directly on the top toolbar of the **Analysis Table** next to the `Mode:` dropdown.
  - Changes made in the Table toolbar immediately synchronize with the Roles Manager tab via `EngineListener.onFilterConfigChanged`.

---

## [v1.1.1] - 2026-09-01
### 🐛 Fixed & 💅 Polished
- **Aggressive Drop Scope Toggle**:
  - Added a drop scope toggle in `RolesPanel`:
    - `Drop State-Changing Only (POST, PUT, DELETE, PATCH)` (Default)
    - `Drop ALL Requests (Aggressive: including GET)`
  - When aggressive mode is enabled, even `GET` requests are safely dropped in Burp Proxy while replaying.
- **Top Toolbar Streamlining**:
  - Removed redundant buttons (`Re-Test Selected`, `Re-Test All`, `Remove Selected`) from the top toolbar to resolve horizontal squishing and layout overflow.
  - All re-testing and removal functions remain fully accessible via the **Right-Click Context Menu** and <kbd>Delete</kbd> / <kbd>Backspace</kbd> keys.

---

## [v1.1.0] - 2026-09-01 (Batch 2 Milestone)
### 🚀 Added (Safe Request Interception & Drop Control)
- **Native Proxy Drop & Interception Modes**:
  - Implemented `InterceptionMode` enum:
    - `DROP_ORIGINAL_AND_REPLAY`: Automatically drops state-changing requests (`POST`, `PUT`, `DELETE`, `PATCH`) in Burp Proxy via `ProxyRequestReceivedAction.drop()` so victim data is never destroyed, while testing attacker roles safely.
    - `REPLAY_FIRST_THEN_ALLOW`: Replays attacker test requests before releasing the victim request.
    - `PASS_THROUGH_AND_REPLAY`: Standard pass-through mode.
  - Implemented `ProxyRequestHandler` in `HttpTrafficHandler` for native Proxy drop handling.
  - Added configurable state-changing method filters (`POST`, `PUT`, `DELETE`, `PATCH`).
- **Role Execution Ordering & Priority Sequencing**:
  - Added `Move Up` (▲) and `Move Down` (▼) controls in `RolesPanel` to customize the exact sequence in which roles are evaluated.
  - Engine executes replays strictly according to the configured role priority order.
- **UI & Analysis Table Enhancements**:
  - Added quick `InterceptionMode` dropdown directly in the `TablePanel` top toolbar for rapid switching.
  - Added dedicated `Action` badge column in `AuditTableModel` displaying color-coded `[DROPPED]`, `[PASSED]`, and `[HELD->PASSED]` tags.
  - Included `Original Action` status in the `RequestResponseViewerPanel` audit summary notes.

---

## [v1.0.6] - 2026-09-01
### 💅 Dark Theme & Statistics Polish
- **Dynamic Look & Feel Chart Theming**:
  - Replaced JFreeChart hardcoded white backgrounds (`#FFFFFF`) and black text with dynamic Swing UI color extraction (`UIManager.getColor("Panel.background")` / `Label.foreground`).
  - Darkened pie plot background, chart panel background, legend background, slice label background, and slice link lines to seamlessly blend with Burp Suite's Dark Mode.
  - Added dynamic theme change detection (`updateUI()`) so the chart automatically re-themes if the user switches Burp Suite display modes.

---

## [v1.0.5] - 2026-09-01
### 💅 UI & Usability Improvements
- **Scrollable Tab Layout & Mouse Wheel Navigation**:
  - Configured `JTabbedPane.SCROLL_TAB_LAYOUT` on `RequestResponseViewerPanel` and `MainTabbedPane` so tabs never wrap into multiple cluttered rows when managing dozens of roles.
  - Added native horizontal scroll navigation buttons (◀ / ▶).
  - Added mouse wheel listener on tab headers for fast, smooth tab switching.

---

## [v1.0.4] - 2026-09-01
### 💅 UI & Styling Improvements
- **Native Layout & Dialog Polish**:
  - Replaced raw HTML labels in `RetestRolesDialog` with clean native Swing title and subtitle components to prevent broken HTML rendering in Burp.
  - Expanded dialog dimensions to a spacious `520 x 440` and separated the "Select All / Deselect All" toolbar from the bottom action buttons.
  - Fixed button squishing and overlapping between action buttons ("Run Re-Test" & "Cancel").
- **Dark/Light Theme Compatibility**:
  - Removed hardcoded white backgrounds (`Color.WHITE`) from dialog checkbox lists and metric cards, allowing full native contrast adaptation with Burp Suite Dark Mode.

---

## [v1.0.3] - 2026-09-01
### 🚀 Added
- **Selective & Batch Re-Testing for New/Configured Roles**:
  - Added **"Re-Test Selected"** button and **"Re-Test All"** button in `TablePanel` toolbar.
  - Added **"Re-Test Selected Request(s)..."** and **"Re-Test All Captured Requests..."** to right-click context menu.
  - Created `RetestRolesDialog` modal showing a dynamic checklist of all $N$ configured user roles + unauthenticated toggle with "Select All" / "Deselect All" helpers.
  - Added asynchronous `EngineController.retestRecords()` background re-auditing that updates table rows, role tabs, and statistics in real-time.

---

## [v1.0.2] - 2026-09-01
### 🚀 Added
- **Selective Item Removal**:
  - Added **"Remove Selected"** button on the toolbar in `TablePanel`.
  - Added support for multiple interval selection (`Ctrl + Click`, `Shift + Click`).
  - Added right-click context menu (**"Remove Selected Item(s)"** and **"Clear All Records"**) on the audit table.
  - Added keyboard shortcut: <kbd>Delete</kbd> / <kbd>Backspace</kbd> keys immediately remove selected records.
  - Connected record removal to `EngineController` and `StatisticsPanel` to dynamically recalculate statistics and pie chart.

### 🐛 Fixed
- **Viewer Stale Data Cleanup**:
  - `RequestResponseViewerPanel` now auto-clears all editors, status labels, and notes when the table is cleared, when selected rows are deleted, or when search filters deselect rows.

---

## [v1.0.1] - 2026-09-01
### 🚀 Added
- **Dynamic Dedicated Role Tabs**:
  - Replaced the single grouped "Role Replayed Traffic" tab with individual dedicated tabs for every configured role ($N$ users).
  - Each role tab contains independent Request/Response editors and a color-coded status/verdict banner.
  - Added live role synchronization when roles are created, updated, or removed.

---

## [v1.0.0] - 2026-09-01 (Initial Batch 1 MVP)
### 🚀 Added
- **Project Foundation & Build**:
  - Configured Gradle with Java 21, Montoya API `2024.12` (`compileOnly`), JFreeChart `1.5.4`, and Gson `2.11.0`.
  - Configured ShadowJar plugin to enforce Fat-JAR (`app/build/libs/Thorization.jar`) and disabled thin JARs.
- **SOLID Architecture & Core Engines**:
  - Implemented `DetectionEngine` (hybrid status code + body length threshold + error signatures + similarity).
  - Implemented `ReplayEngine` (asynchronous multi-role and unauthenticated replay).
  - Implemented `HttpTrafficHandler` (Montoya HTTP traffic interceptor for Proxy/Repeater).
  - Implemented `EngineController` with background worker pool and observer listeners.
- **User Interface (3 Dedicated Tabs)**:
  - Tab 1: `RolesPanel` (Roles Manager & Filter configuration).
  - Tab 2: `TablePanel` (Master ON/OFF toggle, colored JTable, JSplitPane Request/Response viewers, JSON session Export/Import).
  - Tab 3: `StatisticsPanel` (Interactive JFreeChart Pie Chart & metric cards).
- **Standalone Development Preview**:
  - Added `App.java` for standalone GUI preview.
