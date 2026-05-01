package ru.er_log.dictate.feature.settings.domain

import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer contract for reading and persisting user-facing settings.
 *
 * All flows are cold and emit the current value immediately upon collection,
 * then emit on every subsequent change.
 */
public interface SettingsRepository {
    public val clipboardOnPaste: Flow<Boolean>
    public val vibrateOnRecord: Flow<Boolean>
    public suspend fun setClipboardOnPaste(value: Boolean)
    public suspend fun setVibrateOnRecord(value: Boolean)
}
