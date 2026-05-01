package ru.er_log.dictate.feature.home.ui

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.er_log.dictate.feature.home.data.OverlayRunningSource
import ru.er_log.dictate.feature.permissions.domain.Permission
import ru.er_log.dictate.feature.permissions.domain.PermissionStatus
import ru.er_log.dictate.feature.permissions.domain.PermissionsRepository
import ru.er_log.dictate.feature.stats.domain.GetWordsForPeriodUseCase
import ru.er_log.dictate.feature.stats.domain.Period
import ru.er_log.dictate.feature.stats.domain.StatsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------------------
    // Fakes
    // ---------------------------------------------------------------------------

    private fun fakeUseCase(words: Long = 0L): GetWordsForPeriodUseCase {
        val repo = object : StatsRepository {
            override fun wordsForPeriod(period: Period): Flow<Long> = flowOf(words)
        }
        return GetWordsForPeriodUseCase(repo)
    }

    private fun fakePermissionsRepo(
        initial: Map<Permission, PermissionStatus> = emptyMap(),
    ): PermissionsRepository = object : PermissionsRepository {
        private val flow = MutableStateFlow(initial)
        override fun observe(): Flow<Map<Permission, PermissionStatus>> = flow
        override fun refresh() = Unit
        override fun openSettings(permission: Permission) = Unit
    }

    private fun fakeOverlaySource(running: Boolean = false): OverlayRunningSource =
        object : OverlayRunningSource {
            override val isRunning: Flow<Boolean> = flowOf(running)
        }

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    @Test
    fun `wordsForPeriod is 123 after init when use case emits 123`() = runTest {
        val viewModel = HomeViewModel(
            getWordsForPeriod = fakeUseCase(123L),
            permissionsRepository = fakePermissionsRepo(),
            overlayRunningSource = fakeOverlaySource(),
        )

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(123L, state.wordsForPeriod)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial selectedPeriod is Week`() = runTest {
        val viewModel = HomeViewModel(
            getWordsForPeriod = fakeUseCase(),
            permissionsRepository = fakePermissionsRepo(),
            overlayRunningSource = fakeOverlaySource(),
        )

        viewModel.uiState.test {
            assertEquals(Period.Week, awaitItem().selectedPeriod)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectPeriod updates selectedPeriod in state`() = runTest {
        val viewModel = HomeViewModel(
            getWordsForPeriod = fakeUseCase(0L),
            permissionsRepository = fakePermissionsRepo(),
            overlayRunningSource = fakeOverlaySource(),
        )

        viewModel.uiState.test {
            awaitItem() // initial Week
            viewModel.selectPeriod(Period.Month)
            val state = awaitItem()
            assertEquals(Period.Month, state.selectedPeriod)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `overlayEnabled reflects overlay running source`() = runTest {
        val viewModel = HomeViewModel(
            getWordsForPeriod = fakeUseCase(),
            permissionsRepository = fakePermissionsRepo(),
            overlayRunningSource = fakeOverlaySource(running = true),
        )

        viewModel.uiState.test {
            assertTrue(awaitItem().overlayEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `overlayEnabled is false when overlay not running`() = runTest {
        val viewModel = HomeViewModel(
            getWordsForPeriod = fakeUseCase(),
            permissionsRepository = fakePermissionsRepo(),
            overlayRunningSource = fakeOverlaySource(running = false),
        )

        viewModel.uiState.test {
            assertFalse(awaitItem().overlayEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `permissionsState allGranted true when all permissions granted`() = runTest {
        val allGranted = mapOf(
            Permission.Microphone to PermissionStatus.GRANTED,
            Permission.Overlay to PermissionStatus.GRANTED,
            Permission.Accessibility to PermissionStatus.GRANTED,
        )
        val viewModel = HomeViewModel(
            getWordsForPeriod = fakeUseCase(),
            permissionsRepository = fakePermissionsRepo(allGranted),
            overlayRunningSource = fakeOverlaySource(),
        )

        viewModel.uiState.test {
            assertTrue(awaitItem().permissionsState.allGranted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `permissionsState allGranted false when any permission denied`() = runTest {
        val partial = mapOf(
            Permission.Microphone to PermissionStatus.GRANTED,
            Permission.Overlay to PermissionStatus.DENIED,
            Permission.Accessibility to PermissionStatus.GRANTED,
        )
        val viewModel = HomeViewModel(
            getWordsForPeriod = fakeUseCase(),
            permissionsRepository = fakePermissionsRepo(partial),
            overlayRunningSource = fakeOverlaySource(),
        )

        viewModel.uiState.test {
            assertFalse(awaitItem().permissionsState.allGranted)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
