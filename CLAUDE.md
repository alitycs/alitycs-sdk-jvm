# CLAUDE.md — alitycs-sdk-jvm

## Project Overview

Kotlin-first JVM SDK (v1.0.0) for the Alitycs Analytics Platform: event tracking, batching, sessions, HTTP transport with retry. Java interop via `@JvmStatic`.

## Commands

```bash
./gradlew test             # All tests (JUnit 5)
./gradlew build            # Full build + tests
./gradlew compileKotlin    # Compile-check only
```

## Architecture

```
src/main/kotlin/com/alitycs/sdk/
├── Alitycs.kt          # Main SDK class + companion convenience API
├── AlitycsConfig.kt    # Config data class with defaults
├── Types.kt            # AnalyticsEvent, BatchPayload, EventContext, EventType
├── HttpTransport.kt    # HTTP POST + exponential backoff retry
├── BatchManager.kt     # Queue + flush-on-size + coroutine timer
├── SessionManager.kt   # Session/anonymous ID + timeout rotation
├── Context.kt          # JVM/OS metadata (locale, timezone, os, jvm version)
└── Utils.kt            # generateId(), serializeProperties()

src/test/kotlin/com/alitycs/sdk/
└── integration/        # E2E tests with embedded HTTP server
```

### Event Processing Pipeline

1. `track()`/`identify()`/`page()` → `enqueue()`
2. `enqueue()` builds an `AnalyticsEvent` (with IDs, session, context, timestamp)
3. If batching enabled: `BatchManager.add()` queues the event, auto-flushes at `flushSize` or `flushInterval`
4. `BatchManager.flush()` wraps events in a `BatchPayload` and calls `HttpTransport.send()`
5. If batching disabled: immediately wraps in `BatchPayload` and sends

## Code Conventions

- Language: Kotlin-first, Java interop via `@JvmStatic`, `@JvmOverloads`, `flushBlocking()`/`shutdownBlocking()`
- Package: `com.alitycs.sdk`
- JDK target: 11+
- HTTP: `java.net.http.HttpClient` (zero external HTTP deps)
- JSON: `kotlinx-serialization-json`
- Async: Kotlin coroutines internally; blocking wrappers for Java
- Thread safety: `ConcurrentHashMap`, `@Volatile`, coroutine `Mutex`
- Testing: JUnit 5 + MockK + kotlinx-coroutines-test
- Integration tests use embedded `com.sun.net.httpserver.HttpServer`
