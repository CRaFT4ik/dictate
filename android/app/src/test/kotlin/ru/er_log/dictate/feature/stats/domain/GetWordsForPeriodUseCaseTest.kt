package ru.er_log.dictate.feature.stats.domain

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetWordsForPeriodUseCaseTest {

    // ---------------------------------------------------------------------------
    // Fake repository
    // ---------------------------------------------------------------------------

    private fun fakeRepo(flow: Flow<Long>): StatsRepository = object : StatsRepository {
        override fun wordsForPeriod(period: Period): Flow<Long> = flow
    }

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    @Test
    fun `invoke forwards values from repository unchanged`() = runTest {
        val repo = fakeRepo(flowOf(123L, 456L, 0L))
        val useCase = GetWordsForPeriodUseCase(repo)

        useCase(Period.Week).test {
            assertEquals(123L, awaitItem())
            assertEquals(456L, awaitItem())
            assertEquals(0L, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `invoke emits zero when repository emits zero`() = runTest {
        val repo = fakeRepo(flowOf(0L))
        val useCase = GetWordsForPeriodUseCase(repo)

        useCase(Period.Month).test {
            assertEquals(0L, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `invoke works with MutableStateFlow and re-emits on update`() = runTest {
        val stateFlow = MutableStateFlow(10L)
        val repo = fakeRepo(stateFlow)
        val useCase = GetWordsForPeriodUseCase(repo)

        useCase(Period.Year).test {
            assertEquals(10L, awaitItem())

            stateFlow.value = 99L
            assertEquals(99L, awaitItem())

            stateFlow.value = 0L
            assertEquals(0L, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invoke delegates period parameter to repository`() = runTest {
        var capturedPeriod: Period? = null
        val repo = object : StatsRepository {
            override fun wordsForPeriod(period: Period): Flow<Long> {
                capturedPeriod = period
                return flowOf(42L)
            }
        }
        val useCase = GetWordsForPeriodUseCase(repo)

        useCase(Period.Month).test {
            awaitItem()
            awaitComplete()
        }

        assertEquals(Period.Month, capturedPeriod)
    }
}
