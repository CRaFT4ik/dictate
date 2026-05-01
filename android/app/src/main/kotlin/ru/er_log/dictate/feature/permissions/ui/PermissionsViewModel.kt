package ru.er_log.dictate.feature.permissions.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.er_log.dictate.feature.permissions.domain.Permission
import ru.er_log.dictate.feature.permissions.domain.PermissionStatus
import ru.er_log.dictate.feature.permissions.domain.PermissionsRepository

public data class PermissionsUiState(
    val statuses: Map<Permission, PermissionStatus> = emptyMap(),
    val allGranted: Boolean = false,
)

public class PermissionsViewModel(
    private val repository: PermissionsRepository,
) : ViewModel() {

    public val uiState: StateFlow<PermissionsUiState> = repository.observe()
        .map { statuses ->
            val allGranted = statuses.size == 3 &&
                statuses.values.all { it == PermissionStatus.GRANTED }
            PermissionsUiState(
                statuses = statuses,
                allGranted = allGranted,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PermissionsUiState(),
        )

    public fun refresh() {
        repository.refresh()
    }

    public fun openSettings(permission: Permission) {
        repository.openSettings(permission)
    }
}
