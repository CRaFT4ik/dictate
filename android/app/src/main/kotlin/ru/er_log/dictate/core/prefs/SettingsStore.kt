package ru.er_log.dictate.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings"
)

/**
 * Persistent store for user-facing settings backed by DataStore Preferences.
 * File on disk: `settings.preferences_pb`.
 */
public class SettingsStore(private val context: Context) {

    private val dataStore: DataStore<Preferences> get() = context.settingsDataStore

    /** Emits whether dictated text should also be placed on the clipboard after paste. Default: false. */
    public val clipboardOnPaste: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_CLIPBOARD_ON_PASTE] ?: false
    }

    /** Emits whether the device should vibrate when a recording starts or stops. Default: true. */
    public val vibrateOnRecord: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_VIBRATE_ON_RECORD] ?: true
    }

    /** Persists the clipboard-on-paste preference. */
    public suspend fun setClipboardOnPaste(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_CLIPBOARD_ON_PASTE] = value }
    }

    /** Persists the vibrate-on-record preference. */
    public suspend fun setVibrateOnRecord(value: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_VIBRATE_ON_RECORD] = value }
    }

    private companion object {
        val KEY_CLIPBOARD_ON_PASTE = booleanPreferencesKey("clipboard_on_paste")
        val KEY_VIBRATE_ON_RECORD = booleanPreferencesKey("vibrate_on_record")
    }
}
