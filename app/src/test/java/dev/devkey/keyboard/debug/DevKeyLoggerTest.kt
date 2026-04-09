package dev.devkey.keyboard.debug

import org.junit.After
import org.junit.Test
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import java.lang.reflect.Field

/**
 * Minimal guard test for DevKeyLogger.enableServer / disableServer lifecycle.
 *
 * WHY: Phase 2.1 is the first production caller of DevKeyLogger.enableServer.
 *      Before this plan, the API had zero callers and zero tests.
 * NOTE: We use reflection to inspect the private serverUrl field because DevKeyLogger
 *       is an object singleton with no public getter. This is a test-only tradeoff
 *       justified by the fact that the field controls the HTTP-forwarding kill-switch.
 */
class DevKeyLoggerTest {

    @After
    fun tearDown() {
        // IMPORTANT: Clear server state between tests — DevKeyLogger is a singleton.
        DevKeyLogger.disableServer()
    }

    @Test
    fun `enableServer sets serverUrl field`() {
        DevKeyLogger.enableServer("http://127.0.0.1:3948")
        assertNotNull("serverUrl should be set after enableServer", readServerUrl())
    }

    @Test
    fun `disableServer clears serverUrl field`() {
        DevKeyLogger.enableServer("http://127.0.0.1:3948")
        DevKeyLogger.disableServer()
        assertNull("serverUrl should be null after disableServer", readServerUrl())
    }

    @Test
    fun `category convenience methods are no-op when server disabled`() {
        // WHY: Precondition — no server means no crash, no HTTP call.
        //      The sendToServer coroutine path must early-return on null serverUrl.
        DevKeyLogger.disableServer()
        // These calls must not throw.
        DevKeyLogger.ime("test_event")
        DevKeyLogger.voice("test_event", mapOf("state" to "IDLE"))
        DevKeyLogger.error("test_event")
    }

    private fun readServerUrl(): String? {
        val field: Field = DevKeyLogger::class.java.getDeclaredField("serverUrl")
        field.isAccessible = true
        return field.get(DevKeyLogger) as String?
    }
}
