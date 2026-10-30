package com.callrecorder.pro.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.callrecorder.pro.R
import com.callrecorder.pro.data.db.RecordingEntity
import com.callrecorder.pro.record.RecordingStateBus
import com.callrecorder.pro.ui.HomeViewModel
import com.callrecorder.pro.util.formatBytes
import com.callrecorder.pro.util.formatDuration
import com.callrecorder.pro.util.shareRecording
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenPlayer: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    val recs by vm.recordings.collectAsState()
    val filter by vm.filterState.collectAsState()
    val totalSize by vm.totalSize.collectAsState()
    val isRecording by RecordingStateBus.isRecording.collectAsState()
    val startedAt by RecordingStateBus.startedAt.collectAsState()
    var pendingDelete by remember { mutableStateOf<RecordingEntity?>(null) }
    val context = LocalContext.current

    // live ticker while the service is recording
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isRecording) {
        while (isRecording) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Text(
                        text = stringResource(R.string.storage_usage, formatBytes(totalSize)),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (isRecording) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(Modifier.width(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.notif_recording_title) + " · " +
                                    formatDuration(now - startedAt),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            OutlinedTextField(
                value = filter.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true
            )
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter.direction == null && !filter.favoritesOnly,
                    onClick = { vm.setDirection(null); vm.setFavoritesOnly(false) },
                    label = { Text(stringResource(R.string.filter_all)) }
                )
                FilterChip(
                    selected = filter.direction == "incoming",
                    onClick = { vm.setDirection("incoming") },
                    label = { Text(stringResource(R.string.filter_incoming)) }
                )
                FilterChip(
                    selected = filter.direction == "outgoing",
                    onClick = { vm.setDirection("outgoing") },
                    label = { Text(stringResource(R.string.filter_outgoing)) }
                )
                FilterChip(
                    selected = filter.favoritesOnly,
                    onClick = { vm.setFavoritesOnly(!filter.favoritesOnly) },
                    label = { Text(stringResource(R.string.filter_favorites)) }
                )
            }
            if (recs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(
                            if (filter.query.isBlank()) R.string.empty_library else R.string.empty_search
                        ),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recs, key = { it.id }) { rec ->
                        RecordingRow(
                            rec = rec,
                            onClick = { onOpenPlayer(rec.id) },
                            onToggleFav = { vm.toggleFavorite(rec) },
                            onShare = { context.shareRecording(File(rec.filePath)) },
                            onDelete = { pendingDelete = rec }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(rec); pendingDelete = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun RecordingRow(
    rec: RecordingEntity,
    onClick: () -> Unit,
    onToggleFav: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = rec.contactName ?: rec.number.ifBlank { stringResource(R.string.unknown_number) },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${directionLabel(rec.direction)} · ${formatDuration(rec.durationMs)} · " +
                            formatBytes(rec.fileSize),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(rec.startTime)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = onToggleFav) {
                Icon(
                    imageVector = if (rec.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (rec.favorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = null)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = null)
            }
        }
    }
}

@Composable
private fun directionLabel(direction: String): String = when (direction) {
    "incoming" -> stringResource(R.string.call_incoming)
    "outgoing" -> stringResource(R.string.call_outgoing)
    else -> stringResource(R.string.unknown_number)
}
