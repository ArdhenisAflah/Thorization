## Description

Please include a summary of the changes and the related issue. Highlight relevant motivation, context, and any architectural considerations.

Fixes #(issue)

---

## Type of Change

Please delete options that are not relevant:

- [ ] 🐛 Bug fix (non-breaking change which fixes an issue)
- [ ] ✨ New feature (non-breaking change which adds functionality)
- [ ] 💥 Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] 📝 Documentation update
- [ ] ⚡ Performance improvement
- [ ] 🔧 Refactoring / Code hygiene

---

## Verification & Testing

Please describe the tests that you ran to verify your changes:

- [ ] **Automated Test Suite**: Ran `./gradlew test` and all unit tests passed cleanly.
- [ ] **Fat JAR Build**: Ran `./gradlew shadowJar` and built `app/build/libs/Thorization.jar` without dependency conflicts.
- [ ] **Manual Burp Suite Test**: Loaded the generated JAR into Burp Suite (Extensions > Installed) and verified UI behavior and console output.

---

## Checklist

- [ ] My code follows the style guidelines of this project.
- [ ] I have performed a self-review of my own code.
- [ ] I have commented my code, particularly in hard-to-understand areas.
- [ ] I have updated the documentation / README / CHANGELOG accordingly.
- [ ] My changes generate no new compiler warnings or unhandled exceptions.
- [ ] I have added tests that prove my fix is effective or that my feature works.
