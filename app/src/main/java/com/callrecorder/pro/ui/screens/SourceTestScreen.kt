package com.callrecorder.pro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callrecorder.pro.R
import com.callrecorder.pro.di.AppContainer
import com.callrecorder.pro.record.AudioSources
import com.callrecorder.pro.test.SourceTestEngine
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceTestScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val engine = remember { SourceTestEngine(container.appContext) }
    var running by remember { mutableStateOf(false) }
    var currentKey by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<SourceTestEngine.SourceResult>()) }

    val best = remember(results) { engine.bestOf(results) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.sourcetest_title)) })
    }) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            Text(stringResource(R.string.sourcetest_desc))
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.sourcetest_warning),
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        running = true
                        results = engine.testAll { key -> currentKey = key }
                        running = false
                    }
                },
                enabled = !running
            ) { Text(stringResource(R.string.sourcetest_run)) }
            if (running) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.sourcetest_running,
                        stringResource(AudioSources.byKey(currentKey).labelRes)
                    )
                )
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.sourceKey }) { r ->
                    val isBest = best?.sourceKey == r.sourceKey
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(AudioSources.byKey(r.sourceKey).labelRes) +
                                        if (isBest) " ★ " + stringResource(R.string.sourcetest_best) else "",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isBest) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(stringResource(R.string.sourcetest_rms) + ": " +
                                    String.format(Locale.US, "%.1f dB", r.rmsDb))
                            Text(stringResource(R.string.sourcetest_snr) + ": " +
                                    String.format(Locale.US, "%.1f dB", r.noiseFloorDb))
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = {
                                scope.launch { container.settings.setAudioSource(r.sourceKey) }
                            }) { Text(stringResource(R.string.sourcetest_use)) }
                        }
                    }
                }
            }
        }
    }
}
