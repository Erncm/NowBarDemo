package com.nowbar.demo

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private val channelId = "live"
    private val idTimer = 1
    private val idMedia = 2
    private val h = Handler(Looper.getMainLooper())
    private lateinit var nm: NotificationManager
    private lateinit var status: TextView
    private var session: MediaSession? = null
    private var startAt = 0L
    private var endAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (now >= endAt) {
                nm.cancel(idTimer)
                status.text = "Sayaç bitti"
                return
            }
            val p = ((now - startAt) * 100 / (endAt - startAt)).toInt().coerceIn(0, 100)
            nm.notify(idTimer, buildTimer(p))
            h.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Canlı bildirimler", NotificationManager.IMPORTANCE_DEFAULT)
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        status = TextView(this).apply { textSize = 15f; gravity = Gravity.CENTER }
        fun btn(t: String, a: () -> Unit) = Button(this).apply {
            text = t; setOnClickListener { a() }
        }
        root.addView(status)
        root.addView(btn("5 dk sayaç başlat (Now Bar)") { startTimer(5 * 60 * 1000L) })
        root.addView(btn("30 sn hızlı test") { startTimer(30 * 1000L) })
        root.addView(btn("Medya kartı (Now Bar)") { startMedia() })
        root.addView(btn("Sayaç → Now Bar (medya kartı, 2 dk)") { startMediaTimer(2 * 60 * 1000L) })
        root.addView(btn("Hepsini kaldır") { stopAll() })
        root.addView(btn("Canlı bildirim izni / ayarı") { openSettings() })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val ok = if (Build.VERSION.SDK_INT >= 36) nm.canPostPromotedNotifications() else false
        status.text = when {
            Build.VERSION.SDK_INT < 36 -> "Android 16 / One UI 8 değil: sadece medya kartı Now Bar'a düşer."
            ok -> "Canlı bildirim izni AÇIK ✔"
            else -> "Canlı bildirim izni KAPALI — alttaki ayar butonundan aç."
        }
    }

    private fun contentIntent() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildTimer(progress: Int): Notification {
        val b = Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Odak sayacı")
            .setContentText("Geri sayım sürüyor")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(endAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(contentIntent())
        // Samsung Now Bar için resmi olmayan ek alanlar (deneme)
        b.addExtras(Bundle().apply {
            putInt("android.ongoingActivityNoti.style", 1)
            putString("android.ongoingActivityNoti.primaryInfo", "Odak")
            putString("android.ongoingActivityNoti.secondaryInfo", "Sayaç")
            putString("android.ongoingActivityNoti.nowbarPrimaryInfo", "Odak sayacı")
            putString("android.ongoingActivityNoti.nowbarSecondaryInfo", "Geri sayım")
            putString("android.ongoingActivityNoti.chipExpandedText", "Odak")
        })
        if (Build.VERSION.SDK_INT >= 36) {
            b.addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
            b.setStyle(
                Notification.ProgressStyle()
                    .setProgress(progress)
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100)))
            )
        }
        return b.build()
    }

    private fun startTimer(ms: Long) {
        h.removeCallbacks(tick)
        startAt = System.currentTimeMillis()
        endAt = startAt + ms
        status.text = "Sayaç çalışıyor — Now Bar'a bak"
        tick.run()
    }

    private fun startMedia() {
        val s = session ?: MediaSession(this, "nowbar").also { session = it }
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Now Bar Testi")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Demo")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 200000L)
                .build()
        )
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO
                )
                .setState(PlaybackState.STATE_PLAYING, 0L, 1f, SystemClock.elapsedRealtime())
                .build()
        )
        s.isActive = true
        nm.notify(idMedia, mediaNotif(s, "Now Bar Testi", "Demo"))
    }

    private fun mediaNotif(s: MediaSession, title: String, text: String): Notification =
        Notification.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(contentIntent())
            .setStyle(Notification.MediaStyle().setMediaSession(s.sessionToken))
            .build()

    // Now Bar'da kesin çalışan yol: sayacı medya kartı olarak göster
    private val mediaTick = object : Runnable {
        override fun run() {
            val s = session ?: return
            val left = endAt - System.currentTimeMillis()
            if (left <= 0) {
                stopAll()
                status.text = "Sayaç bitti"
                return
            }
            val sec = left / 1000
            s.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Kalan %02d:%02d".format(sec / 60, sec % 60))
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Odak sayacı")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, endAt - startAt)
                    .build()
            )
            h.postDelayed(this, 1000)
        }
    }

    private fun startMediaTimer(ms: Long) {
        h.removeCallbacks(tick)
        h.removeCallbacks(mediaTick)
        nm.cancel(idTimer)
        startAt = System.currentTimeMillis()
        endAt = startAt + ms
        val s = session ?: MediaSession(this, "nowbar").also { session = it }
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY_PAUSE)
                .setState(PlaybackState.STATE_PLAYING, 0L, 1f, SystemClock.elapsedRealtime())
                .build()
        )
        s.isActive = true
        nm.notify(idMedia, mediaNotif(s, "Odak sayacı", "Geri sayım"))
        status.text = "Medya kartı sayacı çalışıyor — kilit ekranına bak"
        mediaTick.run()
    }

    private fun stopAll() {
        h.removeCallbacks(tick)
        h.removeCallbacks(mediaTick)
        nm.cancel(idTimer)
        nm.cancel(idMedia)
        session?.release()
        session = null
    }

    private fun openSettings() {
        try {
            startActivity(
                Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS")
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (e: Exception) {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }
    }

    override fun onDestroy() {
        h.removeCallbacks(tick)
        super.onDestroy()
    }
}
