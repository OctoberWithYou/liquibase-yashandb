# Contributing to `liquibase-yashandb`

Thank you for your interest in contributing to the YashanDB Liquibase connector!

## Getting Started

1. Fork the repository on GitHub.
2. Clone your fork locally.
3. Make sure you have JDK 8+ and Maven 3.8+ installed.
4. Install the YashanDB JDBC driver into your local Maven repository:
   ```bash
   mvn install:install-file \
       -Dfile=yashandb-jdbc-1.9.24.jar \
       -DgroupId=com.yashandb.jdbc \
       -DartifactId=yashandb-jdbc \
       -Dversion=1.9.24 \
       -Dpackaging=jar
   ```
5. Build and run tests:
   ```bash
   mvn clean verify
   ```

## Reporting Bugs

Please open a GitHub issue with:

- YashanDB version (from `select * from v$version;`)
- JDBC driver version
- Liquibase version
- A minimal reproducible example (changelog + `liquibase.properties`)
- The failing SQL and the YashanDB error code / message

## Proposing New Extensions

When adding a new YashanDB-specific override:

1. Place `Database` classes under `com.yashandb.liquibase.database`.
2. Place `SqlGenerator` classes under `com.yashandb.liquibase.sqlgenerator` and
   override `getPriority()` / `supports()` so they only activate for
   `YashanDbDatabase`.
3. Place `SnapshotGenerator` classes under `com.yashandb.liquibase.snapshot`.
4. Register each new class in the corresponding
   `src/main/resources/META-INF/services/liquibase.*` file.
5. Document the reason for the override in a Javadoc `【重写原因】` block,
   following the existing style.

## Code Style

- Java 8 source / target.
- 4-space indentation, no tabs.
- Wrap at ~120 columns.
- Use the existing `YashanDb*` prefix for all class names.
- Keep `getShortName()` returning `"yashandb"` and `PRODUCT_NAME` as `"YashanDB"`.

## Pull Requests

- One feature / fix per PR.
- Keep the PR description focused on *why* the change is needed.
- Ensure `mvn clean verify` passes locally.
- The CI workflow will automatically run the build on JDK 8 / 11 / 17.

## License

By submitting a pull request you agree that your contributions will be licensed
under the [Apache License 2.0](LICENSE).
