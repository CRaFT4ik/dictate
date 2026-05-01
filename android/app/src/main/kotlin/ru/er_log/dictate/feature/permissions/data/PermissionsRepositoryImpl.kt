package ru.er_log.dictate.feature.permissions.data

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.er_log.dictate.feature.permissions.domain.Permission
import ru.er_log.dictate.feature.permissions.domain.PermissionStatus
import ru.er_log.dictate.feature.permissions.domain.PermissionsRepository

public class PermissionsRepositoryImpl(private val context: Context) : PermissionsRepository {

    private val _state = MutableStateFlow(computeStatuses())

    override fun observe(): Flow<Map<Permission, PermissionStatus>> = _state.asStateFlow()

    override fun refresh() {
        _state.value = computeStatuses()
    }

    override fun openSettings(permission: Permission) {
        val intent = when (permission) {
            Permission.Microphone -> Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
            Permission.Overlay -> Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            Permission.Accessibility -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun computeStatuses(): Map<Permission, PermissionStatus> = mapOf(
        Permission.Microphone to microphoneStatus(),
        Permission.Overlay to overlayStatus(),
        Permission.Accessibility to accessibilityStatus(),
    )

    private fun microphoneStatus(): PermissionStatus =
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            PermissionStatus.GRANTED
        else
            PermissionStatus.DENIED

    private fun overlayStatus(): PermissionStatus =
        if (Settings.canDrawOverlays(context)) PermissionStatus.GRANTED else PermissionStatus.DENIED

    private fun accessibilityStatus(): PermissionStatus {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val serviceId = "${context.packageName}/.core.accessibility.PasteAccessibilityService"
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.id == serviceId }
        return if (enabled) PermissionStatus.GRANTED else PermissionStatus.DENIED
    }
}
