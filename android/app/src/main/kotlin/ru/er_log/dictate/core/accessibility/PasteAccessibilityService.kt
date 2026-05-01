package ru.er_log.dictate.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicReference

public class PasteAccessibilityService : AccessibilityService() {

    public companion object {
        public val holder: AtomicReference<PasteAccessibilityService?> = AtomicReference(null)
    }

    override fun onServiceConnected() {
        holder.set(this)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        holder.set(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        holder.set(null)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
