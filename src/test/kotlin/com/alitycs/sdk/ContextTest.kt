package com.alitycs.sdk

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ContextTest {

    @Test
    fun `collectContext returns correct SDK info`() {
        val ctx = collectContext()
        assertEquals("1.1.2", ctx.sdkVersion)
        assertEquals("kotlin", ctx.sdkLanguage)
    }

    @Test
    fun `collectContext includes locale`() {
        val ctx = collectContext()
        assertNotNull(ctx.locale)
    }

    @Test
    fun `collectContext includes timezone`() {
        val ctx = collectContext()
        assertNotNull(ctx.timezone)
    }

    @Test
    fun `collectContext includes OS info`() {
        val ctx = collectContext()
        assertNotNull(ctx.osName)
        assertNotNull(ctx.osVersion)
    }

    @Test
    fun `collectContext includes JVM version`() {
        val ctx = collectContext()
        assertNotNull(ctx.jvmVersion)
    }

    @Test
    fun `collectContext does not include browser-specific fields`() {
        val ctx = collectContext()
        assertNull(ctx.userAgent)
        assertNull(ctx.url)
        assertNull(ctx.referrer)
        assertNull(ctx.screen)
    }
}
