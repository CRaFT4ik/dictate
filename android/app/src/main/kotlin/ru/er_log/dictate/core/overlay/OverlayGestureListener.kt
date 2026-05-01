package ru.er_log.dictate.core.overlay

public interface OverlayGestureListener {
    public fun onDragStart()
    public fun onDrag(deltaXPx: Float, deltaYPx: Float)
    public fun onDragEnd(finalXPx: Int, finalYPx: Int)
    public fun onRecordStart()
    public fun onRecordEnd()
    public fun onRecordCancel()
}
