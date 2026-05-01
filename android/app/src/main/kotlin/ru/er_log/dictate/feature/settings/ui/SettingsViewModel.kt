package ru.er_log.dictate.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.er_log.dictate.feature.settings.domain.SettingsRepository

public class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {

    public val clipboardOnPaste: StateFlow<Boolean> = repository.clipboardOnPaste
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    public val vibrateOnRecord: StateFlow<Boolean> = repository.vibrateOnRecord
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    public fun setClipboardOnPaste(value: Boolean) {
        viewModelScope.launch { repository.setClipboardOnPaste(value) }
    }

    public fun setVibrateOnRecord(value: Boolean) {
        viewModelScope.launch { repository.setVibrateOnRecord(value) }
    }
}
