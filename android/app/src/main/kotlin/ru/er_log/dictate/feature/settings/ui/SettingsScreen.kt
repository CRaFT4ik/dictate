package ru.er_log.dictate.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.koin.androidx.compose.koinViewModel
import ru.er_log.dictate.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    onBack: () -> Unit,
) {
    val clipboardOnPaste by viewModel.clipboardOnPaste.collectAsStateWithLifecycle()
    val vibrateOnRecord by viewModel.vibrateOnRecord.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_clipboard_on_paste)) },
                supportingContent = { Text(stringResource(R.string.settings_clipboard_on_paste_desc)) },
                trailingContent = {
                    Switch(
                        checked = clipboardOnPaste,
                        onCheckedChange = viewModel::setClipboardOnPaste,
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_vibrate_on_record)) },
                trailingContent = {
                    Switch(
                        checked = vibrateOnRecord,
                        onCheckedChange = viewModel::setVibrateOnRecord,
                    )
                },
            )
        }
    }
}
