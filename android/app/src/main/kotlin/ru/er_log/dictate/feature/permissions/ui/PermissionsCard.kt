package ru.er_log.dictate.feature.permissions.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.er_log.dictate.R
import ru.er_log.dictate.feature.permissions.domain.Permission
import ru.er_log.dictate.feature.permissions.domain.PermissionStatus

@Composable
public fun PermissionsCard(
    uiState: PermissionsUiState,
    onGrantClick: (Permission) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAccessibilitySheet by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (uiState.allGranted) {
                Text(
                    text = stringResource(R.string.permission_card_ready),
                    color = Color(0xFF2E7D32),
                )
            } else {
                Text(text = stringResource(R.string.permission_card_title))

                PermissionRow(
                    label = stringResource(R.string.permission_microphone),
                    status = uiState.statuses[Permission.Microphone] ?: PermissionStatus.DENIED,
                    onGrant = { onGrantClick(Permission.Microphone) },
                )
                PermissionRow(
                    label = stringResource(R.string.permission_overlay),
                    status = uiState.statuses[Permission.Overlay] ?: PermissionStatus.DENIED,
                    onGrant = { onGrantClick(Permission.Overlay) },
                )
                PermissionRow(
                    label = stringResource(R.string.permission_accessibility),
                    status = uiState.statuses[Permission.Accessibility] ?: PermissionStatus.DENIED,
                    onGrant = { showAccessibilitySheet = true },
                )
            }
        }
    }

    if (showAccessibilitySheet) {
        AccessibilityHelpSheet(
            onDismiss = { showAccessibilitySheet = false },
            onOpenSettings = { onGrantClick(Permission.Accessibility) },
        )
    }
}

@Composable
private fun PermissionRow(
    label: String,
    status: PermissionStatus,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val statusIcon = if (status == PermissionStatus.GRANTED) "✓" else "⚠"
        val statusColor = if (status == PermissionStatus.GRANTED) Color(0xFF2E7D32) else Color(0xFFF9A825)
        Text(text = statusIcon, color = statusColor)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, modifier = Modifier.weight(1f))
        if (status == PermissionStatus.DENIED) {
            Button(onClick = onGrant) {
                Text(text = stringResource(R.string.permission_grant))
            }
        }
    }
}
