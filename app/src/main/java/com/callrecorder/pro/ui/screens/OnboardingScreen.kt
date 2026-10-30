
package com.callrecorder.pro.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.callrecorder.pro.R
import com.callrecorder.pro.data.settings.SettingsRepository
import kotlinx.coroutines.launch

private data class PermStep(
    val titleRes: Int,
    val bodyRes: Int,
    val permission: String?,
    val required: Boolean
)

private fun buildSteps(): List<PermStep> = buildList {
    add(PermStep(R.string.onboarding_legal_title, R.string.onboarding_legal_body, null, true))
    add(PermStep(R.string.onboarding_perm_audio_title, R.string.onboarding_perm_audio_body, Manifest.permission.RECORD_AUDIO, true))
    add(PermStep(R.string.onboarding_perm_phone_title, R.string.onboarding_perm_phone_body, Manifest.permission.READ_PHONE_STATE, false))
    if (Build.VERSION.SDK_INT >= 33) {
        add(PermStep(R.string.onboarding_perm_notifications_title, R.string.onboarding_perm_notifications_body, Manifest.permission.POST_NOTIFICATIONS, false))
    }
    add(PermStep(R.string.onboarding_perm_contacts_title, R.string.onboarding_perm_contacts_body, Manifest.permission.READ_CONTACTS, false))
    add(PermStep(R.string.onboarding_perm_calllog_title, R.string.onboarding_perm_calllog_body, Manifest.permission.READ_CALL_LOG, false))
}

/**
 * Step-by-step permission onboarding with graceful denial handling.
 * NOTE: no local functions on purpose - the compiler failed to resolve
 * mutually-referencing local funs here; all logic is inlined.
 */
@Composable
fun OnboardingScreen(
    settings: SettingsRepository,
    onFinished: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val allSteps = remember { buildSteps() }
    var step by remember { mutableStateOf(0) }
    var showDenied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val current = allSteps[step.coerceIn(allSteps.indices)]
    val isLast = step == allSteps.size - 1

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        when {
            !granted && current.required -> showDenied = true
            isLast -> scope.launch {
                settings.setOnboardingDone(true)
                onFinished()
            }
            else -> {
                showDenied = false
                step++
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (step == 0) stringResource(R.string.onboarding_welcome_title)
            else stringResource(current.titleRes),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(16.dp))
        Card {
            Text(
                text = stringResource(current.bodyRes),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        if (showDenied) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_denied_explain),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (step > 0) {
                TextButton(onClick = {
                    if (isLast) {
                        scope.launch {
                            settings.setOnboardingDone(true)
                            onFinished()
                        }
                    } else step++
                }) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                when {
                    step == 0 -> {
                        scope.launch { settings.setLegalAccepted(true) }
                        step++
                    }
                    current.permission == null -> {
                        if (isLast) {
                            scope.launch {
                                settings.setOnboardingDone(true)
                                onFinished()
                            }
                        } else step++
                    }
                    ContextCompat.checkSelfPermission(context, current.permission) ==
                            PackageManager.PERMISSION_GRANTED -> {
                        if (isLast) {
                            scope.launch {
                                settings.setOnboardingDone(true)
                                onFinished()
                            }
                        } else step++
                    }
                    else -> launcher.launch(current.permission)
                }
            }) {
                Text(
                    stringResource(
                        when {
                            step == 0 -> R.string.onboarding_i_agree
                            isLast -> R.string.onboarding_start
                            else -> R.string.onboarding_next
                        }
                    )
                )
            }
        }
    }
}
