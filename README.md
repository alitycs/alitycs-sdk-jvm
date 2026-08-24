# Alitycs JVM SDK

[![CI](https://github.com/alitycs/alitycs-sdk-jvm/actions/workflows/ci.yml/badge.svg)](https://github.com/alitycs/alitycs-sdk-jvm/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Official open-source Kotlin-first SDK for sending product-analytics events to
[Alitycs](https://alitycs.com) from JVM 11+ applications. It uses `java.net.http.HttpClient`,
Kotlin coroutines, and `kotlinx.serialization`.

Current version: `1.0.0`.

## Installation

The Maven coordinates are `com.alitycs:alitycs-sdk-jvm:1.0.0`. Maven Central publication is not
advertised until the Alitycs namespace and signing credentials are configured. For local use:

```bash
./gradlew publishToMavenLocal
```

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("com.alitycs:alitycs-sdk-jvm:1.0.0")
}
```

Compiled JARs, a sources JAR, a Maven POM, and checksums are attached to each
[GitHub Release](https://github.com/alitycs/alitycs-sdk-jvm/releases).

## Kotlin quick start

```kotlin
import com.alitycs.sdk.Alitycs
import com.alitycs.sdk.AlitycsConfig

val analytics = Alitycs.init(
    AlitycsConfig(apiKey = System.getenv("ALITYCS_API_KEY"))
)

analytics.identify("usr_123", mapOf("plan" to "pro"))
analytics.track("signup_completed", mapOf("source" to "docs"))
analytics.captureError("checkout_failed", mapOf("provider" to "stripe"))

analytics.shutdown()
```

## Java quick start

```java
import com.alitycs.sdk.Alitycs;
import com.alitycs.sdk.AlitycsConfig;
import java.util.Map;

Alitycs analytics = Alitycs.init(
    new AlitycsConfig(System.getenv("ALITYCS_API_KEY"))
);

analytics.identify("usr_123", Map.of("plan", "pro"));
analytics.track("signup_completed", Map.of("source", "docs"));
analytics.captureError("checkout_failed", Map.of("provider", "stripe"));

analytics.shutdownBlocking();
```

## Trusted revenue events

Revenue ingestion is server-only and requires a secret key with the `revenue:write` scope:

```kotlin
import com.alitycs.sdk.RevenuePayload

analytics.trackRevenue(
    RevenuePayload.transaction(
        factId = "order_123",
        amount = "19.99",
        currency = "USD",
    )
)
```

Never expose a secret key in a client application.

## Configuration

| Option           | Default                          | Description                                                                        |
| ---------------- | -------------------------------- | ---------------------------------------------------------------------------------- |
| `apiKey`         | required                         | Publishable key for ordinary ingest, or a secret key for trusted server operations |
| `endpoint`       | `https://api.alitycs.com/events` | Worker ingestion endpoint                                                          |
| `flushInterval`  | `10000`                          | Batch flush interval in milliseconds                                               |
| `flushSize`      | `25`                             | Queue size that triggers a flush                                                   |
| `maxQueueSize`   | `1000`                           | Maximum queued events                                                              |
| `maxRetries`     | `3`                              | Retry attempts for retryable transport failures                                    |
| `sessionTimeout` | `1800000`                        | Inactivity timeout in milliseconds                                                 |
| `batching`       | `true`                           | Send queued batches or one event per request                                       |
| `debug`          | `false`                          | Enable SDK diagnostics                                                             |

Requests use `Authorization: Bearer <apiKey>` and `Content-Type: application/json`. The default
endpoint is the worker's `/events` ingestion route, not the tenant-scoped analytics read API.

## API surface

- `track`, `identify`, `reset`, `page`, and `captureError`
- `trackRevenue` for trusted server operations
- `setGlobalProperties`, `getGlobalProperties`, `removeGlobalProperties`, and
  `clearGlobalProperties`
- Suspending `flush` and `shutdown`
- Java-friendly `flushBlocking` and `shutdownBlocking`
- `pending`

Events are queued in memory, flushed by size or interval, and retried with exponential backoff.
Calls are best-effort after the configured retries are exhausted. Call `shutdown` during graceful
application termination so queued events are sent.

## Development

Requirements: JDK 11+, the committed Gradle wrapper, Bash, Git, Ruby 3.3 or newer, and CPython 3.11
through 3.14.

```bash
./gradlew test
./gradlew koverVerify
./gradlew build
./gradlew publishToMavenLocal
```

Kover enforces at least 90% line and 85% method coverage. The cross-SDK conformance runner is owned
by the parent Alitycs SDK workspace; `runConformance` explains how to provide it in a standalone
clone.

## Releases

An annotated `vMAJOR.MINOR.PATCH` or `vMAJOR.MINOR.PATCH-PRERELEASE` tag matching the full Gradle
project version runs the release workflow. It rejects tag commits outside reviewed `main` history,
builds without release credentials, and creates a GitHub Release with reproducible, attested
artifacts and SHA-256 checksums. See [Releasing](docs/RELEASING.md). Stable and prerelease examples
are `v1.1.0` and `v1.1.0-rc.1`.

## Community and security

- [Contributing](CONTRIBUTING.md)
- [CodeRabbit reviews](docs/coderabbit.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Security policy](SECURITY.md)
- [Support](SUPPORT.md)
- [Changelog](CHANGELOG.md)

## Related repository

- [Alitycs JavaScript SDKs](https://github.com/alitycs/alitycs-sdk-js)

## License

[MIT](LICENSE) © 2026 Alitycs Team.
