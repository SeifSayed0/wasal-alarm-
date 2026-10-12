package com.seifsayed0.wasalalarm.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.seifsayed0.wasalalarm.MainActivity
import com.seifsayed0.wasalalarm.R
import com.seifsayed0.wasalalarm.WasalApplication
import com.seifsayed0.wasalalarm.data.LocalDataStore
import com.seifsayed0.wasalalarm.data.LocalAppDataLogic
import com.seifsayed0.wasalalarm.data.LocalDataRead
import com.seifsayed0.wasalalarm.data.TripStore
import com.seifsayed0.wasalalarm.domain.*
import com.seifsayed0.wasalalarm.ui.AlarmActivity
import java.util.Locale

@SuppressLint("LogNotTimber")
class LocationTrackingService : Service() {
    private lateinit var store: TripStore
    private lateinit var localStore: LocalDataStore
    private lateinit var fused: FusedLocationProviderClient
    private val handler = Handler(Looper.getMainLooper())
    private var callback: LocationCallback? = null
    private var watchdog: Runnable? = null
    private var player: MediaPlayer? = null
    private var trip: Trip? = null
    private var alarmVibrator: Vibrator? = null
    private var retryCount = 0
    private var trackingStartedElapsed = 0L

    override fun onCreate() {
        super.onCreate()
        store = TripStore(this)
        localStore = LocalDataStore(this)
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                finishTrip()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                pauseTrip()
                return START_STICKY
            }
            ACTION_RESUME -> {
                val restored = restoreTrip() ?: run { stopSelf(startId); return START_NOT_STICKY }
                trip = ArrivalLogic.resumeTrip(restored).copy(statusMessage = "جارٍ استئناف التتبّع", status = TripStatus.TRACKING)
                if (!store.save(trip!!)) {
                    trip = trip!!.copy(paused = true, status = TripStatus.SERVICE_ERROR, statusMessage = "تعذّر حفظ حالة الرحلة؛ لم يُستأنف التتبّع")
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
            }
            ACTION_START -> {
                val existing = restoreTrip()
                if (existing != null) {
                    trip = existing
                } else {
                    val lat = intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN)
                    val lon = intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN)
                    val radius = intent.getIntExtra(EXTRA_RADIUS, -1)
                    val name = intent.getStringExtra(EXTRA_NAME).orEmpty().take(80)
                    val settings = localStore.load().settings
                    val candidate = runCatching {
                        ArrivalLogic.startNewTrip(Coordinate(lat, lon), radius, System.currentTimeMillis(), name, settings)
                    }.getOrNull()
                    if (candidate == null) {
                        stopSelf(startId)
                        return START_NOT_STICKY
                    }
                    trip = candidate
                    retryCount = 0
                }
                stopLocationUpdates()
                stopAlarmPlayback()
                if (!store.save(trip!!)) {
                    trip = trip!!.copy(paused = true, status = TripStatus.SERVICE_ERROR, statusMessage = "تعذّر حفظ الرحلة على الجهاز؛ لم يبدأ التتبّع")
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
            }
            null -> {
                val restored = restoreTrip() ?: run { stopSelf(startId); return START_NOT_STICKY }
                trip = restored
            }
            else -> {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }

        if (!hasLocationPermission()) {
            updateStatus("إذن الموقع غير متاح؛ أوقفت التتبّع", TripStatus.PERMISSION_REQUIRED)
            finishTrip(TripOutcome.INTERRUPTED)
            return START_NOT_STICKY
        }
        if (!isLocationEnabled()) {
            val current = trip ?: return START_NOT_STICKY
            trip = current.copy(paused = true, status = TripStatus.GPS_DISABLED, statusMessage = "فعّل الموقع ثم اضغط استئناف التتبّع")
            store.save(trip!!)
            if (!startForegroundSafely(pausedNotification())) return START_NOT_STICKY
            stopLocationUpdates()
            return START_NOT_STICKY
        }

