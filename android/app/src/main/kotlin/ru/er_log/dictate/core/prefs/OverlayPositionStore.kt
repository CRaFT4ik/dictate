package ru.er_log.dictate.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.overlayPositionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "overlay_position"
)

/**
 * Persistent store for the floating button overlay position backed by DataStore Preferences.
 * File on disk: `overlay_position.preferences_pb`.
 *
 * Coordinates are stored in density-independent pixels (dp) so position survives display
 * density changes across reboots or screen rotations.
 */
public class OverlayPositionStore(private val context: Context) {

    private val dataStore: DataStore<Preferences> get() = context.overlayPositionDataStore

    /**
     * Emits the last saved overlay position as `(x_dp, y_dp)`, or `null` when no position has
     * been saved yet (i.e. on first launch). Both keys must be present for a non-null result;
     * a partial write is treated as absent.
     */
    public val position: Flow<Pair<Float, Float>?> = dataStore.data.map { prefs ->
        val x = prefs[KEY_X_DP]
        val y = prefs[KEY_Y_DP]
        if (x != null && y != null) Pair(x, y) else null
    }

    /**
     * Persists the overlay position. Both coordinates are written in a single atomic
     * [DataStore.edit] transaction so the [position] flow never emits a partial state.
     *
     * @param xDp horizontal position in density-independent pixels.
     * @param yDp vertical position in density-independent pixels.
     */
    public suspend fun setPosition(xDp: Float, yDp: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_X_DP] = xDp
            prefs[KEY_Y_DP] = yDp
        }
    }

    private companion object {
        val KEY_X_DP = floatPreferencesKey("x_dp")
        val KEY_Y_DP = floatPreferencesKey("y_dp")
    }
}
