package ru.er_log.dictate.feature.permissions.domain

public sealed class Permission {
    public data object Microphone : Permission()
    public data object Overlay : Permission()
    public data object Accessibility : Permission()
}