        val current = trip ?: run { stopSelf(startId); return START_NOT_STICKY }
        val notification = when {
            current.alarmTriggered -> alarmNotification()
            current.paused -> pausedNotification()
            else -> trackingNotification()
        }
        if (!startForegroundSafely(notification)) return START_NOT_STICKY
        when {
            current.alarmTriggered -> stopLocationUpdates() // Never replay sound/vibration on process restoration.
            current.paused -> stopLocationUpdates()
            else -> beginLocationUpdates()
        }
        return START_STICKY
    }

    private fun restoreTrip(): Trip? = store.load()?.takeIf(ArrivalLogic::canRecover)

    private fun startForegroundSafely(notification: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (error: SecurityException) {
        Log.w(TAG, "Foreground location service permission/start condition failed", error)
        trip?.let { current ->
            val failed = current.copy(paused = true, status = TripStatus.SERVICE_ERROR, statusMessage = "تعذّر بدء خدمة التتبّع؛ راجع أذونات الموقع والإشعارات")
            trip = failed
            store.save(failed)
        }
        stopLocationUpdates()
        stopAlarmPlayback()
        stopSelf()
        false
    } catch (error: RuntimeException) {
        Log.w(TAG, "Foreground service could not start", error)
        trip?.let { current ->
            val failed = current.copy(paused = true, status = TripStatus.SERVICE_ERROR, statusMessage = "تعذّر بدء خدمة التتبّع؛ أعد المحاولة من التطبيق")
            trip = failed
            store.save(failed)
        }
        stopLocationUpdates()
        stopAlarmPlayback()
        stopSelf()
        false
    }

    private fun beginLocationUpdates() {
        stopLocationUpdates()
        if (!hasLocationPermission()) {
            updateStatus("إذن الموقع غير متاح", TripStatus.PERMISSION_REQUIRED)
            return
        }
        val current = trip ?: return
        trackingStartedElapsed = SystemClock.elapsedRealtime()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setMinUpdateDistanceMeters(20f)
            .setMaxUpdateDelayMillis(20_000L)
            .setWaitForAccurateLocation(false)
            .build()
        val next = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.maxByOrNull { it.elapsedRealtimeNanos }?.let(::handleLocation)
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) updateStatus("إشارة الموقع ضعيفة أو غير متاحة؛ أنتظر تحديثاً صالحاً", TripStatus.WEAK_SIGNAL)
            }
        }
        callback = next
        try {
            fused.requestLocationUpdates(request, next, mainLooper)
                .addOnFailureListener { error ->
                    if (callback === next) {
                        stopLocationUpdates()
                        updateStatus("تعذّر الحصول على تحديثات الموقع؛ افحص خدمات الموقع ثم استأنف الرحلة", TripStatus.LOCATION_UNAVAILABLE)
                        Log.w(TAG, "Location updates request failed", error)
                    }
                }
            scheduleWatchdog()
        } catch (error: SecurityException) {
            updateStatus("سُحب إذن الموقع؛ أوقفت التتبّع", TripStatus.PERMISSION_REQUIRED)
            finishTrip(TripOutcome.INTERRUPTED)
        } catch (error: RuntimeException) {
            updateStatus("تعذّر تشغيل مزوّد الموقع", TripStatus.SERVICE_ERROR)
            Log.w(TAG, "Location provider could not start", error)
        }
    }

    private fun scheduleWatchdog() {
        watchdog?.let(handler::removeCallbacks)
        val expectedId = trip?.id ?: return
        val next = Runnable {
            val current = trip ?: return@Runnable
            if (current.id != expectedId || current.paused || current.alarmTriggered || !current.active) return@Runnable
            val last = current.lastLocationElapsedRealtimeMillis ?: trackingStartedElapsed
            val age = SystemClock.elapsedRealtime() - last
            if (age < NO_FIX_TIMEOUT_MILLIS) {
                scheduleWatchdog()
            } else if (retryCount < MAX_LOCATION_RETRIES && hasLocationPermission() && isLocationEnabled()) {
                retryCount += 1
                updateStatus("لا يوجد تحديث موقع حديث؛ أحاول إعادة الاتصال (${retryCount}/${MAX_LOCATION_RETRIES})")
                beginLocationUpdates()
            } else {
                stopLocationUpdates()
                updateStatus("لا يوجد موقع صالح حالياً؛ افحص GPS ثم اضغط استئناف التتبّع", TripStatus.LOCATION_UNAVAILABLE)
            }
        }
        watchdog = next
        handler.postDelayed(next, NO_FIX_TIMEOUT_MILLIS)
    }

    private fun handleLocation(location: Location) {
        val current = trip ?: return
        if (!current.active || current.paused || current.alarmTriggered) return
        val fix = LocationFix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            timeMillis = location.time,
            elapsedRealtimeMillis = location.elapsedRealtimeNanos / 1_000_000L
        )
        val evaluation = ArrivalLogic.evaluate(current, fix, System.currentTimeMillis(), SystemClock.elapsedRealtime())
        when (evaluation.status) {
            FixStatus.INVALID_COORDINATES, FixStatus.INVALID_ACCURACY -> updateStatus("بيانات الموقع غير صالحة", TripStatus.LOCATION_UNAVAILABLE)
            FixStatus.STALE -> updateStatus("تحديث الموقع قديم؛ أنتظر تحديثاً جديداً", TripStatus.STALE_FIX)
            FixStatus.INACCURATE -> updateStatus("دقة الموقع ضعيفة؛ المسافة تقريبية وغير مؤكدة", TripStatus.INACCURATE_FIX)
            FixStatus.OUT_OF_ORDER -> Unit // Ignore duplicate or older callback without changing state.
            FixStatus.JUMP -> updateStatus("تجاهلت قفزة مفاجئة في GPS؛ أنتظر قراءة مستقرة", TripStatus.GPS_JUMP)
            FixStatus.TRIP_INACTIVE -> Unit
            FixStatus.ACCEPTED -> {
                val updated = evaluation.nextTrip ?: return
                if (!store.save(updated)) {
                    trip = updated.copy(paused = true, status = TripStatus.SERVICE_ERROR, statusMessage = "تعذّر حفظ حالة التنبيه؛ أوقفت قياسات الموقع بأمان")
                    store.save(trip!!)
                    stopLocationUpdates()
                    updateNotification(pausedNotification())
                    return
                }
                trip = updated
                retryCount = 0
                if (evaluation.finalTriggered) {
                    stopLocationUpdates()
                    startArrivalAlarm(playEffects = true)
                } else if (evaluation.warningTriggered) {
                    updateNotification(warningNotification())
                    if (updated.soundEnabled) playAlarmSound(looping = false)
                    if (updated.vibrationEnabled) vibrateOnce()
                } else {
                    updateNotification(trackingNotification())
                }
                scheduleWatchdog()
            }
        }
    }

    private fun startArrivalAlarm(playEffects: Boolean) {
        val current = trip ?: return
        if (!current.alarmTriggered || current.alarmDismissed || !current.active) return
        if (playEffects && current.soundEnabled) playAlarmSound(looping = true)
        if (playEffects && current.vibrationEnabled) vibrateRepeatedly()
        updateNotification(alarmNotification())
    }

    private fun playAlarmSound(looping: Boolean) {
        stopPlayerOnly()
        val uri = trip?.alarmSoundUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        try {
            val next = MediaPlayer()
            player = next
            next.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            next.setDataSource(this, uri)
            next.isLooping = looping
            next.setOnPreparedListener { if (player === it) it.start() }
            next.setOnCompletionListener { done ->
                if (player === done) {
                    player = null
                    runCatching { done.release() }
                }
            }
            next.setOnErrorListener { failed, _, _ ->
                if (player === failed) {
                    player = null
                    runCatching { failed.release() }
                    updateStatus("تعذّر تشغيل صوت التنبيه؛ استخدم إشعار الوصول أو أوقفه من التطبيق")
                }
                true
            }
            next.prepareAsync()
        } catch (error: Exception) {
            stopPlayerOnly()
            updateStatus("تعذّر تشغيل صوت التنبيه؛ استخدم إشعار الوصول أو أوقفه من التطبيق")
            Log.w(TAG, "Alarm sound failed", error)
        }
    }

    private fun stopPlayerOnly() {
        val old = player ?: return
        player = null
        runCatching { if (old.isPlaying) old.stop() }
        runCatching { old.release() }
    }

    private fun vibrateOnce() {
        getVibrator()?.let { vibrator ->
            if (vibrator.hasVibrator()) {
                alarmVibrator = vibrator
                vibrator.vibrate(VibrationEffect.createOneShot(650, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    private fun vibrateRepeatedly() {
        getVibrator()?.let { vibrator ->
            if (vibrator.hasVibrator()) {
                alarmVibrator = vibrator
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 850, 450, 850), 0))
            }
        }
    }

    private fun getVibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun trackingNotification(): Notification {
        val current = trip
        val text = current?.lastDistanceMeters?.let { "المسافة بخط مستقيم نحو ${formatDistance(it)}" } ?: "بانتظار تحديث موقع صالح"
        val pause = serviceAction(ACTION_PAUSE)
        return baseNotification(text, "التتبّع نشط", "إيقاف مؤقت", pause, WasalApplication.TRACKING_CHANNEL, NotificationCompat.PRIORITY_LOW).build()
    }

    private fun pausedNotification(): Notification = baseNotification(
        "التتبّع متوقف مؤقتاً؛ لن يصدر تنبيه وصول حتى تستأنف", "الرحلة متوقفة مؤقتاً",
        "استئناف", serviceAction(ACTION_RESUME), WasalApplication.TRACKING_CHANNEL, NotificationCompat.PRIORITY_DEFAULT
    ).build()

    private fun warningNotification(): Notification = baseNotification(
        "اقتربت من النطاق المحدد؛ المسافة خط مستقيم", "تنبيه اقتراب",
        "إنهاء الرحلة", serviceAction(ACTION_STOP), WasalApplication.ALARM_CHANNEL, NotificationCompat.PRIORITY_HIGH
    ).setCategory(NotificationCompat.CATEGORY_EVENT).build()

    private fun alarmNotification(): Notification {
        val builder = baseNotification(
            "دخلت نطاق الوجهة المحدد", "تنبيه الوصول",
            "إيقاف التنبيه", serviceAction(ACTION_STOP), WasalApplication.ALARM_CHANNEL, NotificationCompat.PRIORITY_MAX
        ).setCategory(NotificationCompat.CATEGORY_ALARM).setOngoing(true)
        val manager = getSystemService(android.app.NotificationManager::class.java)
        if (Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()) {
            val alarmIntent = PendingIntent.getActivity(
                this, 202, Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setFullScreenIntent(alarmIntent, true)
        }
        return builder.build()
    }

    private fun baseNotification(
        text: String,
        title: String,
        actionText: String,
        actionIntent: PendingIntent,
        channel: String,
        priority: Int
    ): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            this, 200, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setCategory(if (channel == WasalApplication.ALARM_CHANNEL) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_SERVICE)
            .setPriority(priority)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, actionText, actionIntent)
    }

    private fun serviceAction(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(), Intent(this, LocationTrackingService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun updateNotification(notification: Notification) {
        getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun updateStatus(message: String, status: TripStatus? = null) {
        val old = trip ?: return
        val updated = old.copy(statusMessage = message, status = status ?: old.status)
        trip = updated
        if (!store.save(updated)) Log.w(TAG, "Could not persist current trip status")
        if (old.active) {
            updateNotification(when {
                old.alarmTriggered -> alarmNotification()
                old.paused -> pausedNotification()
                else -> trackingNotification()
            })
        }
    }

    private fun pauseTrip() {
        val current = trip ?: restoreTrip() ?: return
        if (!current.active || current.alarmTriggered) return
        val paused = ArrivalLogic.pauseTrip(current).copy(status = TripStatus.PAUSED, statusMessage = "التتبّع متوقف مؤقتاً؛ لن يصدر تنبيه وصول حتى تستأنف")
        if (!store.save(paused)) {
            updateStatus("تعذّر حفظ حالة الإيقاف؛ أعد المحاولة قبل إغلاق التطبيق", TripStatus.SERVICE_ERROR)
            return
        }
        trip = paused
        stopLocationUpdates()
        updateNotification(pausedNotification())
    }

    private fun finishTrip(outcome: TripOutcome? = null) {
        stopLocationUpdates()
        stopAlarmPlayback()
        val finished = trip ?: store.load()
        if (finished != null) {
            val result = outcome ?: if (finished.alarmTriggered) TripOutcome.ARRIVED else TripOutcome.CANCELLED
            val record = TripHistoryRecord(
                tripId = finished.id,
                destinationName = finished.destinationName.ifBlank { "${String.format(Locale.US, "%.5f", finished.destination.latitude)}, ${String.format(Locale.US, "%.5f", finished.destination.longitude)}" },
                destination = finished.destination,
                radiusMeters = finished.radiusMeters,
                startedAtMillis = finished.startedAtMillis,
                endedAtMillis = System.currentTimeMillis().coerceAtLeast(finished.startedAtMillis),
                outcome = result,
                warningTriggered = finished.warningTriggered,
                alarmTriggered = finished.alarmTriggered,
                earlyWarningEnabled = finished.earlyWarningEnabled,
                earlyWarningDistanceMeters = finished.earlyWarningDistanceMeters,
                soundEnabled = finished.soundEnabled,
                vibrationEnabled = finished.vibrationEnabled
            )
            val historyWritten = localStore.update(
                transform = { data -> LocalAppDataLogic.recordHistory(data, record) },
                failIfCorrupt = true
            )
            if (!historyWritten) {
                Log.w(TAG, "Trip history could not be committed; preserving active trip for retry")
                trip = finished.copy(paused = true, status = TripStatus.SERVICE_ERROR, statusMessage = "تعذّر حفظ سجل الرحلة؛ أُبقيت الرحلة لإعادة المحاولة")
                store.save(trip!!)
                updateNotification(pausedNotification())
                return
            }
        }
        getSystemService(android.app.NotificationManager::class.java).cancel(NOTIFICATION_ID)
        store.clear()
        trip = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopLocationUpdates() {
        watchdog?.let(handler::removeCallbacks)
        watchdog = null
        callback?.let { runCatching { fused.removeLocationUpdates(it) } }
        callback = null
    }

    private fun stopAlarmPlayback() {
        stopPlayerOnly()
        runCatching { alarmVibrator?.cancel() }
        alarmVibrator = null
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isLocationEnabled(): Boolean = runCatching {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= 28) manager.isLocationEnabled
        else manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)

    private fun formatDistance(meters: Float): String =
        if (meters >= 1_000f) String.format(Locale.getDefault(), "%.1f كم", meters / 1_000f)
        else String.format(Locale.getDefault(), "%.0f م", meters)

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The foreground service may continue after the UI task is swiped away, subject to OS/OEM policy.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopLocationUpdates()
        stopAlarmPlayback()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "WasalLocationService"
        private const val NO_FIX_TIMEOUT_MILLIS = 60_000L
        private const val MAX_LOCATION_RETRIES = 2
        const val ACTION_START = "com.seifsayed0.wasalalarm.action.START"
        const val ACTION_RESUME = "com.seifsayed0.wasalalarm.action.RESUME"
        const val ACTION_PAUSE = "com.seifsayed0.wasalalarm.action.PAUSE"
        const val ACTION_STOP = "com.seifsayed0.wasalalarm.action.STOP"
        const val EXTRA_LATITUDE = "destination_latitude"
        const val EXTRA_LONGITUDE = "destination_longitude"
        const val EXTRA_RADIUS = "radius_meters"
        const val EXTRA_NAME = "destination_name"
        const val NOTIFICATION_ID = 4051
    }
}
