package ru.er_log.dictate.feature.home.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import ru.er_log.dictate.R
import ru.er_log.dictate.feature.permissions.ui.PermissionsCard

/**
 * Home screen that composes the stats card, permissions card and overlay toggle button.
 *
 * Refreshes permissions automatically when the screen resumes so that changes made in the
 * system settings dialog are reflected immediately without requiring the user to restart.
 *
 * @param viewModel The home screen ViewModel provided by Koin.
 * @param onNavigateToSettings Called when the user taps the settings cog icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    onNavigateToSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh once on first composition.
    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    // Refresh every time the screen comes back to the foreground (e.g. after returning from
    // system settings where the user may have granted a permission).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.home_settings_cog),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            StatsCard(
                selectedPeriod = uiState.selectedPeriod,
                wordsForPeriod = uiState.wordsForPeriod,
                onPeriodChange = viewModel::selectPeriod,
            )

            Spacer(modifier = Modifier.height(16.dp))

            PermissionsCard(
                uiState = uiState.permissionsState,
                onGrantClick = viewModel::onGrantPermission,
            )

            Spacer(modifier = Modifier.height(16.dp))

            val overlayButtonLabel = if (uiState.overlayEnabled) {
                stringResource(R.string.home_overlay_disable)
            } else {
                stringResource(R.string.home_overlay_enable)
            }
            Button(
                onClick = { viewModel.setOverlayEnabled(!uiState.overlayEnabled, context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = overlayButtonLabel)
            }
        }
    }
}
