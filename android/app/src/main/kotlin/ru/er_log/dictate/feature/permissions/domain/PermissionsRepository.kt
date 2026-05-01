package ru.er_log.dictate.feature.permissions.domain

import kotlinx.coroutines.flow.Flow

public interface PermissionsRepository {
    public fun observe(): Flow<Map<Permission, PermissionStatus>>
    public fun refresh()
    public fun openSettings(permission: Permission)
}
