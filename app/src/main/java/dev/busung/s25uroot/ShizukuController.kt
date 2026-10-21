package dev.busung.s25uroot

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ShizukuController {
    private const val PERMISSION_REQUEST_CODE = 0x5352

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    /**
     * The binder is delivered to the app asynchronously after the Shizuku service starts.
     * Wait a short while in case the service is already up but the binder has not arrived yet.
     */
    suspend fun pingUntilRunning(timeoutMillis: Long = 3_000): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isRunning()) return true
            delay(100)
        }
        return isRunning()
    }

    fun isGranted(): Boolean = try {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    suspend fun requestPermission(): Boolean {
        if (isGranted()) return true
        if (!isRunning()) return false
        return suspendCancellableCoroutine { continuation ->
            lateinit var listener: Shizuku.OnRequestPermissionResultListener
            listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode == PERMISSION_REQUEST_CODE) {
                    Shizuku.removeRequestPermissionResultListener(listener)
                    continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
            continuation.invokeOnCancellation {
                Shizuku.removeRequestPermissionResultListener(listener)
            }
            try {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            } catch (error: Throwable) {
                Shizuku.removeRequestPermissionResultListener(listener)
                continuation.resumeWithException(error)
            }
        }
    }

    fun exec(cmd: Array<String>, env: Array<String>? = null, dir: String? = null): Process {
        val binder = Shizuku.getBinder()
            ?: throw IllegalStateException("Shizuku binder is not available")
        return RemoteProcess(IShizukuService.Stub.asInterface(binder).newProcess(cmd, env, dir))
    }

    /**
     * Runs a short command with a deadline and bounded output. Both streams must be
     * drained concurrently: reading stdout to EOF first can deadlock on a full stderr
     * pipe. A nonzero command exit retains the existing empty-string result.
     */
    fun capture(cmd: Array<String>): String {
        val result = captureProcessOutput(exec(cmd))
        return if (result.code == 0) result.output else ""
    }

    fun writeFile(remotePath: String, mode: String, source: InputStream) {
        val process = exec(arrayOf("sh", "-c", "cat > '$remotePath' && chmod $mode '$remotePath'"))
        val exitCode = try {
            process.outputStream.use { output ->
                source.use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            }
            process.waitFor()
        } finally {
            if (process.isAlive) process.destroy()
        }
        check(exitCode == 0) { "Failed to stage $remotePath (exit $exitCode)" }
    }

    private class RemoteProcess(private val remote: IRemoteProcess) : Process() {
        private val input by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.getInputStream()) }
        private val output by lazy { ParcelFileDescriptor.AutoCloseOutputStream(remote.getOutputStream()) }
        private val error by lazy { ParcelFileDescriptor.AutoCloseInputStream(remote.getErrorStream()) }

        override fun getInputStream(): InputStream = input
        override fun getOutputStream(): OutputStream = output
        override fun getErrorStream(): InputStream = error
        override fun waitFor(): Int = remote.waitFor()
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean =
            remote.waitForTimeout(timeout, unit.toString())
        override fun exitValue(): Int = remote.exitValue()

        override fun destroy() {
            runCatching { remote.destroy() }
        }

        override fun destroyForcibly(): Process {
            destroy()
            return this
        }

        override fun isAlive(): Boolean = remote.alive()
    }
}
