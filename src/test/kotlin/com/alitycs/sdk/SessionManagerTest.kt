package com.alitycs.sdk

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SessionManagerTest {

    @Test
    fun `creates session with valid IDs on init`() {
        val manager = SessionManager(30 * 60 * 1000L)
        val session = manager.getSession()
        assertTrue(session.id.startsWith("sess_"))
        assertTrue(session.anonymousId.startsWith("anon_"))
        assertNull(session.userId)
    }

    @Test
    fun `touch updates lastActivity when not expired`() {
        val manager = SessionManager(30 * 60 * 1000L)
        val session1 = manager.getSession()
        Thread.sleep(10)
        manager.touch()
        val session2 = manager.getSession()
        assertEquals(session1.id, session2.id)
        assertTrue(session2.lastActivity >= session1.lastActivity)
    }

    @Test
    fun `touch creates new session when expired but preserves anonymousId`() {
        val manager = SessionManager(1) // 1ms timeout
        val session1 = manager.getSession()
        Thread.sleep(50)
        manager.touch()
        val session2 = manager.getSession()
        assertNotEquals(session1.id, session2.id)
        assertEquals(session1.anonymousId, session2.anonymousId)
    }

    @Test
    fun `setUserId sets userId on session`() {
        val manager = SessionManager(30 * 60 * 1000L)
        assertNull(manager.getSession().userId)
        manager.setUserId("user-123")
        assertEquals("user-123", manager.getSession().userId)
    }

    @Test
    fun `new session after expiry clears userId`() {
        val manager = SessionManager(1) // 1ms timeout
        manager.setUserId("user-123")
        Thread.sleep(50)
        manager.touch()
        assertNull(manager.getSession().userId)
    }

    @Test
    fun `getSession returns a copy`() {
        val manager = SessionManager(30 * 60 * 1000L)
        val s1 = manager.getSession()
        val s2 = manager.getSession()
        assertEquals(s1.id, s2.id)
        // Modifying the copy should not affect the manager
        s1.userId = "modified"
        assertNull(manager.getSession().userId)
    }
}
