# Contributing to Thorization

Thank you for your interest in contributing to **Thorization**! We welcome bug reports, feature suggestions, documentation improvements, and pull requests from the community.

Please take a moment to review this document before submitting your contribution.

---

## Code of Conduct

This project and everyone participating in it is governed by the [Thorization Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to **aflahdenis9@gmail.com**.

---

## How Can I Contribute?

### 1. Reporting Bugs

Before creating a bug report, please check existing [Issues](https://github.com/ArdhenisAflah/Thorization/issues) to see if the problem has already been reported.

If you find a new bug, please open an issue using the [Bug Report Template](.github/ISSUE_TEMPLATE/bug_report.md) and include:
- Extension version (e.g., `v1.3.2`)
- Burp Suite edition & version (Community or Professional, e.g., `2026.x`)
- Operating system and Java runtime version (`java -version`)
- Clear, step-by-step reproduction instructions
- Relevant console logs from Burp Suite (**Extensions** > **Installed** > **Thorization** > **Errors** or **Output** tabs)

### 2. Suggesting Features & Improvements

Feature ideas are always welcome! Please submit a feature request using our [Feature Request Template](.github/ISSUE_TEMPLATE/feature_request.md) describing:
- The pain point or problem you are trying to solve
- The proposed solution, UI flow, or heuristic enhancement
- Any alternative solutions or workarounds you've considered

### 3. Submitting Pull Requests

We follow standard GitHub Pull Request workflows. To contribute code:

1. **Fork the repository** on GitHub.
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/<your-username>/Thorization.git
   cd Thorization
   ```
3. **Create a topic branch** from `main`:
   ```bash
   git checkout -b feat/my-new-feature
   ```
4. **Make your changes** following our coding standards.
5. **Verify that tests pass and the fat JAR builds cleanly**:
   ```bash
   ./gradlew test shadowJar
   ```
6. **Commit your changes** using [Conventional Commits](#commit-message-guidelines).
7. **Push your branch** to your GitHub fork:
   ```bash
   git push origin feat/my-new-feature
   ```
8. **Open a Pull Request** against `ArdhenisAflah/Thorization` on the `main` branch.

---

## Development Environment & Setup

### Prerequisites

- **Java Development Kit (JDK) 21** or newer (OpenJDK, Temurin, or Amazon Corretto).
- **Gradle 8.x / 9.x** (the bundled `./gradlew` wrapper will handle Gradle automatically).
- **Burp Suite Professional or Community Edition** (v2023.12+ recommended for full Montoya API support).

### Building the Project

Compile the source code and build the standalone Fat JAR:

```bash
./gradlew shadowJar
```

The resulting extension JAR will be located at:
```
app/build/libs/Thorization.jar
```

### Running Tests

Execute the unit and integration test suite:

```bash
./gradlew test
```

Test reports are generated in HTML format under:
```
app/build/reports/tests/test/index.html
```

### Manual Testing in Burp Suite

1. Open Burp Suite.
2. Go to **Extensions** > **Installed**.
3. Click **Add**.
4. Set **Extension type** to `Java`.
5. Browse and select `app/build/libs/Thorization.jar`.
6. Inspect the **Output** and **Errors** tabs to confirm the extension initialized without exceptions.

---

## Coding Guidelines

1. **Montoya API First**: Always use native Burp Suite Montoya API interfaces (`burp.api.montoya.*`). Avoid legacy Extender interfaces or reflection hacks.
2. **Zero-Bloat Dependency Policy**: Avoid adding heavy external libraries. Standard JDK 21 libraries (`java.net.http`, `java.util.concurrent`, etc.) and bundled core utilities (`Gson`, `FlatLaf`) should be preferred whenever possible.
3. **Non-Blocking UI**: Never execute network requests or heavy computations on the Swing Event Dispatch Thread (EDT). Use background virtual threads or executors.
4. **Thread Safety & Memory Hygiene**: Clear audit listeners, cancel pending executor futures, and avoid unbounded in-memory data structures that could cause heap exhaustion during prolonged pentests.
5. **UI Consistency**: Ensure all custom Swing panels respect FlatLaf dark and light themes, maintaining legible font sizing and responsive layout scaling across different screen DPIs.

---

## Commit Message Guidelines

We adhere to [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short description>
```

### Common Types:
- `feat`: A new feature or user-facing capability
- `fix`: A bug fix
- `docs`: Documentation changes (README, guides)
- `refactor`: Code reorganization with no functional or behavioral change
- `test`: Adding or updating unit tests
- `ci`: Changes to CI/CD workflows or build scripts
- `perf`: Performance improvements

### Example:
```
feat(mutation): add support for nested array JSON path substitution
fix(ui): prevent button truncation on high-DPI displays
docs: update BApp installation walkthrough
```

---

## License

By contributing to Thorization, you agree that your contributions will be licensed under the project's [MIT License](LICENSE).
