# Changelog

This project follows [Semantic Versioning](https://semver.org/). User-visible changes are recorded
here before a version tag is created.

## [Unreleased]

## [1.1.0] - 2026-08-28

### Added
- Optional `persistencePath` exact-batch write-ahead logging. A serialized in-flight batch is
  stored atomically before its first attempt and replayed byte-identically after restart,
  including any remaining final `Retry-After` deadline. Terminal responses acknowledge the WAL;
  pre-flush in-memory events remain outside this durability boundary.
- Configurable HTTP timeouts (`connectTimeoutMs`, default 5s; `requestTimeoutMs`, default 10s) on
  `AlitycsConfig`; previously `HttpTransport` had none and a stalled server could wedge a flush
  indefinitely.
- A 429 response's `Retry-After` header (delta-seconds or HTTP-date) is now honoured: the retry
  after it waits until the complete server deadline instead of the default backoff. Long waits are
  divided into sleep slices of at most 10s without issuing an early request.
  Previously the header was ignored and rate-limited clients hammered through the rate limit.
- Client-side enforcement of the canonical ingestion limits (identical to the server's
  `EventValidator`): ≤50 properties per event, property keys ≤100 chars, values ≤1000 chars,
  estimated event size ≤64KB, non-blank action plus `userId`/`anonymousId` required, epoch-millis
  timestamps (seconds-scale values rejected), age ≤7 days and never in the future. Violating events
  are rejected locally at build time: they are never queued and never sent, surfaced with a
  warn-level log (never debug-gated) and the new `Alitycs.rejectedLocally` counter. User data is
  never truncated silently.
- Split-on-batch-rejection: when the server rejects an entire batch with HTTP 400 (one invalid
  event poisons the whole batch), `BatchManager` splits the payload in half and re-sends each half
  recursively so valid events are still delivered. A 64-request budget per flush prevents an
  adversarial rejected batch from causing unbounded request amplification.

### Changed
- Batch sends now report honest outcomes (`SendOutcome`: Success / Rejected / TransportFailure)
  instead of swallowing every exception. On transient transport failure, drained-but-undelivered
  events are re-added to the head of the queue preserving order instead of being silently dropped;
  counters `deliveredTotal`/`requeuedTotal`/`lostTotal` on `BatchManager` expose what happened.
- `CancellationException` is no longer swallowed by `flush()` — cancellation propagates to callers,
  and drained events are re-queued first.
- Failed deliveries no longer report an empty queue: `flush()`/`shutdown()` leave undelivered
  events pending rather than claiming success.
- `shutdown()` now closes event admission atomically before draining and always cancels its scope,
  so an enqueue racing with shutdown is either included in that drain or explicitly rejected.
  Events enqueued after shutdown begins are rejected locally like any limit violation
  (warn-level log plus the `rejectedLocally` counter, never queued) instead of being silently
  swallowed: previously they were queued while the flush timer was stopped, so they were never
  delivered and `pending` rose without effect. The new `Alitycs.isShutdown` property exposes the
  state.
- The durable WAL and in-memory queue are both bounded by `maxQueueSize`. When retaining a new
  batch would exceed the WAL bound, the oldest complete batches are evicted with an unconditional
  warning; WAL replacement now forces file contents and cleans temporary files after failures.
- `Alitycs.initDefault()` no longer leaks the previous default instance: it shuts the old instance
  down (bounded by the standard blocking-shutdown timeout) before installing the new one, instead
  of orphaning its coroutine scope, flush timer, and HTTP client per call. If that shutdown fails,
  a warning is logged and replacement proceeds.
- Documented the `osName`/`osVersion`/`jvmVersion` context fields as reserved (collected
  client-side, currently discarded by server-side ingestion). No wire-format change.

## [1.0.0] - 2026-08-23

- Initial public Kotlin and Java-compatible JVM SDK release.
- Added v0.4.0-compatible event batches, session reset, global properties, error and trusted revenue
  events, bounded batching, retry, and blocking Java lifecycle methods.
- Added Kover gates at 90% lines and 85% methods.

[Unreleased]: https://github.com/alitycs/alitycs-sdk-jvm/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/alitycs/alitycs-sdk-jvm/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/alitycs/alitycs-sdk-jvm/releases/tag/v1.0.0
