package de.seuhd.ktfuzzer.engine

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Stores each crash under `<crashDir>/exit<code>/<sha256(input)>/` as three files: `input` (the raw
 * crashing bytes) and the `stdout` and `stderr` the target produced. Crashes are grouped by target
 * exit code and deduplicated by input content hash within each group.
 *
 * @param crashDir the directory where crash folders are written.
 */
internal class FileCrashSink(private val crashDir: Path) : CrashSink {
    private val alreadyWritten = HashSet<Path>()

    /**
     * Writes a crash folder for an exit-code/input pair that has not been seen by this sink instance.
     *
     * @param input the crashing input to store as UTF-8 bytes.
     * @param exitCode the target exit code, used in the `exit<code>` folder name.
     * @param stdout stdout captured for this run.
     * @param stderr stderr captured for this run.
     * @return true when a new crash folder was written, or false for a duplicate exit-code/input pair.
     */
    override fun save(input: String, exitCode: Int, stdout: String, stderr: String): Boolean {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val dir = crashDir.resolve("exit$exitCode").resolve(sha256Hex(bytes))
        if (dir in alreadyWritten) return false
        Files.createDirectories(dir)
        Files.write(dir.resolve(INPUT_FILE), bytes)
        Files.writeString(dir.resolve(STDOUT_FILE), stdout)
        Files.writeString(dir.resolve(STDERR_FILE), stderr)
        alreadyWritten.add(dir) // mark this path as already written after input, stdout, and stderr are all on disk.
        return true
    }

    /** Returns the lowercase hexadecimal SHA-256 digest of [data]. */
    private fun sha256Hex(data: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(data).toHexString()

    private companion object {
        const val INPUT_FILE = "input"
        const val STDOUT_FILE = "stdout"
        const val STDERR_FILE = "stderr"
    }
}
