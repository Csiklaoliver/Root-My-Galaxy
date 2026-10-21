package dev.busung.s25uroot

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Independent of installation: this action never calls InstallViewModel.install. */
@Composable
internal fun ShizukuPreflightCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.shizuku_preflight_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.shizuku_preflight_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(
                enabled = !running,
                onClick = {
                    running = true
                    output = ""
                    scope.launch {
                        try {
                            check(ShizukuController.pingUntilRunning()) {
                                context.getString(R.string.error_shizuku_unavailable)
                            }
                            check(ShizukuController.requestPermission()) {
                                context.getString(R.string.error_shizuku_permission)
                            }
                            output = withContext(Dispatchers.IO) {
                                val script = context.assets.open("shizuku-preflight.sh")
                                    .bufferedReader().use { it.readText() }
                                ShizukuController.capture(arrayOf("/system/bin/sh", "-c", script))
                                    .ifBlank { error("Shizuku preflight returned no successful output") }
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            output = "Preflight failed: ${error.message ?: error.javaClass.simpleName}"
                        } finally {
                            running = false
                        }
                    }
                },
            ) {
                Text(stringResource(
                    if (running) R.string.shizuku_preflight_running else R.string.shizuku_preflight_run,
                ))
            }
            if (output.isNotBlank()) {
                SelectionContainer {
                    Text(
                        output,
                        modifier = Modifier.heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Shizuku preflight", output))
                }) {
                    Text(stringResource(R.string.shizuku_preflight_copy))
                }
            }
        }
    }
}
