package ru.er_log.dictate.feature.home.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.er_log.dictate.core.overlay.FloatingButtonService
import ru.er_log.dictate.feature.home.data.OverlayRunningSource
import ru.er_log.dictate.feature.permissions.domain.Permission
import ru.er_log.dictate.feature.permissions.domain.PermissionStatus
import ru.er_log.dictate.feature.permissions.domain.PermissionsRepository
import ru.er_log.dictate.feature.permissions.ui.PermissionsUiState
import ru.er_log.dictate.feature.stats.domain.GetWordsForPeriodUseCase
import ru.er_log.dictate.feature.stats.domain.Period

/**
 * UI state for the home screen.
 *
 * @property selectedPeriod The currently selected stats aggregation period.
 * @property wordsForPeriod Total word count for [selectedPeriod].
 * @property permissionsState Current state of all required runtime permissions.
 * @property overlayEnabled Whether the floating button overlay service is currently running.
 */
public data class HomeUiState(
    val selectedPeriod: Period = Period.Week,
    val wordsForPeriod: Long = 0L,
    val permissionsState: PermissionsUiState = PermissionsUiState(emptyMap(), false),
    val overlayEnabled: Boolean = false,
)

/**
 * ViewModel for the home screen.
 *
 * Combines stats, permissions and overlay running state into a single [StateFlow].
 *
 * @param getWordsForPeriod Use case for querying word counts from the stats repository.
 * @param permissionsRepository Repository that provides permission status observations.
 * @param overlayRunningSource Source that emits the overlay service running state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class HomeViewModel(
    private val getWordsForPeriod: GetWordsForPeriodUseCase,
    private val permissionsRepository: PermissionsRepository,
    private val overlayRunningSource: OverlayRunningSource,
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow<Period>(Period.Week)

    /** Reactive stream of word counts that switches when the period selection changes. */
    private val wordsFlow = _selectedPeriod.flatMapLatest { period ->
        getWordsForPeriod(period)
    }

    /** Reactive stream of permissions mapped to [PermissionsUiState]. */
    private val permissionsFlow = permissionsRepository.observe().map { statuses ->
        val allGranted = statuses.size == 3 &&
            statuses.values.all { it == PermissionStatus.GRANTED }
        PermissionsUiState(statuses = statuses, allGranted = allGranted)
    }

    /** Merged state flow exposed to the UI. */
    public val uiState: StateFlow<HomeUiState> = combine(
        _selectedPeriod,
        wordsFlow,
        permissionsFlow,
        overlayRunningSource.isRunning,
    ) { period, words, permissions, running ->
        HomeUiState(
            selectedPeriod = period,
            wordsForPeriod = words,
            permissionsState = permissions,
            overlayEnabled = running,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    /**
     * Changes the selected aggregation period.
     *
     * Triggers a new query via [getWordsForPeriod] through the [wordsFlow] flat-map.
     */
    public fun selectPeriod(period: Period) {
        _selectedPeriod.value = period
    }

    /**
     * Starts or stops the floating button overlay service.
     *
     * @param enabled `true` to start the service, `false` to stop it.
     * @param context Activity context used to dispatch the service intent.
     *                 May be `null` (no-op in that case).
     */
    public fun setOverlayEnabled(enabled: Boolean, context: Context?) {
        context ?: return
        val intent = Intent(context, FloatingButtonService::class.java)
        if (enabled) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.stopService(intent)
        }
    }

    /**
     * Requests the permissions repository to re-evaluate the current grant state.
     *
     * Should be called on `ON_RESUME` so that changes made in system settings are reflected.
     */
    public fun refreshPermissions() {
        permissionsRepository.refresh()
    }

    /**
     * Delegates opening the system settings screen for [permission] to the repository.
     */
    public fun onGrantPermission(permission: Permission) {
        permissionsRepository.openSettings(permission)
    }
}
