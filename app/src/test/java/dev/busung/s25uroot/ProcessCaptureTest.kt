package dev.busung.s25uroot

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProcessCaptureTest {
    private fun shell(command: String): Process =
        ProcessBuilder("/bin/sh", "-c", command).start()

    @Test(timeout = 10_000)
    fun retainsBothStreamsAndNonzeroExitCode() {
        val result = captureProcessOutput(shell("printf stdout; printf stderr >&2; exit 7"))
        assertEquals(7, result.code)
        assertEquals("stdoutstderr", result.output)
    }

    @Test(timeout = 10_000)
    fun drainsStderrWhileStdoutRemainsOpen() {
        // More than a pipe buffer, written before stdout closes. Sequential readers
        // deadlock here because the child cannot exit until stderr is consumed.
        val result = captureProcessOutput(
            shell("head -c 262144 /dev/zero >&2; printf finished"),
            timeoutMillis = 5_000,
            maxBytesPerStream = 4096,
        )
        assertEquals(0, result.code)
        assertTrue(result.output.startsWith("finished"))
        assertTrue(result.output.contains("[output truncated]"))
        assertTrue(result.output.length < 8192)
    }

    @Test(timeout = 10_000)
    fun keepsDrainingStdoutAfterOutputLimit() {
        val result = captureProcessOutput(
            shell("head -c 262144 /dev/zero; printf finished >&2"),
            timeoutMillis = 5_000,
            maxBytesPerStream = 4096,
        )
        assertEquals(0, result.code)
        assertTrue(result.output.endsWith("finished"))
        assertTrue(result.output.contains("[output truncated]"))
        assertTrue(result.output.length < 8192)
    }

    @Test(timeout = 10_000)
    fun closesUnusedStdin() {
        val result = captureProcessOutput(shell("cat; printf eof"), timeoutMillis = 2_000)
        assertEquals("eof", result.output)
    }

    @Test(timeout = 10_000)
    fun timesOutAndStopsChild() {
        val process = shell("exec sleep 30")
        try {
            captureProcessOutput(process, timeoutMillis = 200)
            fail("Expected command timeout")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().contains("timed out"))
        }
        assertFalse(process.isAlive)
    }
}
