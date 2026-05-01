package ru.er_log.dictate.core.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.get
import ru.er_log.dictate.R
import ru.er_log.dictate.core.accessibility.PasteController
import ru.er_log.dictate.core.accessibility.PasteResult
import ru.er_log.dictate.core.audio.WavRecorder
import ru.er_log.dictate.core.prefs.OverlayPositionStore
import ru.er_log.dictate.core.prefs.SettingsStore
import ru.er_log.dictate.core.transcribe.DictateRepository
import ru.er_log.dictate.core.transcribe.TranscribeResult
import ru.er_log.dictate.feature.stats.data.RecognitionLogger

public class FloatingButtonService : Service(), OverlayGestureListener {

    private lateinit var wavRecorder: WavRecorder
    private lateinit var dictateRepository: DictateRepository
    private lateinit var pasteController: PasteController
    private lateinit var recognitionLogger: RecognitionLogger
    private lateinit var settingsStore: SettingsStore
    private lateinit var overlayPositionStore: OverlayPositionStore
    private lateinit var vibrator: Vibrator
    private lateinit var clipboardManager: ClipboardManager

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var screenDensity: Float = 1f

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning.set(true)

        wavRecorder = get()
        dictateRepository = get()
        pasteController = get()
        recognitionLogger = get()
        settingsStore = get()
        overlayPositionStore = get()
        @Suppress("DEPRECATION")
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        startForeground(NOTIFICATION_ID, buildNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val (screenWidth, screenHeight) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }
            metrics.widthPixels to metrics.heightPixels
        }
        screenDensity = resources.displayMetrics.density

        val (initialX, initialY) = resolveInitialPosition(screenWidth, screenHeight, screenDensity)

        layoutParams = WindowManager.LayoutParams(
            BUTTON_SIZE_PX,
            BUTTON_SIZE_PX,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            x = initialX
            y = initialY
        }

        overlayView = OverlayView(this)
        overlayView.listener = this
        windowManager.addView(overlayView, layoutParams)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        isRunning.set(false)
        serviceScope.cancel()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        super.onDestroy()
    }

    override fun onDragStart() = Unit

    override fun onDrag(deltaXPx: Float, deltaYPx: Float) {
        layoutParams.x += deltaXPx.toInt()
        layoutParams.y += deltaYPx.toInt()
        windowManager.updateViewLayout(overlayView, layoutParams)
    }

    override fun onDragEnd(finalXPx: Int, finalYPx: Int) {
        val density = screenDensity
        serviceScope.launch {
            overlayPositionStore.setPosition(finalXPx / density, finalYPx / density)
        }
    }

    override fun onRecordStart() {
        val shouldVibrate = runBlocking { settingsStore.vibrateOnRecord.firstOrNull() } ?: true
        if (shouldVibrate) {
            vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        wavRecorder.start()
    }

    override fun onRecordEnd() {
        currentJob = serviceScope.launch {
            val wav = wavRecorder.stop()
            when (val result = dictateRepository.transcribe(wav)) {
                is TranscribeResult.Success -> handleSuccess(result)
                TranscribeResult.NetworkError -> handleNetworkError()
                is TranscribeResult.ServerError -> handleServerError()
            }
        }
    }

    override fun onRecordCancel() {
        currentJob?.cancel()
        serviceScope.launch {
            runCatching { wavRecorder.stop() }
        }
    }

    private suspend fun handleSuccess(result: TranscribeResult.Success) {
        recognitionLogger.log(result.words, result.durationSec)

        val pasteResult = pasteController.paste(result.text)

        when (pasteResult) {
            PasteResult.Success -> Unit
            PasteResult.NoFocus -> showToast(getString(R.string.toast_no_focus))
            PasteResult.NotAvailable -> {
                copyToClipboard(result.text)
                showToast(getString(R.string.toast_accessibility_off))
            }
        }

        if (pasteResult == PasteResult.Success) {
            val clipboardOnPaste = settingsStore.clipboardOnPaste.firstOrNull() ?: false
            if (clipboardOnPaste) {
                copyToClipboard(result.text)
            }
        }
    }

    private fun handleNetworkError() {
        vibrateTwice()
        showToast(getString(R.string.toast_server_unreachable))
    }

    private fun handleServerError() {
        vibrateTwice()
        showToast(getString(R.string.toast_server_error))
    }

    private fun vibrate(effect: VibrationEffect) {
        vibrator.vibrate(effect)
    }

    private fun vibrateTwice() {
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 80, 80), -1))
    }

    private fun copyToClipboard(text: String) {
        val clip = ClipData.newPlainText("dictate", text)
        clipboardManager.setPrimaryClip(clip)
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveInitialPosition(screenWidth: Int, screenHeight: Int, density: Float): Pair<Int, Int> {
        val savedPosition = runBlocking { overlayPositionStore.position.firstOrNull() }
        return if (savedPosition != null) {
            val xPx = (savedPosition.first * density).toInt()
            val yPx = (savedPosition.second * density).toInt()
            val clampedX = xPx.coerceIn(-screenWidth / 2, screenWidth / 2)
            val clampedY = yPx.coerceIn(-screenHeight / 2, screenHeight / 2)
            Pair(clampedX, clampedY)
        } else {
            val defaultX = screenWidth / 2 - BUTTON_SIZE_PX / 2 - DEFAULT_EDGE_MARGIN_PX
            val defaultY = 0
            Pair(defaultX, defaultY)
        }
    }

    private fun buildNotification(): Notification {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.floating_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.floating_notification_text))
            .setSmallIcon(R.drawable.ic_mic)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        /** True while the service is alive; read by OverlayTileService to reflect tile state. */
        val isRunning = java.util.concurrent.atomic.AtomicBoolean(false)

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "dictate_overlay"
        private const val BUTTON_SIZE_PX = 160
        private const val DEFAULT_EDGE_MARGIN_PX = 40
    }
}
