package com.callrecorder.pro.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callrecorder.pro.R
import com.callrecorder.pro.data.settings.AudioFormatPref
import com.callrecorder.pro.data.settings.RecordingRule
import com.callrecorder.pro.di.AppContainer
import com.callrecorder.pro.record.AudioSources
import com.callrecorder.pro.util.formatBytes
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onOpenSourceTest: () -> Unit) {
    val scope = rememberCoroutineScope()
    val s by container.settings.settings.collectAsState(initial = null)
    val settings = s ?: return
    val context = LocalContext.current
    var storageBytes by remember { mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        storageBytes = container.recordingDao.totalSizeBytes()
    }
    var ignoredText by remember(settings.ignoredNumbers) {
        mutableStateOf(settings.ignoredNumbers.joinToString(", "))
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { u ->
            context.contentResolver.takePersistableUriPermission(
                u, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            scope.launch { container.settings.setSamsungTreeUri(u.toString()) }
            scope.launch { container.samsungImport.scanOnce(u) }
        }
    }

    val backupExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { scope.launch { container.backupManager.exportTo(it) } } }

    val backupImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { scope.launch { container.backupManager.importFrom(it) } } }

    fun upd(block: suspend () -> Unit) = scope.launch { block() }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.tab_settings)) })
    }) { p ->
        Column(
            Modifier.padding(p).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            SectionTitle(stringResource(R.string.settings_recording))
            Text(stringResource(R.string.settings_format), style = MaterialTheme.typography.titleMedium)
            Row {
                FilterChip(
                    selected = settings.format == AudioFormatPref.AAC,
                    onClick = { upd { container.settings.setFormat(AudioFormatPref.AAC) } },
                    label = { Text(stringResource(R.string.settings_format_aac)) })
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = settings.format == AudioFormatPref.WAV,
                    onClick = { upd { container.settings.setFormat(AudioFormatPref.WAV) } },
                    label = { Text(stringResource(R.string.settings_format_wav)) })
            }
            if (settings.format == AudioFormatPref.AAC) {
                Text(stringResource(R.string.settings_bitrate), style = MaterialTheme.typography.titleMedium)
                Row {
                    listOf(128, 192, 256).forEach { kb ->
                        FilterChip(
                            selected = settings.bitrateKbps == kb,
                            onClick = { upd { container.settings.setBitrate(kb) } },
                            label = { Text("$kb") })
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
            OutlinedButton(onClick = onOpenSourceTest) {
                Text(
                    stringResource(R.string.settings_source) + ": " +
                            stringResource(AudioSources.byKey(settings.audioSource).labelRes) +
                            " — " + stringResource(R.string.settings_source_test)
                )
            }
            ToggleRow(R.string.settings_noise_suppressor, settings.noiseSuppressor) { v ->
                upd { container.settings.setNoiseSuppressor(v) }
            }
            ToggleRow(R.string.settings_echo_canceler, settings.echoCanceler) { v ->
                upd { container.settings.setEchoCanceler(v) }
            }
            ToggleRow(R.string.settings_auto_gain, settings.autoGain) { v ->
                upd { container.settings.setAutoGain(v) }
            }
            ToggleRow(R.string.settings_auto_speaker, settings.autoSpeaker) { v ->
                upd { container.settings.setAutoSpeaker(v) }
            }
            ToggleRow(R.string.settings_notice_beep, settings.noticeBeep) { v ->
                upd { container.settings.setNoticeBeep(v) }
            }

            SectionTitle(stringResource(R.string.settings_rules))
            listOf(
                RecordingRule.ALL to R.string.rules_all,
                RecordingRule.SELECTED_ONLY to R.string.rules_selected,
                RecordingRule.ALL_EXCEPT to R.string.rules_except,
                RecordingRule.MANUAL to R.string.rules_manual
            ).forEach { (rule, label) ->
                Row {
                    RadioButton(
                        selected = settings.rule == rule,
                        onClick = { upd { container.settings.setRule(rule) } })
                    Text(stringResource(label))
                }
            }

            SectionTitle(stringResource(R.string.settings_storage))
            Text(stringResource(R.string.storage_usage, formatBytes(storageBytes)))
            Text(stringResource(R.string.settings_auto_delete), style = MaterialTheme.typography.titleMedium)
            Row {
                listOf(
                    0 to R.string.days_never, 7 to R.string.days_7,
                    30 to R.string.days_30, 90 to R.string.days_90
                ).forEach { (days, label) ->
                    FilterChip(
                        selected = settings.autoDeleteDays == days,
                        onClick = { upd { container.settings.setAutoDeleteDays(days) } },
                        label = { Text(stringResource(label)) })
                    Spacer(Modifier.width(8.dp))
                }
            }
            Text(stringResource(R.string.settings_min_duration), style = MaterialTheme.typography.titleMedium)
            Row {
                listOf(0 to R.string.sec_off, 5 to R.string.sec_5, 15 to R.string.sec_15, 60 to R.string.sec_60)
                    .forEach { (sec, label) ->
                        FilterChip(
                            selected = settings.minDurationSec == sec,
                            onClick = { upd { container.settings.setMinDurationSec(sec) } },
                            label = { Text(stringResource(label)) })
                        Spacer(Modifier.width(8.dp))
                    }
            }
            OutlinedTextField(
                value = ignoredText,
                onValueChange = { ignoredText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_ignored_hint)) }
            )
            OutlinedButton(onClick = {
                upd {
                    container.settings.setIgnoredNumbers(
                        ignoredText.split(',', ' ').map { it.trim() }.filter { it.isNotBlank() }.toSet()
                    )
                }
            }) { Text(stringResource(R.string.settings_ignored_save)) }

            OutlinedButton(onClick = { backupExport.launch("callrecorder_backup.zip") }) {
                Text(stringResource(R.string.export_backup))
            }
            OutlinedButton(onClick = { backupImport.launch(arrayOf("application/zip")) }) {
                Text(stringResource(R.string.import_backup))
            }

            SectionTitle(stringResource(R.string.settings_samsung_import))
            Text(stringResource(R.string.samsung_import_desc))
            OutlinedButton(onClick = { folderPicker.launch(null) }) {
                Text(stringResource(R.string.samsung_import_pick))
            }

            SectionTitle(stringResource(R.string.settings_lock))
            ToggleRow(R.string.settings_lock_desc, settings.appLock) { v ->
                upd { container.settings.setAppLock(v) }
            }
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + context.packageName)
                        )
                    )
                }
            }) { Text(stringResource(R.string.settings_battery_desc)) }

            SectionTitle(stringResource(R.string.settings_font))
            Text(stringResource(R.string.settings_font_family), style = MaterialTheme.typography.titleMedium)
            listOf(
                "cairo" to R.string.font_cairo,
                "ibm" to R.string.font_ibm,
                "basseet" to R.string.font_basseet,
                "camel" to R.string.font_camel,
                "system" to R.string.font_system
            ).forEach { (key, label) ->
                Row {
                    RadioButton(
                        selected = settings.fontFamily == key,
                        onClick = { upd { container.settings.setFontFamily(key) } })
                    Text(stringResource(label))
                }
            }
            Text(stringResource(R.string.settings_font_size), style = MaterialTheme.typography.titleMedium)
            Row {
                listOf(
                    0.9f to R.string.size_small,
                    1f to R.string.size_normal,
                    1.15f to R.string.size_large,
                    1.3f to R.string.size_huge
                ).forEach { (v, label) ->
                    FilterChip(
                        selected = settings.fontScale == v,
                        onClick = { upd { container.settings.setFontScale(v) } },
                        label = { Text(stringResource(label)) })
                    Spacer(Modifier.width(8.dp))
                }
            }

            SectionTitle(stringResource(R.string.settings_language))
            listOf(
                "" to R.string.language_system,
                "en" to R.string.language_en,
                "ar" to R.string.language_ar
            ).forEach { (code, label) ->
                Row {
                    RadioButton(
                        selected = settings.language == code,
                        onClick = { upd { container.settings.setLanguage(code) } })
                    Text(stringResource(label))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(
        text = t,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ToggleRow(labelRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(labelRes), Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
