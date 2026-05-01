package ru.er_log.dictate.core.audio

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module exposing [WavRecorder].
 *
 * Factory is used instead of single because each start/stop recording lifecycle
 * should begin with a clean, reset instance — sharing one instance across
 * concurrent callers (e.g., service restart) would require external
 * synchronisation, whereas a factory makes the lifecycle crystal-clear.
 */
public val audioModule = module {
    factory { WavRecorder(androidContext()) }
}
