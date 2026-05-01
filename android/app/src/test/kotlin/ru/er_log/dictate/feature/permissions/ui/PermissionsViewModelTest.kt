package ru.er_log.dictate.feature.permissions.ui

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import ru.er_log.dictate.feature.permissions.domain.Permission
import ru.er_log.dictate.feature.permissions.domain.PermissionStatus
import ru.er_log.dictate.feature.permissions.domain.PermissionsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeRepo(
        initial: Map<Permission, PermissionStatus>,
    ): PermissionsRepository = object : PermissionsRepository {
        private val flow = MutableStateFlow(initial)
        override fun observe(): Flow<Map<Permission, PermissionStatus>> = flow
        override fun refresh() = Unit
        override fun openSettings(permission: Permission) = Unit
    }

    @Test
    fun `allGranted is true when all permissions are GRANTED`() = runTest {
        val allGranted = mapOf(
            Permission.Microphone to PermissionStatus.GRANTED,
            Permission.Overlay to PermissionStatus.GRANTED,
            Permission.Accessibility to PermissionStatus.GRANTED,
        )
        val viewModel = PermissionsViewModel(fakeRepo(allGranted))

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.allGranted)
            assertEquals(allGranted, state.statuses)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `allGranted is false when at least one permission is DENIED`() = runTest {
        val partiallyGranted = mapOf(
            Permission.Microphone to PermissionStatus.GRANTED,
            Permission.Overlay to PermissionStatus.DENIED,
            Permission.Accessibility to PermissionStatus.GRANTED,
        )
        val viewModel = PermissionsViewModel(fakeRepo(partiallyGranted))

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.allGranted)
            assertEquals(partiallyGranted, state.statuses)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `allGranted is false when all permissions are DENIED`() = runTest {
        val allDenied = mapOf(
            Permission.Microphone to PermissionStatus.DENIED,
            Permission.Overlay to PermissionStatus.DENIED,
            Permission.Accessibility to PermissionStatus.DENIED,
        )
        val viewModel = PermissionsViewModel(fakeRepo(allDenied))

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.allGranted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state has empty statuses and allGranted false`() = runTest {
        val viewModel = PermissionsViewModel(fakeRepo(emptyMap()))

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.statuses.isEmpty())
            assertFalse(state.allGranted)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
