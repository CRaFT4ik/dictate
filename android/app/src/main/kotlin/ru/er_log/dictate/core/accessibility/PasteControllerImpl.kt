package ru.er_log.dictate.core.accessibility

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

public class PasteControllerImpl(private val context: Context) : PasteController {

    override suspend fun paste(text: String): PasteResult {
        val service = PasteAccessibilityService.holder.get() ?: return PasteResult.NotAvailable

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("dictate", text))

        val node = findFocusedNode(service) ?: return PasteResult.NoFocus

        val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        if (!pasted) {
            node.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
            )
        }

        return PasteResult.Success
    }

    private fun findFocusedNode(service: PasteAccessibilityService): AccessibilityNodeInfo? {
        service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?.let { return it }

        for (window in service.windows ?: emptyList()) {
            window.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { return it }
        }

        return null
    }
}
