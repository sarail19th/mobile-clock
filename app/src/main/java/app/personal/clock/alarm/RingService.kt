package app.personal.clock.alarm

import app.personal.clock.sys
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.personal.clock.R
import app.personal.clock.Tabs
import app.personal.clock.data.Store
import app.personal.clock.ui.RingActivity
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Plays the alarm/timer sound while it rings.
 *
 * - Foreground service of type mediaPlayback (audio is the whole point of the service).
 * - Audio uses USAGE_ALARM, so it follows the ALARM volume (and DND's "alarms" exception), not media volume.
 * - The notification carries a full-screen intent so RingActivity appears over the lock screen.
 * - Stops itself after the configured auto-stop time and leaves a "missed" notification.
 */
class RingService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var info: RingInfo? = null
    private val autoStop = Runnable { onAutoStop() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin(intent)
            ACTION_SNOOZE -> snooze()
            ACTION_DISMISS -> dismiss()
            else -> if (info == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseResources()
        if (info != null) {
            info = null
            RingState.set(null)
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------------ start

    private fun begin(intent: Intent) {
        releaseResources() // a newer ring replaces whatever was ringing

        val kind = intent.getIntExtra(EXTRA_KIND, KIND_ALARM)
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val store = Store.get(this)
        val settings = store.settings()

        var label: String
        var soundUri: String? = null
        var vibrate = true
        var snoozeMin = 0
        if (kind == KIND_ALARM) {
            val a = store.alarm(id)
            label = a?.label?.ifBlank { null } ?: "Alarm"
            soundUri = a?.ringtoneUri
            vibrate = a?.vibrate ?: true
            snoozeMin = a?.snoozeMinutes ?: settings.defaultSnoozeMinutes
        } else {
            val t = store.timer(id)
            label = t?.label?.ifBlank { null } ?: "Timer"
        }
        val timeText = timeNow()
        val ring = RingInfo(kind, id, label, System.currentTimeMillis(), canSnooze = snoozeMin > 0, snoozeMinutes = snoozeMin)
        info = ring

        // Must be the first thing we do: the OS gives a few seconds to call startForeground.
        startForeground(
            Notifications.ID_RING,
            buildNotification(ring, timeText),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        RingState.set(ring)

        val autoStopMs = settings.autoStopMinutes * 60_000L
        wakeLock = sys<PowerManager>()
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "personalclock:ring")
            .apply { acquire(autoStopMs + 30_000L) }

        requestAudioFocus()
        if (soundUri != "") playSound(soundUri)
        if (vibrate) startVibration()
        handler.postDelayed(autoStop, autoStopMs)
    }

    private fun alarmAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun requestAudioFocus() {
        val am = sys<AudioManager>()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(alarmAttributes())
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = req
        am.requestAudioFocus(req)
    }

    private fun playSound(uriString: String?) {
        val chosen: Uri? = uriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val candidates = listOfNotNull(
            chosen,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        playCandidate(candidates, 0)
    }

    /** Tries each URI in turn; if the chosen sound can't be read (permission, deleted file) the next one plays. */
    private fun playCandidate(candidates: List<Uri>, index: Int) {
        if (index >= candidates.size || info == null) return
        val r = try {
            RingtoneManager.getRingtone(this, candidates[index])
        } catch (e: Exception) {
            null
        }
        if (r == null) {
            playCandidate(candidates, index + 1)
            return
        }
        try {
            r.audioAttributes = alarmAttributes()
            r.isLooping = true
            r.play()
        } catch (e: Exception) {
            playCandidate(candidates, index + 1)
            return
        }
        ringtone = r
        handler.postDelayed({
            if (info != null && ringtone === r && !r.isPlaying) {
                runCatching { r.stop() }
                ringtone = null
                playCandidate(candidates, index + 1)
            }
        }, 900L)
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val v = sys<VibratorManager>().defaultVibrator
        vibrator = v
        if (!v.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0)
        v.vibrate(effect, alarmAttributes())
    }

    private fun buildNotification(ring: RingInfo, timeText: String): Notification {
        val fullScreen = PendingIntent.getActivity(
            this, 1,
            Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = Notification.Builder(this, Notifications.CH_RING)
            .setSmallIcon(Icon.createWithResource(this, R.drawable.ic_stat_clock))
            .setContentTitle(ring.label)
            .setContentText(if (ring.kind == KIND_TIMER) "Timer finished" else timeText)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
        if (ring.canSnooze) {
            b.addAction(
                Notification.Action.Builder(
                    null, "Snooze ${ring.snoozeMinutes} min", actionIntent(ACTION_SNOOZE, 2),
                ).build(),
            )
        }
        b.addAction(Notification.Action.Builder(null, "Dismiss", actionIntent(ACTION_DISMISS, 3)).build())
        return b.build()
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode, Intent(this, RingService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // ------------------------------------------------------------------ stop paths

    private fun snooze() {
        val ring = info
        if (ring != null && ring.kind == KIND_ALARM && ring.canSnooze) {
            val at = AlarmScheduler.scheduleSnooze(this, ring.id, ring.snoozeMinutes)
            val whenText = timeAt(at)
            Notifications.postSnoozed(this, ring.id, ring.label, whenText)
        }
        finishRinging()
    }

    private fun dismiss() {
        val ring = info
        if (ring != null && ring.kind == KIND_ALARM) {
            AlarmScheduler.cancelSnooze(this, ring.id)
            Notifications.cancelSnoozed(this, ring.id)
        }
        finishRinging()
    }

    private fun onAutoStop() {
        val ring = info ?: return
        val kindText = if (ring.kind == KIND_TIMER) "Missed timer" else "Missed alarm"
        Notifications.postMissed(this, "$kindText: ${ring.label}", "Stopped automatically after ${Store.get(this).settings().autoStopMinutes} min", ring.id)
        finishRinging()
    }

    private fun finishRinging() {
        releaseResources()
        info = null
        RingState.set(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseResources() {
        handler.removeCallbacksAndMessages(null)
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        focusRequest?.let { req ->
            runCatching { sys<AudioManager>().abandonAudioFocusRequest(req) }
        }
        focusRequest = null
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    private fun timeNow(): String = timeAt(System.currentTimeMillis())

    private fun timeAt(epochMs: Long): String {
        val zone = Store.get(this).settings().zone()
        return Instant.ofEpochMilli(epochMs).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    }

    companion object {
        const val ACTION_START = "app.personal.clock.action.RING_START"
        const val ACTION_SNOOZE = "app.personal.clock.action.RING_SNOOZE"
        const val ACTION_DISMISS = "app.personal.clock.action.RING_DISMISS"
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"
        const val KIND_ALARM = 0
        const val KIND_TIMER = 1

        /** Starts ringing. If the OS refuses to start the service, leave a visible "missed" notice instead of crashing. */
        fun start(ctx: Context, kind: Int, id: Int) {
            val i = Intent(ctx, RingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_ID, id)
            try {
                ctx.startForegroundService(i)
            } catch (e: Exception) {
                Notifications.postMissed(
                    ctx, "Could not start the alarm sound",
                    "Check Settings > Permission health (battery: Unrestricted).", id,
                )
            }
        }

        fun snooze(ctx: Context) {
            ctx.startService(Intent(ctx, RingService::class.java).setAction(ACTION_SNOOZE))
        }

        fun dismiss(ctx: Context) {
            ctx.startService(Intent(ctx, RingService::class.java).setAction(ACTION_DISMISS))
        }
    }
}
