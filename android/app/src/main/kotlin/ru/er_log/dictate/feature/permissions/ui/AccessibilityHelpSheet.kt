package ru.er_log.dictate.feature.permissions.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.er_log.dictate.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun AccessibilityHelpSheet(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(text = stringResource(R.string.accessibility_help_title))
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = stringResource(R.string.accessibility_help_step1))
            Text(text = stringResource(R.string.accessibility_help_step2))
            Text(text = stringResource(R.string.accessibility_help_step3))
            Text(text = stringResource(R.string.accessibility_help_step4))
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    onOpenSettings()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.accessibility_help_open_settings))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
