package ru.er_log.dictate.core.accessibility

public sealed class PasteResult {
    public data object Success : PasteResult()
    public data object NoFocus : PasteResult()
    public data object NotAvailable : PasteResult()
}
