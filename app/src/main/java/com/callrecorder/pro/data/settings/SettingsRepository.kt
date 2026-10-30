package com.callrecorder.pro.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class RecordingRule { ALL, SELECTED_ONLY, ALL_EXCEPT, MANUAL }
enum class AudioFormatPref { AAC, WAV }

/** Keys are persisted in English regardless of UI language. */
class SettingsRepository(private val context: Context) {

    private object K {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val legalAccepted = booleanPreferencesKey("legal_accepted")
        val format = stringPreferencesKey("format")
        val bitrateKbps = intPreferencesKey("bitrate_kbps")
        val audioSource = stringPreferencesKey("audio_source")
        val noiseSuppressor = booleanPreferencesKey("noise_suppressor")
        val echoCanceler = booleanPreferencesKey("echo_canceler")
        val autoGain = booleanPreferencesKey("auto_gain")
        val autoSpeaker = booleanPreferencesKey("auto_speaker")
        val noticeBeep = booleanPreferencesKey("notice_beep")
        val rule = stringPreferencesKey("rule")
        val selectedNumbers = stringPreferencesKey("selected_numbers")
        val ignoredNumbers = stringPreferencesKey("ignored_numbers")
        val minDurationSec = intPreferencesKey("min_duration_sec")
        val language = stringPreferencesKey("language")
        val autoDeleteDays = intPreferencesKey("auto_delete_days")
        val appLock = booleanPreferencesKey("app_lock")
        val samsungTreeUri = stringPreferencesKey("samsung_tree_uri")
        val trimSilence = booleanPreferencesKey("trim_silence")
        val normalize = booleanPreferencesKey("normalize")
        val fontFamily = stringPreferencesKey("font_family")
        val fontScale = floatPreferencesKey("font_scale")
    }

    data class Settings(
        val onboardingDone: Boolean = false,
        val legalAccepted: Boolean = false,
        val format: AudioFormatPref = AudioFormatPref.AAC,
        val bitrateKbps: Int = 128,
        val audioSource: String = "VOICE_RECOGNITION",
        val noiseSuppressor: Boolean = true,
        val echoCanceler: Boolean = false,
        val autoGain: Boolean = false,
        val autoSpeaker: Boolean = false,
        val noticeBeep: Boolean = true,
        val rule: RecordingRule = RecordingRule.ALL,
        val selectedNumbers: Set<String> = emptySet(),
        val ignoredNumbers: Set<String> = emptySet(),
        val minDurationSec: Int = 0,
        val language: String = "",
        val autoDeleteDays: Int = 0,
        val appLock: Boolean = false,
        val samsungTreeUri: String = "",
        val trimSilence: Boolean = false,
        val normalize: Boolean = true,
        val fontFamily: String = "cairo",
        val fontScale: Float = 1f
    )

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        fun strSet(key: androidx.datastore.preferences.core.Preferences.Key<String>) =
            (p[key] ?: "").split(',').filter { it.isNotBlank() }.toSet()
        Settings(
            onboardingDone = p[K.onboardingDone] ?: false,
            legalAccepted = p[K.legalAccepted] ?: false,
            format = runCatching { AudioFormatPref.valueOf(p[K.format] ?: "AAC") }
                .getOrDefault(AudioFormatPref.AAC),
            bitrateKbps = p[K.bitrateKbps] ?: 128,
            audioSource = p[K.audioSource] ?: "VOICE_RECOGNITION",
            noiseSuppressor = p[K.noiseSuppressor] ?: true,
            echoCanceler = p[K.echoCanceler] ?: false,
            autoGain = p[K.autoGain] ?: false,
            autoSpeaker = p[K.autoSpeaker] ?: false,
            noticeBeep = p[K.noticeBeep] ?: true,
            rule = runCatching { RecordingRule.valueOf(p[K.rule] ?: "ALL") }
                .getOrDefault(RecordingRule.ALL),
            selectedNumbers = strSet(K.selectedNumbers),
            ignoredNumbers = strSet(K.ignoredNumbers),
            minDurationSec = p[K.minDurationSec] ?: 0,
            language = p[K.language] ?: "",
            autoDeleteDays = p[K.autoDeleteDays] ?: 0,
            appLock = p[K.appLock] ?: false,
            samsungTreeUri = p[K.samsungTreeUri] ?: "",
            trimSilence = p[K.trimSilence] ?: false,
            normalize = p[K.normalize] ?: true,
            fontFamily = p[K.fontFamily] ?: "cairo",
            fontScale = p[K.fontScale] ?: 1f
        )
    }

    suspend fun setOnboardingDone(v: Boolean) = context.dataStore.edit { it[K.onboardingDone] = v }
    suspend fun setLegalAccepted(v: Boolean) = context.dataStore.edit { it[K.legalAccepted] = v }
    suspend fun setFormat(v: AudioFormatPref) = context.dataStore.edit { it[K.format] = v.name }
    suspend fun setBitrate(v: Int) = context.dataStore.edit { it[K.bitrateKbps] = v }
    suspend fun setAudioSource(v: String) = context.dataStore.edit { it[K.audioSource] = v }
    suspend fun setNoiseSuppressor(v: Boolean) = context.dataStore.edit { it[K.noiseSuppressor] = v }
    suspend fun setEchoCanceler(v: Boolean) = context.dataStore.edit { it[K.echoCanceler] = v }
    suspend fun setAutoGain(v: Boolean) = context.dataStore.edit { it[K.autoGain] = v }
    suspend fun setAutoSpeaker(v: Boolean) = context.dataStore.edit { it[K.autoSpeaker] = v }
    suspend fun setNoticeBeep(v: Boolean) = context.dataStore.edit { it[K.noticeBeep] = v }
    suspend fun setRule(v: RecordingRule) = context.dataStore.edit { it[K.rule] = v.name }
    suspend fun setSelectedNumbers(v: Set<String>) =
        context.dataStore.edit { it[K.selectedNumbers] = v.joinToString(",") }
    suspend fun setIgnoredNumbers(v: Set<String>) =
        context.dataStore.edit { it[K.ignoredNumbers] = v.joinToString(",") }
    suspend fun setMinDurationSec(v: Int) = context.dataStore.edit { it[K.minDurationSec] = v }
    suspend fun setLanguage(v: String) = context.dataStore.edit { it[K.language] = v }
    suspend fun setAutoDeleteDays(v: Int) = context.dataStore.edit { it[K.autoDeleteDays] = v }
    suspend fun setAppLock(v: Boolean) = context.dataStore.edit { it[K.appLock] = v }
    suspend fun setSamsungTreeUri(v: String) = context.dataStore.edit { it[K.samsungTreeUri] = v }
    suspend fun setTrimSilence(v: Boolean) = context.dataStore.edit { it[K.trimSilence] = v }
    suspend fun setNormalize(v: Boolean) = context.dataStore.edit { it[K.normalize] = v }
    suspend fun setFontFamily(v: String) = context.dataStore.edit { it[K.fontFamily] = v }
    suspend fun setFontScale(v: Float) = context.dataStore.edit { it[K.fontScale] = v }
}
