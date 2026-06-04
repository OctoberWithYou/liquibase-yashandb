# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 1.0.x   | ✅ Yes             |

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for security problems.

Instead, send an email with a description of the issue to
[security@yashandb.com](mailto:security@yashandb.com) (or the address listed
in the repository's `SECURITY.md` on GitHub). Include:

- affected version(s) of `liquibase-yashandb`
- Liquibase and YashanDB JDBC versions you tested against
- steps to reproduce
- any PoC / proof-of-concept

We will acknowledge receipt within 5 business days and work with you to
triage, fix, and disclose the issue following responsible-disclosure
practices.

## Dependency Updates

`liquibase-core` and `yashandb-jdbc` are declared with `provided` scope; the
consumer is responsible for their versions. The connector itself has no
transitive runtime dependencies.

If a security advisory affects Liquibase or the YashanDB JDBC driver,
upgrade them in your consuming project. Dependabot will open PRs against
this repository when the pinned versions in `pom.xml` have known
vulnerabilities.
