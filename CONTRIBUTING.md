# Contributing

Thank you for improving the Alitycs JVM SDK. Contributions should preserve wire compatibility,
Java interoperability, thread safety, and graceful lifecycle behavior.

## Before opening a pull request

- Use a GitHub issue for bugs or proposals that change public behavior.
- Use [private vulnerability reporting](SECURITY.md) for security findings.
- Keep changes within SDK capabilities already supported by the Alitycs platform.
- Add or update tests for every behavior change.

## Local checks

Use JDK 11+ and the committed wrapper:

```bash
./gradlew test
./gradlew koverVerify
./gradlew build
./gradlew publishToMavenLocal
```

Kover enforces 90% line and 85% method coverage. Tests must remain deterministic and must not need
live credentials.

The SDK posts batches to `https://api.alitycs.com/events` with
`Authorization: Bearer <apiKey>`. Never commit credentials, customer data, Gradle build output, or
local environment files.

## Pull requests

Describe the user-visible effect, Kotlin and Java compatibility impact, and commands you ran. Keep
the changelog current for consumer-facing changes. By contributing, you agree that your
contribution is licensed under this repository's MIT License.
