package de.seuhd.ktfuzzer.exec

import kotlin.test.Test
import kotlin.test.assertEquals

class SignalTest {
    @Test
    fun `known signal exit codes use the signal name`() {
        assertEquals("SIGSEGV", Signal.labelFor(Signal.SIGSEGV.exitCode))
        assertEquals("SIGABRT", Signal.labelFor(Signal.SIGABRT.exitCode))
        assertEquals("SIGTRAP", Signal.labelFor(Signal.SIGTRAP.exitCode))
    }

    @Test
    fun `unknown crash exit codes stay numeric`() {
        assertEquals("exit 200", Signal.labelFor(200))
        assertEquals("exit 130", Signal.labelFor(130))
        assertEquals("exit 2", Signal.labelFor(2))
    }
}
