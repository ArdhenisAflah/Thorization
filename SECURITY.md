# Security Policy

The **Thorization** project takes the security of its code, dependencies, and users seriously. As an application security and penetration testing tool, maintaining software integrity and preventing unintentional side-effects is paramount.

---

## Supported Versions

We provide security updates and patches for the current release branch. Users are encouraged to stay up-to-date with the latest published release.

| Version | Supported          |
| ------- | ------------------ |
| 1.3.x   | :white_check_mark: |
| 1.2.x   | :x:                |
| < 1.2.0 | :x:                |

---

## Reporting a Vulnerability

If you discover a security vulnerability in Thorization, please report it privately. **Do NOT report security vulnerabilities through public GitHub issues, discussions, or social media channels.**

### Preferred Method: GitHub Private Vulnerability Reporting
You can submit a confidential advisory directly to the repository maintainer through GitHub:
1. Navigate to the [Thorization Security Advisories](https://github.com/ArdhenisAflah/Thorization/security/advisories) page.
2. Click **Report a vulnerability**.
3. Fill out the report form with details of the vulnerability.

### Alternative Method: Direct Email
If you are unable to use GitHub Advisories, you may email the maintainer directly at:
- **aflahdenis9@gmail.com**

Please include:
- A clear description of the vulnerability and its potential security impact.
- Exact steps to reproduce the issue (including sample HTTP requests or configuration payloads, if applicable).
- The operating system, Java version, and Burp Suite edition tested on.
- Any suggested fixes, patches, or mitigations.

---

## Response Process & SLAs

When a vulnerability report is submitted:

1. **Acknowledgment**: We will acknowledge receipt of your report within **48 hours**.
2. **Assessment & Validation**: The maintainer will investigate the issue, reproduce the vulnerability, and assess the severity.
3. **Coordination**: We will coordinate with you on the proposed patch and agree on a timeline for responsible disclosure.
4. **Resolution**: A security patch will be tagged and released in a new minor/patch version, accompanied by an advisory acknowledging your responsible disclosure (unless you prefer to remain anonymous).

---

## Responsible Security Testing

Thorization is designed strictly for authorized security assessments, defensive research, and penetration testing on systems where you have explicit permission to test. Please exercise responsible disclosure when reporting issues.
