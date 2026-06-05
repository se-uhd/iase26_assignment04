package de.seuhd.ktfuzzer.engine

import de.seuhd.ktfuzzer.exec.Signal
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileCrashSinkTest {
    @Test
    fun `the sink reports a crash as new or a duplicate by content and exit code`(@TempDir dir: Path) {
        val sink = FileCrashSink(dir)
        val input = "x = 1\n"
        assertTrue(sink.save(input, Signal.SIGSEGV.exitCode, "out", "err"), "first occurrence is new")
        assertFalse(
            sink.save(input, Signal.SIGSEGV.exitCode, "out", "err"),
            "the same content and code is a duplicate"
        )
        assertTrue(sink.save("y = 2\n", Signal.SIGSEGV.exitCode, "", ""), "different content is new")
        assertTrue(
            sink.save(input, Signal.SIGABRT.exitCode, "", ""),
            "the same content under a different code is new"
        )
    }

    @Test
    fun `crashes are grouped by exit code, one folder per distinct input`(@TempDir dir: Path) {
        val sink = FileCrashSink(dir)
        sink.save("a\n", Signal.SIGSEGV.exitCode, "", "")
        sink.save("b\n", Signal.SIGSEGV.exitCode, "", "")
        sink.save("a\n", Signal.SIGABRT.exitCode, "", "")
        assertEquals(
            2,
            folderCount(dir.resolve("exit${Signal.SIGSEGV.exitCode}")),
            "two distinct inputs under one exit code"
        )
        assertEquals(
            1,
            folderCount(dir.resolve("exit${Signal.SIGABRT.exitCode}")),
            "the same input under another code is kept too"
        )
    }

    @Test
    fun `each crash folder holds the input plus the target stdout and stderr`(@TempDir dir: Path) {
        FileCrashSink(dir).save("boom\n", Signal.SIGSEGV.exitCode, "partial output", "stack smashing detected")
        val folder = Files.list(dir.resolve("exit${Signal.SIGSEGV.exitCode}")).use { it.toList() }.single()
        assertContentEquals("boom\n".toByteArray(), Files.readAllBytes(folder.resolve("input")), "the raw input")
        assertEquals("partial output", Files.readString(folder.resolve("stdout")), "the target stdout")
        assertEquals("stack smashing detected", Files.readString(folder.resolve("stderr")), "the target stderr")
    }

    private fun folderCount(dir: Path): Int = Files.list(dir).use { it.toList() }.size
}
