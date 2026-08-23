# Contributing

Thank you for improving the Alitycs JVM SDK. Contributions should preserve wire compatibility,
Java interoperability, thread safety, and graceful lifecycle behavior.

## Before opening a pull request

- Use a GitHub issue for bugs or proposals that change public behavior.
- Use [private vulnerability reporting](SECURITY.md) for security findings.
- Keep changes within SDK capabilities already supported by the Alitycs platform.
- Add or update tests for every behavior change.

## Local checks

Use JDK 11+, the committed wrapper, Python 3.11 or newer, and Ruby 3.3 or newer:

```bash
./gradlew test
./gradlew koverVerify
./gradlew build
./gradlew publishToMavenLocal
./scripts/validate-coderabbit.sh
```

Kover enforces 90% line and 85% method coverage. Tests must remain deterministic and must not need
live credentials.

The SDK posts batches to `https://api.alitycs.com/events` with
`Authorization: Bearer <apiKey>`. Never commit credentials, customer data, Gradle build output, or
local environment files.

## Pull requests

Describe the user-visible effect, Kotlin and Java compatibility impact, and commands you ran. Keep
the changelog current for consumer-facing changes. By contributing, you agree that your
contribution is licensed under this repository's MIT License. Configure GitHub-verified commit
signing before pushing; commits merged into `main` must carry verified signatures.

### Automated review

CodeRabbit reviews human-authored pull requests in addition to the required GitHub Actions checks.
Resolve its blocking findings and wait for CodeRabbit to approve the current pull-request head.
The required `Alitycs CodeRabbit Gate` verifies that exact-head approval, so every new push must
finish its incremental review before the pull request can merge. The complete standalone policy
and trusted evaluator live here; see [CodeRabbit review gate](docs/coderabbit.md) for operations.

Supported bot accounts such as Dependabot, Renovate, and GitHub Actions are intentionally excluded
from CodeRabbit review. Those pull requests still need all required CI checks and approval from a
human maintainer on the latest commit. Review submissions and dismissals automatically reconcile
the gate; comment `/coderabbit-gate` only to recover from a missed GitHub event.

Administrative break-glass changes to review protections are reserved for service outages or urgent
security response. Record the reason in the pull request, restore the normal gate immediately, and
link a follow-up issue for any deferred review or remediation.
