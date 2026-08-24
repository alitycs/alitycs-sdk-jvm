# Contributing

Thank you for improving the Alitycs JVM SDK. Contributions should preserve wire compatibility,
Java interoperability, thread safety, and graceful lifecycle behavior.

## Before opening a pull request

- Use a GitHub issue for bugs or proposals that change public behavior.
- Use [private vulnerability reporting](SECURITY.md) for security findings.
- Keep changes within SDK capabilities already supported by the Alitycs platform.
- Add or update tests for every behavior change.

## Local checks

Use JDK 11+, the committed wrapper, Bash, Git, CPython 3.11 through 3.14, and Ruby 3.3 or newer:

```bash
./gradlew test
./gradlew koverVerify
./gradlew build
./gradlew publishToMavenLocal
./scripts/verify-workflow-pins.rb
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
signing if desired; commit signatures are optional and are not part of the required branch
baseline.

### Automated review

CodeRabbit automatically reviews ready pull requests, including dependency-bot updates, in addition
to the required GitHub Actions checks. Resolve blocking findings and wait for incremental review to
finish after every push. The native `CodeRabbit` status reports that review processing completed; it
does not by itself mean that CodeRabbit approved the pull request. Check the formal review state for
approval or requested changes.

Normal GitHub review protection remains authoritative and an eligible human approval can satisfy
it. Changes to `.coderabbit.yaml`, `.github/workflows/`, CODEOWNERS, or their validation scripts
also require code-owner approval. See [CodeRabbit reviews](docs/coderabbit.md) for the
repository-owned policy, validation commands, and branch-protection baseline.
