package ru.er_log.dictate.core.network

import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.Assert.assertTrue
import ru.er_log.dictate.BuildConfig

class ConfigSanityTest {

    @Test
    fun configHasNoPlaceholder() {
        assumeFalse(BuildConfig.SERVER_URL.contains("X.X"))
        assertTrue(BuildConfig.SERVER_URL.startsWith("http://"))
    }
}
