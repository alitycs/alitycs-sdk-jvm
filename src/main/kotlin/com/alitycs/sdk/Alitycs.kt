package com.alitycs.sdk

import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class Alitycs private constructor(private val config: AlitycsConfig) {

    private val transport = HttpTransport(
        endpoint = config.endpoint,
        apiKey = config.apiKey,
        maxRetries = config.maxRetries,
        debug = config.debug,
        connectTimeoutMs = config.connectTimeoutMs,
        requestTimeoutMs = config.requestTimeoutMs
    )
    private val sessionManager = SessionManager(config.sessionTimeout)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val batchManager: BatchManager? = if (config.batching) {
        BatchManager(
            flushSize = config.flushSize,
            flushInterval = config.flushInterval,
            maxQueueSize = config.maxQueueSize,
            debug = config.debug,
            sendFn = transport::send
        ).also { it.start(scope) }
    } else null

    @Volatile
    private var userId: String? = null
    private val globalProperties = ConcurrentHashMap<String, Any?>()
    private val inFlight = ConcurrentHashMap.newKeySet<Job>()
    private val rejectedLocallyCounter = AtomicLong(0)

    /** Events rejected locally at build time for violating ingestion limits. */
    val rejectedLocally: Long get() = rejectedLocallyCounter.get()

    @JvmOverloads
    fun track(eventName: String, properties: Map<String, Any?> = emptyMap()) {
        if (eventName.isBlank()) return
        enqueue(EventType.TRACK, eventName, properties)
    }

    /** Server-only trusted revenue ingestion. Requires a secret key with revenue:write. */
    @JvmOverloads
    fun trackRevenue(payload: RevenuePayload, properties: Map<String, Any?> = emptyMap()) {
        enqueue(EventType.TRACK, "revenue_${payload.kind}", properties, payload)
    }

    @JvmOverloads
    fun captureError(errorName: String, properties: Map<String, Any?> = emptyMap()) {
        if (errorName.isBlank()) return
        enqueue(EventType.ERROR, errorName, properties)
    }

    @JvmOverloads
    fun identify(userId: String, traits: Map<String, Any?> = emptyMap()) {
        if (userId.isBlank()) return
        this.userId = userId
        sessionManager.setUserId(userId)
        enqueue(EventType.IDENTIFY, "identify", buildMap {
            put("userId", userId)
            putAll(traits)
        })
    }

    fun reset() {
        userId = null
        sessionManager.reset()
    }

    @JvmOverloads
    fun page(name: String? = null, properties: Map<String, Any?> = emptyMap()) {
        val pageName = if (name.isNullOrBlank()) "page_view" else name
        enqueue(EventType.PAGE, pageName, properties)
    }

    fun setGlobalProperties(properties: Map<String, Any?>) {
        globalProperties.putAll(properties)
    }

    fun getGlobalProperties(): Map<String, Any?> = HashMap(globalProperties)

    fun removeGlobalProperties(keys: List<String>) {
        keys.forEach { globalProperties.remove(it) }
    }

    fun clearGlobalProperties() {
        globalProperties.clear()
    }

    suspend fun flush() {
        if (batchManager != null) {
            batchManager.flush()
        } else {
            inFlight.toList().forEach { it.join() }
        }
    }

    @JvmOverloads
    fun flushBlocking(timeoutMs: Long = 30_000L) {
        runBlocking {
            withTimeout(timeoutMs) { flush() }
        }
    }

    suspend fun shutdown() {
        batchManager?.stop()
        if (batchManager != null) {
            batchManager.flush()
        } else {
            inFlight.toList().forEach { it.join() }
        }
        scope.cancel()
    }

    @JvmOverloads
    fun shutdownBlocking(timeoutMs: Long = 30_000L) {
        runBlocking {
            withTimeout(timeoutMs) { shutdown() }
        }
    }

    val pending: Int
        get() = batchManager?.pending ?: inFlight.size

    /** True once shutdown() has completed; a shut-down client rejects further events. */
    val isShutdown: Boolean
        get() = !scope.isActive

    private fun enqueue(
        type: EventType,
        name: String,
        properties: Map<String, Any?>?,
        revenue: RevenuePayload? = null,
    ) {
        if (!scope.isActive) {
            // Post-shutdown events are rejected locally like any limit violation:
            // never queued, never sent — warn + counter instead of a silent swallow.
            rejectedLocallyCounter.incrementAndGet()
            System.err.println("[Alitycs] WARN Client is shut down — event '$name' rejected locally")
            return
        }
        sessionManager.touch()
        val session = sessionManager.getSession()

        val merged = buildMap<String, Any?> {
            putAll(globalProperties)
            if (properties != null) putAll(properties)
        }

        val event = try {
            AnalyticsEvent(
                eventId = "evt_${generateId()}",
                event = name,
                eventType = type,
                userId = userId,
                anonymousId = session.anonymousId,
                sessionId = session.id,
                timestamp = System.currentTimeMillis(),
                properties = serializeProperties(merged),
                revenue = revenue,
                context = collectContext()
            ).also { validateEvent(it) }
        } catch (e: EventRejectedException) {
            // Rejected locally: never queued, never sent. Warn (not debug-gated) + counter.
            rejectedLocallyCounter.incrementAndGet()
            System.err.println("[Alitycs] WARN ${e.message}")
            return
        }

        if (batchManager != null) {
            batchManager.add(event)
        } else {
            val job = scope.launch {
                val payload = BatchPayload(
                    batchId = "batch_${generateId()}",
                    sentAt = System.currentTimeMillis(),
                    events = listOf(event)
                )
                when (val outcome = transport.send(payload)) {
                    is SendOutcome.Success -> Unit
                    is SendOutcome.Rejected ->
                        System.err.println(
                            "[Alitycs] WARN Server rejected event '${event.eventId}' with " +
                                "HTTP ${outcome.status} — dropped"
                        )
                    is SendOutcome.TransportFailure ->
                        System.err.println(
                            "[Alitycs] WARN Transport failure (${outcome.cause?.message ?: "unknown"}) — " +
                                "event '${event.eventId}' could not be delivered"
                        )
                }
            }
            inFlight.add(job)
            job.invokeOnCompletion { inFlight.remove(job) }
        }
    }

    companion object {
        @Volatile
        private var defaultInstance: Alitycs? = null

        @JvmStatic
        fun init(config: AlitycsConfig): Alitycs = Alitycs(config)

        @JvmStatic
        fun initDefault(config: AlitycsConfig): Alitycs {
            // Shut down any previous default first so its scope/timer are not leaked;
            // without this, repeated initDefault() calls orphan a live timer per call.
            val previous = defaultInstance
            if (previous != null && !previous.isShutdown) {
                try {
                    previous.shutdownBlocking()
                } catch (e: Exception) {
                    System.err.println(
                        "[Alitycs] WARN Failed to shut down previous default instance: ${e.message}"
                    )
                }
            }
            return init(config).also { defaultInstance = it }
        }

        @JvmStatic
        @JvmName("trackDefault")
        fun track(eventName: String, properties: Map<String, Any?> = emptyMap()) {
            defaultInstance?.track(eventName, properties)
        }

        @JvmStatic
        @JvmName("trackRevenueDefault")
        fun trackRevenue(payload: RevenuePayload, properties: Map<String, Any?> = emptyMap()) {
            defaultInstance?.trackRevenue(payload, properties)
        }

        @JvmStatic
        @JvmName("captureErrorDefault")
        fun captureError(errorName: String, properties: Map<String, Any?> = emptyMap()) {
            defaultInstance?.captureError(errorName, properties)
        }

        @JvmStatic
        @JvmName("identifyDefault")
        fun identify(userId: String, traits: Map<String, Any?> = emptyMap()) {
            defaultInstance?.identify(userId, traits)
        }

        @JvmStatic
        fun resetDefault() {
            defaultInstance?.reset()
        }

        @JvmStatic
        @JvmName("pageDefault")
        fun page(name: String? = null, properties: Map<String, Any?> = emptyMap()) {
            defaultInstance?.page(name, properties)
        }

        @JvmStatic
        fun setDefaultGlobalProperties(properties: Map<String, Any?>) {
            defaultInstance?.setGlobalProperties(properties)
        }

        @JvmStatic
        fun getDefaultGlobalProperties(): Map<String, Any?> {
            return defaultInstance?.getGlobalProperties() ?: emptyMap()
        }

        @JvmStatic
        suspend fun flushDefault() {
            defaultInstance?.flush()
        }

        @JvmStatic
        suspend fun shutdownDefault() {
            defaultInstance?.shutdown()
            defaultInstance = null
        }
    }
}
