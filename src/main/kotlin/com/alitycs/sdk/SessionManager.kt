package com.alitycs.sdk

class SessionManager(private val sessionTimeout: Long) {

    @Volatile
    private var session: SessionData = create()

    @Synchronized
    fun getSession(): SessionData = session.copy()

    @Synchronized
    fun touch() {
        if (isExpired()) {
            session = create(session.anonymousId)
        } else {
            session = session.copy(lastActivity = System.currentTimeMillis())
        }
    }

    @Synchronized
    fun setUserId(userId: String) {
        session = session.copy(userId = userId, lastActivity = System.currentTimeMillis())
    }

    @Synchronized
    fun reset(): SessionData {
        session = create()
        return session.copy()
    }

    private fun isExpired(): Boolean =
        System.currentTimeMillis() - session.lastActivity > sessionTimeout

    private fun create(anonymousId: String? = null): SessionData = SessionData(
        id = "sess_${generateId()}",
        anonymousId = anonymousId ?: "anon_${generateId()}",
        startTime = System.currentTimeMillis(),
        lastActivity = System.currentTimeMillis()
    )
}
