package ru.er_log.dictate.core.accessibility

public interface PasteController {
    public suspend fun paste(text: String): PasteResult
}
