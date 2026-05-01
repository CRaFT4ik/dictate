package ru.er_log.dictate.feature.settings.data

import ru.er_log.dictate.core.prefs.SettingsStore
import ru.er_log.dictate.feature.settings.domain.SettingsRepository

public class SettingsRepositoryImpl(private val store: SettingsStore) : SettingsRepository {
    override val clipboardOnPaste = store.clipboardOnPaste
    override val vibrateOnRecord = store.vibrateOnRecord
    override suspend fun setClipboardOnPaste(value: Boolean) = store.setClipboardOnPaste(value)
    override suspend fun setVibrateOnRecord(value: Boolean) = store.setVibrateOnRecord(value)
}
