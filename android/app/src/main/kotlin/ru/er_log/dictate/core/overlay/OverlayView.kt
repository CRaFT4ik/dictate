package ru.er_log.dictate.core.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import ru.er_log.dictate.R

/**
 * Circular floating button drawn programmatically. Background is a solid circle rendered via
 * [Paint]; the mic icon sits centered on top as a drawable.
 *
 * Implements an IDLE / DRAG / RECORD state machine via [onTouchEvent]. The service sets
 * [listener] after constructing the view and receives gesture callbacks through [OverlayGestureListener].
 */
public class OverlayView(context: Context) : View(context) {

    public var listener: OverlayGestureListener? = null

    private enum class State { IDLE, DRAG, RECORD }

    private var state = State.IDLE

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val recordHandler = Handler(Looper.getMainLooper())
    private val recordRunnable = Runnable { onRecordTimerFired() }

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC6650A4")
        style = Paint.Style.FILL
    }

    private val micDrawable = ContextCompat.getDrawable(context, R.drawable.ic_mic)

    init {
        contentDescription = "Dictate floating button"
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy)
        canvas.drawCircle(cx, cy, radius, circlePaint)

        micDrawable?.let { drawable ->
            val iconSize = (radius * 0.6f).toInt()
            val left = (width - iconSize) / 2
            val top = (height - iconSize) / 2
            drawable.setBounds(left, top, left + iconSize, top + iconSize)
            drawable.draw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP -> handleUp(event)
            MotionEvent.ACTION_CANCEL -> handleCancel(event)
            MotionEvent.ACTION_POINTER_DOWN -> Unit
        }
        return true
    }

    private fun handleDown(event: MotionEvent) {
        if (state != State.IDLE) return
        downRawX = event.rawX
        downRawY = event.rawY
        lastRawX = event.rawX
        lastRawY = event.rawY
        recordHandler.postDelayed(recordRunnable, RECORD_HOLD_MS)
    }

    private fun handleMove(event: MotionEvent) {
        when (state) {
            State.IDLE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (dx * dx + dy * dy > touchSlop * touchSlop) {
                    recordHandler.removeCallbacks(recordRunnable)
                    state = State.DRAG
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    listener?.onDragStart()
                }
            }
            State.DRAG -> {
                val deltaX = event.rawX - lastRawX
                val deltaY = event.rawY - lastRawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                listener?.onDrag(deltaX, deltaY)
            }
            State.RECORD -> Unit
        }
    }

    private fun handleUp(event: MotionEvent) {
        when (state) {
            State.IDLE -> {
                recordHandler.removeCallbacks(recordRunnable)
            }
            State.DRAG -> {
                val finalX = (layoutParams as? android.view.WindowManager.LayoutParams)?.x ?: event.rawX.toInt()
                val finalY = (layoutParams as? android.view.WindowManager.LayoutParams)?.y ?: event.rawY.toInt()
                state = State.IDLE
                listener?.onDragEnd(finalX, finalY)
            }
            State.RECORD -> {
                state = State.IDLE
                setRecordingActive(false)
                listener?.onRecordEnd()
            }
        }
    }

    private fun handleCancel(event: MotionEvent) {
        when (state) {
            State.IDLE -> {
                recordHandler.removeCallbacks(recordRunnable)
            }
            State.DRAG -> {
                recordHandler.removeCallbacks(recordRunnable)
                val finalX = (layoutParams as? android.view.WindowManager.LayoutParams)?.x ?: event.rawX.toInt()
                val finalY = (layoutParams as? android.view.WindowManager.LayoutParams)?.y ?: event.rawY.toInt()
                state = State.IDLE
                listener?.onDragEnd(finalX, finalY)
            }
            State.RECORD -> {
                state = State.IDLE
                setRecordingActive(false)
                listener?.onRecordCancel()
            }
        }
    }

    private fun onRecordTimerFired() {
        if (state != State.IDLE) return
        state = State.RECORD
        setRecordingActive(true)
        listener?.onRecordStart()
    }

    /** Called when entering/leaving RECORD state. Tints the button red while recording. */
    public fun setRecordingActive(active: Boolean) {
        circlePaint.color = if (active) Color.parseColor("#CCE53935") else Color.parseColor("#CC6650A4")
        invalidate()
    }

    private companion object {
        const val RECORD_HOLD_MS = 200L
    }
}
