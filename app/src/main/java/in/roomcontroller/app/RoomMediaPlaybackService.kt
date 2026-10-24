package `in`.roomcontroller.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground MediaSession service that keeps room music / Spotify playback state,
 * lock-screen media controls, hardware headset buttons, and real-time track progress
 * active when the app is in the background or the display is off.
 */
class RoomMediaPlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var progressTickerJob: Job? = null

    private var currentTrack: String = ""
    private var currentArtist: String = ""
    private var currentDevice: String = ""
    private var isPlaying: Boolean = false
    private var progressMs: Long = 0L
    private var durationMs: Long = 214_000L
    private var lastSampleElapsedMs: Long = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        ensureNotificationChannel()
        val session = MediaSession(this, "RoomMediaPlaybackSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    dispatchMediaAction("play")
                }

                override fun onPause() {
                    dispatchMediaAction("pause")
                }

                override fun onSkipToNext() {
                    dispatchMediaAction("next")
                }

                override fun onSkipToPrevious() {
                    dispatchMediaAction("previous")
                }

                override fun onSeekTo(pos: Long) {
                    progressMs = pos.coerceIn(0L, durationMs)
                    lastSampleElapsedMs = System.currentTimeMillis()
                    updateMediaSessionState()
                    dispatchMediaAction("seek", pos.toInt())
                }
            })
            isActive = true
        }
        mediaSession = session
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SYNC_STATE -> {
                currentTrack = intent.getStringExtra(EXTRA_TRACK)?.ifBlank { currentTrack } ?: currentTrack
                currentArtist = intent.getStringExtra(EXTRA_ARTIST)?.ifBlank { currentArtist } ?: currentArtist
                currentDevice = intent.getStringExtra(EXTRA_DEVICE)?.ifBlank { currentDevice } ?: currentDevice
                isPlaying = intent.getBooleanExtra(EXTRA_PLAYING, isPlaying)
                durationMs = intent.getLongExtra(EXTRA_DURATION_MS, durationMs).coerceAtLeast(1000L)
                progressMs = intent.getLongExtra(EXTRA_PROGRESS_MS, progressMs).coerceIn(0L, durationMs)
                lastSampleElapsedMs = System.currentTimeMillis()

                updateMediaSessionState()
                val notification = buildNotification()
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                }
                manageProgressTicker()
            }
            ACTION_USER_COMMAND -> {
                val cmd = intent.getStringExtra(EXTRA_COMMAND).orEmpty()
                val seekPos = intent.getIntExtra(EXTRA_SEEK_MS, 0)
                if (cmd.isNotBlank()) {
                    when (cmd) {
                        "play" -> isPlaying = true
                        "pause" -> isPlaying = false
                        "seek" -> progressMs = seekPos.toLong().coerceIn(0L, durationMs)
                    }
                    lastSampleElapsedMs = System.currentTimeMillis()
                    updateMediaSessionState()
                    val nm = getSystemService(NotificationManager::class.java)
                    runCatching { nm?.notify(NOTIFICATION_ID, buildNotification()) }
                    dispatchMediaAction(cmd, seekPos)
                    manageProgressTicker()
                }
            }
            ACTION_STOP_SERVICE -> {
                progressTickerJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun manageProgressTicker() {
        progressTickerJob?.cancel()
        if (!isPlaying) return
        progressTickerJob = serviceScope.launch {
            while (isActive && isPlaying) {
                delay(1000L)
                val now = System.currentTimeMillis()
                val delta = (now - lastSampleElapsedMs).coerceAtLeast(0L)
                lastSampleElapsedMs = now
                progressMs = (progressMs + delta).coerceAtMost(durationMs)
                updateMediaSessionState()
            }
        }
    }

    private fun dispatchMediaAction(command: String, value: Int = 0) {
        val activeCallback = onMediaTransportCommand
        if (activeCallback != null) {
            activeCallback.invoke(command, value)
        } else {
            // Standalone Fallback: Dispatch command directly to ESP32 when RoomModel ViewModel is dead
            serviceScope.launch(Dispatchers.IO) {
                runCatching {
                    val pairingJson = TokenVault(this@RoomMediaPlaybackService, "room-device-pairing").read()
                    if (pairingJson.isBlank()) return@launch
                    val pairing = DevicePairing.parseFlexible(pairingJson)
                    val origin = getSharedPreferences("room-settings", Context.MODE_PRIVATE)
                        .getString("endpoint", "")?.takeIf { it.isNotBlank() } ?: "https://${pairing.setupIp}"
                    val transport = DeviceTransport(this@RoomMediaPlaybackService, pairing)
                    transport.call(
                        origin,
                        "/v1/command",
                        obj("op" to "spotify", "action" to command, "argument" to "", "value" to value)
                    )
                }
            }
        }
    }

    private fun updateMediaSessionState() {
        val session = mediaSession ?: return
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, currentTrack)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "$currentArtist · $currentDevice")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
            .build()
        session.setMetadata(metadata)

        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_SEEK_TO

        val stateCode = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val pbState = PlaybackState.Builder()
            .setActions(actions)
            .setState(stateCode, progressMs, if (isPlaying) 1.0f else 0.0f)
            .build()
        session.setPlaybackState(pbState)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun actionPending(code: Int, cmd: String): PendingIntent {
            val i = Intent(this, RoomMediaPlaybackService::class.java).apply {
                action = ACTION_USER_COMMAND
                putExtra(EXTRA_COMMAND, cmd)
            }
            return PendingIntent.getService(
                this,
                code,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val prevAction = Notification.Action.Builder(
            android.R.drawable.ic_media_previous,
            "Previous",
            actionPending(101, "previous")
        ).build()

        val playPauseAction = Notification.Action.Builder(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play",
            actionPending(102, if (isPlaying) "pause" else "play")
        ).build()

        val nextAction = Notification.Action.Builder(
            android.R.drawable.ic_media_next,
            "Next",
            actionPending(103, "next")
        ).build()

        val style = Notification.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val stopIntent = Intent(this, RoomMediaPlaybackService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val deletePending = PendingIntent.getService(
            this,
            109,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_room)
            .setContentTitle(currentTrack)
            .setContentText("$currentArtist · $currentDevice")
            .setSubText(if (isPlaying) "Playing in Room" else "Paused")
            .setContentIntent(contentPending)
            .setDeleteIntent(deletePending)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(style)
            .build()
    }

    private fun ensureNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Room Music & Spotify Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background playback status and lock-screen controls for Room Music"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isServiceRunning = false
        progressTickerJob?.cancel()
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "room_media_playback_channel"
        private const val NOTIFICATION_ID = 4201
        const val ACTION_SYNC_STATE = "in.roomcontroller.app.MEDIA_SYNC_STATE"
        const val ACTION_USER_COMMAND = "in.roomcontroller.app.MEDIA_USER_COMMAND"
        const val ACTION_STOP_SERVICE = "in.roomcontroller.app.MEDIA_STOP_SERVICE"

        const val EXTRA_TRACK = "extra_track"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_DEVICE = "extra_device"
        const val EXTRA_PLAYING = "extra_playing"
        const val EXTRA_PROGRESS_MS = "extra_progress_ms"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_COMMAND = "extra_command"
        const val EXTRA_SEEK_MS = "extra_seek_ms"

        @Volatile
        var isServiceRunning: Boolean = false

        @Volatile
        var onMediaTransportCommand: ((command: String, value: Int) -> Unit)? = null

        fun syncPlayback(
            context: Context,
            track: String,
            artist: String,
            device: String,
            playing: Boolean,
            progressMs: Long,
            durationMs: Long
        ) {
            // Gate media notification: Only start foreground service if playing or already active, and track is real
            if ((!playing && !isServiceRunning) || track.isBlank()) return

            runCatching {
                val intent = Intent(context, RoomMediaPlaybackService::class.java).apply {
                    action = ACTION_SYNC_STATE
                    putExtra(EXTRA_TRACK, track)
                    putExtra(EXTRA_ARTIST, artist)
                    putExtra(EXTRA_DEVICE, device)
                    putExtra(EXTRA_PLAYING, playing)
                    putExtra(EXTRA_PROGRESS_MS, progressMs)
                    putExtra(EXTRA_DURATION_MS, durationMs)
                }
                if (playing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stopMediaPlayback(context: Context) {
            isServiceRunning = false
            runCatching {
                val intent = Intent(context, RoomMediaPlaybackService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                context.startService(intent)
            }
        }
    }
}
