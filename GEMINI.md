# Thorization — AI Agent Guidelines & Development Rules

## 1. Patch & Change Tracking Policy (MANDATORY)
Whenever making any patch, bug fix, feature addition, or refactoring to the codebase:
- **`#patch` Trigger Rule**: Whenever `#patch` appears anywhere in the user's prompt (at the beginning or in the sentence), immediately associate it with a formal patch workflow.
- **Always update [`CHANGELOG.md`](file:///d:/Java-Project/Thorization/CHANGELOG.md)** with a new version entry or bullet point summarizing the change under `Added`, `Changed`, or `Fixed`.
- **Always keep [`THORIZATION_DESIGN_WHITEBOARD.md`](file:///d:/Java-Project/Thorization/THORIZATION_DESIGN_WHITEBOARD.md)** synchronized with the current architectural state and roadmap.

## 2. Packaging & Build Policy (Fat-JAR Always)
- Burp Suite runs with isolated classloaders. **Never produce or deploy thin JARs**.
- All dependencies must be bundled via `shadowJar` into `app/build/libs/Thorization.jar`.
- `montoya-api` must remain `compileOnly` so it uses Burp Suite's runtime implementation.
- Always verify builds with `.\gradlew.bat clean build` or `.\gradlew.bat test`.

## 3. Architecture & Code Style
- Follow clean SOLID principles, modular package structure (`model`, `core`, `ui`, `export`, `util`).
- Ensure thread safety in `EngineController` and proper dispatching to Swing EDT (`SwingUtilities.invokeLater`).
- Keep code clean, readable, and well-commented.
