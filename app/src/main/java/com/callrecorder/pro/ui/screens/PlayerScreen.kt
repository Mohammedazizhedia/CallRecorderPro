package com.callrecorder.pro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.callrecorder.pro.R
import com.callrecorder.pro.ui.PlayerViewModel
import com.callrecorder.pro.ui.WaveformView
import com.callrecorder.pro.util.decodePeaks
import com.callrecorder.pro.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val rec by vm.recording.collectAsState()
    val markers by vm.markers.collectAsState()
    val position by vm.positionMs.collectAsState()
    val context = LocalContext.current
    val recVal = rec ?: run { LaunchedEffect(Unit) { onBack() }; return }

    val audioFile = File(recVal.filePath)
    var playError by remember { mutableStateOf(false) }
    val player = remember(audioFile.absolutePath) {
        if (!audioFile.exists()) {
            playError = true
            null
        } else {
            runCatching {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(audioFile)))
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            playError = true
                        }
                    })
                    prepare()
                }
            }.getOrElse {
                playError = true
                null
            }
        }
    }
    DisposableEffect(Unit) { onDispose { player?.release() } }

    var peaks by remember { mutableStateOf(FloatArray(0)) }
    LaunchedEffect(recVal.filePath) {
        peaks = withContext(Dispatchers.IO) { decodePeaks(File(recVal.filePath)) }
    }

    var noteText by remember(recVal.notes) { mutableStateOf(recVal.notes) }
    var showMarkerDialog by remember { mutableStateOf(false) }
    var markerNote by remember { mutableStateOf("") }

    // A-B loop (competitor feature)
    var loopA by remember { mutableStateOf<Long?>(null) }
    var loopB by remember { mutableStateOf<Long?>(null) }
    var loopEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(position, loopA, loopB, loopEnabled) {
        val a = loopA; val b = loopB
        if (loopEnabled && a != null && b != null && b > a && position >= b) {
            player?.seekTo(a)
        }
    }

    val activePlayer = player
    if (activePlayer != null && !playError) {
        vm.startTicker({ activePlayer.isPlaying }, { activePlayer.currentPosition })
    }

    if (playError || player == null) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(
                    if (audioFile.exists()) R.string.player_error else R.string.player_file_missing
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.cancel)) }
        }
        return
    }
    val p = player ?: run {
        // safety net: never happens because of the guard above, but keeps p non-null
        onBack()
        return
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(recVal.contactName ?: recVal.number) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
            },
            actions = {
                var expanded by remember { mutableStateOf(false) }
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.PlayArrow, null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { v ->
                        DropdownMenuItem(
                            text = { Text("${v}x") },
                            onClick = { p.setPlaybackSpeed(v); expanded = false }
                        )
                    }
                }
            })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = formatDuration(position) + " / " + formatDuration(recVal.durationMs),
                style = MaterialTheme.typography.titleMedium
            )
            WaveformView(
                peaks = peaks,
                progress = if (recVal.durationMs > 0) position.toFloat() / recVal.durationMs else 0f,
                onSeek = { frac -> p.seekTo((frac * recVal.durationMs).toLong()) }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { p.seekBack() }) { Icon(Icons.Filled.Replay, null) }
                IconButton(onClick = { if (p.isPlaying) p.pause() else p.play() }) {
                    Icon(
                        if (p.isPlaying) Icons.Filled.Close else Icons.Filled.PlayArrow,
                        null
                    )
                }
                IconButton(onClick = { p.seekForward() }) { Icon(Icons.Filled.Forward, null) }
                IconButton(onClick = { showMarkerDialog = true }) { Icon(Icons.Filled.Add, null) }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = loopA != null,
                    onClick = { loopA = p.currentPosition; if (loopB != null && loopB!! <= loopA!!) loopB = null },
                    label = { Text("A " + (loopA?.let(::formatDuration) ?: "--:--")) }
                )
                FilterChip(
                    selected = loopB != null,
                    onClick = { loopB = p.currentPosition; loopEnabled = loopA != null && loopB!! > loopA!! },
                    label = { Text("B " + (loopB?.let(::formatDuration) ?: "--:--")) }
                )
                FilterChip(
                    selected = loopEnabled,
                    enabled = loopA != null && loopB != null && loopB!! > loopA!!,
                    onClick = { loopEnabled = !loopEnabled },
                    label = { Text(stringResource(R.string.player_loop_ab)) }
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.Repeat,
                    contentDescription = null,
                    tint = if (loopEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.player_bookmark_note)) },
                trailingIcon = {
                    IconButton(onClick = { vm.saveNotes(noteText) }) {
                        Icon(Icons.Filled.Check, null)
                    }
                })
            Text(
                text = stringResource(R.string.player_markers),
                style = MaterialTheme.typography.titleMedium
            )
            LazyColumn {
                items(markers, key = { it.positionMs }) { m ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(m.positionMs) + " — " + m.note,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { p.seekTo(m.positionMs) }) {
                            Icon(Icons.Filled.PlayArrow, null)
                        }
                        IconButton(onClick = { vm.removeMarker(m.positionMs) }) {
                            Icon(Icons.Filled.Close, null)
                        }
                    }
                }
            }
        }
    }
    if (showMarkerDialog) {
        AlertDialog(
            onDismissRequest = { showMarkerDialog = false },
            title = { Text(stringResource(R.string.player_bookmark)) },
            text = {
                OutlinedTextField(value = markerNote, onValueChange = { markerNote = it })
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addMarker(markerNote); markerNote = ""; showMarkerDialog = false
                }) { Text(stringResource(R.string.player_bookmark_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showMarkerDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            })
    }
}
