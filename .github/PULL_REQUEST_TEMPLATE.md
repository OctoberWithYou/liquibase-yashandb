# Pull Request Template

<!-- Thank you for contributing to liquibase-yashandb! -->

## What does this PR change?

<!-- Briefly describe the bug being fixed or the feature being added. -->

## Why?

<!-- Explain the motivation. For example: YashanDB vXX emits <SQL> instead of
     the expected <SQL>, so we need to override <generator>. -->

## Checklist

- [ ] The change is covered by a unit test, or a manual test case is described below.
- [ ] Documentation (README / Javadoc) is updated where relevant.
- [ ] `mvn clean verify` passes locally on at least one JDK (8 / 11 / 17).
- [ ] If a new Liquibase extension point is registered, the corresponding
      `META-INF/services/liquibase.*` file is updated.
- [ ] No runtime behaviour change for non-YashanDB databases.

## Manual test (if applicable)

```bash
# paste the commands that exercise the change end-to-end
```

## Related issues

Closes #
