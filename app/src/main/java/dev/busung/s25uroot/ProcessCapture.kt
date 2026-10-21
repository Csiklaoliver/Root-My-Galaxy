package dev.busung.s25uroot

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal data class CapturedProcess(val code: Int, val output: String)

/** Capture short diagnostic commands without blocking on either output pipe. */
internal fun captureProcessOutput(
    process: Process,
    timeoutMillis: Long = 10_000,
    maxBytesPerStream: Int = 64 * 1024,
): CapturedProcess {
    require(timeoutMillis > 0)
    require(maxBytesPerStream > 0)
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    val readers = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "diagnostic-output").apply { isDaemon = true }
    }
    try {
        val stdout = readers.submit(Callable {
            readCapturedStream(process.inputStream, maxBytesPerStream)
        })
        val stderr = readers.submit(Callable {
            readCapturedStream(process.errorStream, maxBytesPerStream)
        })
        // These commands do not accept input. Closing stdin also lets a child that
        // reads it observe EOF instead of waiting indefinitely.
        process.outputStream.close()
        if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
            throw IOException("Command timed out after $timeoutMillis ms")
        }
        val output = stdout.get(
            (deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS,
        ) + stderr.get(
            (deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS,
        )
        return CapturedProcess(process.exitValue(), output)
    } catch (error: TimeoutException) {
        throw IOException("Command output timed out after $timeoutMillis ms", error)
    } finally {
        runCatching {
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    process.waitFor(100, TimeUnit.MILLISECONDS)
                }
            }
        }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.outputStream.close() }
        readers.shutdownNow()
    }
}

private fun readCapturedStream(stream: InputStream, limit: Int): String = stream.use { input ->
    val kept = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    var truncated = false
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        val retain = count.coerceAtMost(limit - kept.size())
        kept.write(buffer, 0, retain)
        if (retain < count) truncated = true
        // Continue draining after the limit so the child can still finish.
    }
    kept.toString(Charsets.UTF_8.name()) + if (truncated) "\n[output truncated]\n" else ""
}
