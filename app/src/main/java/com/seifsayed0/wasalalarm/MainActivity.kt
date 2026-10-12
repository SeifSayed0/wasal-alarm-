package com.seifsayed0.wasalalarm

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.seifsayed0.wasalalarm.data.LocalDataRead
import com.seifsayed0.wasalalarm.data.LocalAppDataLogic
import com.seifsayed0.wasalalarm.data.LocalDataStore
import com.seifsayed0.wasalalarm.data.RoadRoutingClient
import com.seifsayed0.wasalalarm.data.TripStore
import com.seifsayed0.wasalalarm.data.TripRead
import com.seifsayed0.wasalalarm.domain.*
import com.seifsayed0.wasalalarm.service.LocationTrackingService
import com.seifsayed0.wasalalarm.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

private val WasalTeal = Color(0xFF0D766E)
private val WasalInk = Color(0xFF18302D)
private val WasalCanvas = Color(0xFFF5F7F4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cache = Cache(File(cacheDir, "osm-map-http"), 50L * 1024 * 1024)
        HttpRequestUtil.setOkHttpClient(
            OkHttpClient.Builder().cache(cache).addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .header("User-Agent", "Wasal/1.2.0 (Android; https://github.com/SeifSayed0/wasal-alarm-)")
                    .build())
            }.build()
        )
        setContent { WasalApp() }
    }
}

private data class PendingTrip(val coordinate: Coordinate, val name: String, val radius: Int)

@SuppressLint("InlinedApi")
@Composable
private fun WasalApp() {
    val context = LocalContext.current
    val tripStore = remember { TripStore(context) }
    val localStore = remember { LocalDataStore(context) }
    val initialRead = remember { localStore.read() }
    var localRead by remember { mutableStateOf(initialRead) }
    var appData by remember {
        mutableStateOf((initialRead as? LocalDataRead.Loaded)?.data ?: LocalAppData())
    }
    val initialTripRead = remember { tripStore.read() }
    var trip by remember { mutableStateOf((initialTripRead as? TripRead.Loaded)?.trip?.takeIf { it.active }) }
    var tripCorrupt by remember { mutableStateOf(initialTripRead is TripRead.Corrupt) }
    var route by remember { mutableStateOf(trip?.let { if (it.alarmTriggered) "alarm" else "trip" } ?: "home") }
    var settingsReturnRoute by remember { mutableStateOf("home") }
    var selectedDestination by remember { mutableStateOf<Coordinate?>(null) }
    var selectedName by remember { mutableStateOf("") }
    var selectedRadius by remember { mutableIntStateOf(appData.settings.defaultRadiusMeters) }
    var statusNote by remember { mutableStateOf<String?>(null) }
    var waitingForService by remember { mutableStateOf(false) }
    var permissionDialog by remember { mutableStateOf("") }
    var notificationDeclined by remember { mutableStateOf(false) }
    var gpsDialog by remember { mutableStateOf(false) }
    var pendingTrip by remember { mutableStateOf<PendingTrip?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<GeocodeResult>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var pendingSaveCoordinate by remember { mutableStateOf<Coordinate?>(null) }
    var pendingSaveName by remember { mutableStateOf("") }
    var savingPlaceFromMap by remember { mutableStateOf(false) }
    var placeSearchQuery by remember { mutableStateOf("") }
    var placeSearchResults by remember { mutableStateOf<List<GeocodeResult>>(emptyList()) }
    var placeSearchError by remember { mutableStateOf<String?>(null) }
    var placeSearching by remember { mutableStateOf(false) }
    var placeSearchRequest by remember { mutableIntStateOf(0) }
    var mapReturnRoute by remember { mutableStateOf("destination") }
    var pendingDuplicate by remember { mutableStateOf<SavedPlace?>(null) }
    var internetAvailable by remember { mutableStateOf(isInternetAvailable(context)) }
    val roadRoutingClient = remember { RoadRoutingClient() }
    val scope = rememberCoroutineScope()
    val language = appData.settings.language
    val dataCorrupt = localRead == LocalDataRead.Corrupt

    fun searchSavedPlaces() {
        val query = placeSearchQuery.trim()
        val request = ++placeSearchRequest
        placeSearchResults = emptyList()
        placeSearchError = null
        if (query.isBlank()) {
            placeSearchError = tx(language, "اكتب اسم المكان أو عنوانه أولاً.", "Enter a place name or address first.")
            return
        }
        placeSearching = true
        scope.launch {
            try {
                val results = geocode(context, query)
                if (request == placeSearchRequest) {
                    placeSearchResults = results
                    if (results.isEmpty()) placeSearchError = tx(language, "لم يعثر Geocoder المتاح على نتيجة. جرّب اسماً آخر أو أضف المكان من الخريطة.", "The available Geocoder found no result. Try another name or add the place from the map.")
                }
            } catch (_: Exception) {
                if (request == placeSearchRequest) placeSearchError = tx(language, "تعذّر البحث. قد تكون خدمة Geocoder أو الإنترنت غير متاحة؛ يمكنك إضافة المكان من الخريطة.", "Search failed. Geocoder or internet may be unavailable; you can add the place from the map.")
            } finally {
                if (request == placeSearchRequest) placeSearching = false
            }
        }
    }

    fun saveData(transform: (LocalAppData) -> LocalAppData): Boolean {
        if (!localStore.update(transform)) {
            statusNote = tx(language, "تعذّر حفظ البيانات محلياً. أعد المحاولة وتأكد من مساحة الجهاز.", "Could not save local data. Try again and check device storage.")
            return false
        }
        localRead = localStore.read()
        appData = (localRead as? LocalDataRead.Loaded)?.data ?: LocalAppData()
        statusNote = null
        return true
    }

    fun refreshLocalData() {
        localRead = localStore.read()
        appData = (localRead as? LocalDataRead.Loaded)?.data ?: LocalAppData()
    }

    fun startNow(request: PendingTrip) {
        if (trip?.active == true) {
            statusNote = tx(language, "هناك رحلة نشطة بالفعل. أنهِها أو افتح حالتها أولاً.", "A trip is already active. Open it or end it first.")
            route = "trip"
            return
        }
        if (!isLocationEnabled(context)) { gpsDialog = true; return }
        if (!hasLocationPermission(context)) { pendingTrip = request; permissionDialog = "location"; return }
        if (Build.VERSION.SDK_INT >= 33 && !notificationDeclined && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingTrip = request
            permissionDialog = "notifications"
            return
        }
        val playServices = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        if (!playServices) {
            statusNote = tx(language, "خدمات Google Play للموقع غير متاحة على هذا الجهاز. لا يمكن بدء التتبّع الآن.", "Google Play location services are unavailable on this device. Monitoring cannot start now.")
            return
        }
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
            putExtra(LocationTrackingService.EXTRA_LATITUDE, request.coordinate.latitude)
            putExtra(LocationTrackingService.EXTRA_LONGITUDE, request.coordinate.longitude)
            putExtra(LocationTrackingService.EXTRA_RADIUS, request.radius)
            putExtra(LocationTrackingService.EXTRA_NAME, request.name.take(80))
        }
        try {
            ContextCompat.startForegroundService(context, intent)
            waitingForService = true
            route = "starting"
            statusNote = if (Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                tx(language, "قد لا يظهر إشعار التتبّع لأن الإشعارات معطّلة.", "The tracking notification may be hidden because notifications are disabled.")
            } else null
        } catch (error: SecurityException) {
            waitingForService = false
            route = "home"
            statusNote = tx(language, "منع Android بدء الخدمة. افتح التطبيق وراجع الأذونات ثم أعد المحاولة.", "Android blocked the service start. Review permissions in the app and try again.")
        } catch (error: RuntimeException) {
            waitingForService = false
            route = "home"
            statusNote = tx(language, "لم يبدأ التتبّع. افتح التطبيق وجرّب البدء مرة أخرى.", "Monitoring did not start. Keep the app open and try again.")
        }
    }

    fun requestStart(request: PendingTrip) {
        pendingTrip = request
        if (!isLocationEnabled(context)) { gpsDialog = true; return }
        if (!hasLocationPermission(context)) { permissionDialog = "location"; return }
        startNow(request)
    }

    fun persistPlace(place: SavedPlace): Boolean {
        var accepted = false
        val saved = saveData { current ->
            val existing = current.places.any { it.id == place.id }
            val next = if (existing) LocalAppDataLogic.updatePlace(current, place)
                else if (current.places.size >= 100) null
                else LocalAppDataLogic.addPlace(current, place, keepPotentialDuplicate = true)
            if (next == null) current else { accepted = true; next }
        }
        if (!accepted) statusNote = tx(language, "لم يُحفظ المكان: تحقق من الاسم والنطاق والحد الأقصى البالغ 100 مكان.", "Place not saved: check its name/radius and the 100-place limit.")
        return saved && accepted
    }

    fun savePlace(place: SavedPlace): Boolean {
        val existing = appData.places.any { it.id == place.id }
        if (!existing) {
            val duplicate = LocalAppDataLogic.findPotentialDuplicate(appData, place)
            if (duplicate != null) {
                pendingDuplicate = place
                return true // Keep both only after the explicit confirmation below.
            }
        }
        return persistPlace(place)
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val granted = results.values.any { it } || hasLocationPermission(context)
        val request = pendingTrip
        if (!granted) {
            statusNote = tx(language, "يلزم إذن الموقع لبدء التتبّع. يمكنك تغيير الإذن من إعدادات التطبيق.", "Location permission is required to monitor a trip. You can change it in App settings.")
            pendingTrip = null
        } else if (request != null) startNow(request)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationDeclined = !granted
        val request = pendingTrip
        if (!granted) statusNote = tx(language, "الإشعارات مرفوضة؛ قد لا يظهر إشعار الخدمة في درج الإشعارات.", "Notifications were denied; the ongoing service notification may not appear in the drawer.")
        if (request != null) startNow(request)
    }
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        @Suppress("DEPRECATION")
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            saveData { current -> current.copy(settings = current.settings.copy(alarmSoundUri = uri.toString())) }
        }
    }

    LaunchedEffect(waitingForService) {
        if (waitingForService) {
            delay(5_000)
            if (waitingForService && route == "starting" && trip == null) {
                waitingForService = false
                route = "home"
                statusNote = tx(language, "لم تؤكد خدمة Android بدء الرحلة خلال المهلة. تحقّق من الأذونات وخدمات الموقع ثم أعد المحاولة.", "Android did not confirm the trip service in time. Check permissions and location services, then retry.")
            }
        }
    }

    LaunchedEffect(Unit) {
        var previousTripId = trip?.id
        while (true) {
            val tripRead = tripStore.read()
            if (tripRead is TripRead.Corrupt) tripCorrupt = true
            val latest = (tripRead as? TripRead.Loaded)?.trip?.takeIf { it.active }
            trip = latest
            if (latest != null && route == "starting") {
                waitingForService = false
                route = if (latest.alarmTriggered) "alarm" else "trip"
            }
            if (latest?.alarmTriggered == true && route != "alarm") route = "alarm"
            if (latest == null && route in setOf("trip", "alarm")) route = "home"
            if (latest == null && previousTripId != null) refreshLocalData()
            previousTripId = latest?.id
            internetAvailable = isInternetAvailable(context)
            delay(1_500)
        }
    }

    BackHandler(enabled = route != "home") {
        val previous = route
        if (previous == "starting") waitingForService = false
        route = when (route) {
            "map" -> mapReturnRoute
            "settings" -> settingsReturnRoute
            "destination", "saved", "history", "trip" -> "home"
            "alarm" -> "trip"
            else -> "home"
        }
        if (previous == "map" && savingPlaceFromMap) savingPlaceFromMap = false
    }

    val dark = when (appData.settings.theme) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFF70D4C7), onPrimary = Color(0xFF003731), primaryContainer = Color(0xFF164D46),
        secondary = Color(0xFFB5CDC7), background = Color(0xFF101917), surface = Color(0xFF182320),
        onSurface = Color(0xFFE1EFEB), surfaceVariant = Color(0xFF283632), secondaryContainer = Color(0xFF283D38),
        tertiaryContainer = Color(0xFF4A3923), error = Color(0xFFFFB4AB), errorContainer = Color(0xFF5D1717)
    ) else lightColorScheme(
        primary = WasalTeal, onPrimary = Color.White, primaryContainer = Color(0xFFD9EFEB), onPrimaryContainer = Color(0xFF093D39),
        secondary = Color(0xFF4A6862), background = WasalCanvas, surface = Color.White, onSurface = WasalInk,
        surfaceVariant = Color(0xFFE6ECE9), secondaryContainer = Color(0xFFE3F0EC), tertiaryContainer = Color(0xFFFFF1D8),
        error = Color(0xFFB3261E), errorContainer = Color(0xFFF9DEDC)
    )

    val snackbarHostState = remember { SnackbarHostState() }
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalLayoutDirection provides if (language == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            LaunchedEffect(statusNote, route) {
                val message = statusNote
                if (message != null && route != "home") {
                    snackbarHostState.showSnackbar(message)
                    if (statusNote == message) statusNote = null
                }
            }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
                when (route) {
                    "destination" -> DestinationScreen(
                        language = language, places = appData.places, query = searchQuery, searchResults = searchResults,
                        searching = searching, searchError = searchError, internetAvailable = internetAvailable,
                        onQueryChange = { searchQuery = it; searchError = null },
                        onSearch = {
                            searchResults = emptyList()
                            searchError = null
                            if (searchQuery.isBlank()) searchError = tx(language, "اكتب اسم مكان أولاً.", "Enter a place name first.")
                            else scope.launch {
                                searching = true
                                try {
                                    val results = geocode(context, searchQuery.trim())
                                    searchResults = results
                                    if (results.isEmpty()) searchError = tx(language, "لم يعثر Geocoder المتاح على نتيجة. جرّب اسماً آخر أو استخدم الدبوس/الإحداثيات.", "The available Geocoder returned no result. Try another name or use a map pin/coordinates.")
                                } catch (_: Exception) {
                                    searchError = tx(language, "تعذّر البحث. قد يكون Geocoder أو الإنترنت غير متاح؛ استخدم الإحداثيات أو مكاناً محفوظاً.", "Search failed. The Geocoder or internet may be unavailable; use coordinates or a saved place.")
                                } finally { searching = false }
                            }
                        },
                        onSelectSearchResult = { result ->
                            selectedDestination = result.coordinate
                            selectedName = result.name.take(80)
                            selectedRadius = appData.settings.defaultRadiusMeters
                            route = "home"
                        },
                        onMap = { mapReturnRoute = "destination"; savingPlaceFromMap = false; route = "map" },
                        onUseSaved = { place ->
                            selectedDestination = place.coordinate; selectedName = place.name; selectedRadius = place.radiusMeters; route = "home"
                        },
                        onBack = { route = "home" },
                        onManualCoordinate = { point, name ->
                            selectedDestination = point
                            selectedName = name.ifBlank { tx(language, "إحداثيات محددة", "Pinned coordinates") }
                            selectedRadius = appData.settings.defaultRadiusMeters
                            route = "home"
                        }
                    )
                    "map" -> MapPickerScreen(
                        initial = selectedDestination,
                        radius = selectedRadius,
                        language = language,
                        onBack = { route = mapReturnRoute; if (savingPlaceFromMap) savingPlaceFromMap = false },
                        onConfirm = { point ->
                            if (savingPlaceFromMap) {
                                pendingSaveCoordinate = point
                                pendingSaveName = ""
                                route = "saved"
                            } else {
                                selectedDestination = point
                                selectedName = tx(language, "موقع على الخريطة", "Map pin")
                                selectedRadius = appData.settings.defaultRadiusMeters
                                route = "home"
                            }
                            savingPlaceFromMap = false
                        }
                    )
                    "saved" -> SavedPlacesScreen(
                        language = language, places = appData.places, pendingCoordinate = pendingSaveCoordinate,
                        pendingName = pendingSaveName, searchQuery = placeSearchQuery,
                        searchResults = placeSearchResults, searchError = placeSearchError, searching = placeSearching,
                        internetAvailable = internetAvailable,
                        onSearchQueryChange = { placeSearchQuery = it; placeSearchError = null },
                        onSearch = ::searchSavedPlaces,
                        onSelectSearchResult = { result ->
                            pendingSaveCoordinate = result.coordinate
                            pendingSaveName = result.name
                            placeSearchResults = emptyList()
                            placeSearchError = null
                        },
                        onDraftConsumed = { pendingSaveCoordinate = null; pendingSaveName = "" },
                        onAddFromMap = {
                            pendingSaveName = ""
                            placeSearchResults = emptyList()
                            mapReturnRoute = "saved"
                            savingPlaceFromMap = true
                            route = "map"
                        },
                        onSave = ::savePlace,
                        onDelete = { place -> saveData { current -> current.copy(places = current.places.filterNot { it.id == place.id }) } },
                        onUse = { place -> selectedDestination = place.coordinate; selectedName = place.name; selectedRadius = place.radiusMeters; route = "home" },
                        onBack = { route = "home" }
                    )
                    "history" -> TripHistoryScreen(
                        language = language, history = appData.history, historyEnabled = appData.settings.historyEnabled,
                        onDelete = { record -> saveData { current -> current.copy(history = LocalAppDataLogic.deleteHistory(current, record.id).history) } },
                        onClear = { saveData { current -> LocalAppDataLogic.clearHistory(current) } }, onBack = { route = "home" }
                    )
                    "settings" -> SettingsScreen(
                        language = language, settings = appData.settings,
                        locationPermission = hasLocationPermission(context), locationReady = isLocationEnabled(context),
                        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                        internetAvailable = internetAvailable, appVersion = BuildConfig.VERSION_NAME,
                        onSettingsChange = { next -> saveData { current -> current.copy(settings = next) } },
                        onPickSound = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, tx(language, "اختيار صوت وصّال", "Choose Wasal alarm sound"))
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                appData.settings.alarmSoundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it.toUri()) }
                            }
                            runCatching { soundPicker.launch(intent) }.onFailure {
                                statusNote = tx(language, "تعذّر فتح أصوات الجهاز؛ سيبقى صوت المنبّه الافتراضي.", "Could not open device sounds; the default alarm sound remains selected.")
                            }
                        },
                        onTestSound = { testSound(context, appData.settings.alarmSoundUri) },
                        onTestVibration = { testVibration(context) },
                        onLocationSettings = { runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } },
                        onAppSettings = { openAppSettings(context) },
                        onBatterySettings = { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }.onFailure { openAppSettings(context) } },
                        onFullScreenSettings = { openFullScreenSettings(context) },
                        onBack = { route = settingsReturnRoute }
                    )
                    "trip" -> TripScreen(
                        language = language, trip = trip,
                        roadDistanceEnabled = appData.settings.roadDistanceEnabled,
                        internetAvailable = internetAvailable,
                        roadRoutingClient = roadRoutingClient,
                        onPauseResume = {
                            val action = if (trip?.paused == true) LocationTrackingService.ACTION_RESUME else LocationTrackingService.ACTION_PAUSE
                            val intent = Intent(context, LocationTrackingService::class.java).setAction(action)
                            runCatching { if (action == LocationTrackingService.ACTION_RESUME) ContextCompat.startForegroundService(context, intent) else context.startService(intent) }
                                .onFailure { statusNote = tx(language, "تعذّر تغيير حالة الرحلة. أعد فتح التطبيق وحاول مرة أخرى.", "Could not change trip state. Reopen the app and try again.") }
                        },
                        onCancel = { runCatching { context.startService(Intent(context, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_STOP)) }; route = "home" },
                        onLocationSettings = { runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } },
                        onOpenSettings = { settingsReturnRoute = "trip"; route = "settings" },
                        onRoutingAttribution = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.openstreetmap.org/fixthemap".toUri())) }
                                .onFailure { statusNote = tx(language, "تعذّر فتح صفحة الإبلاغ عن خريطة OSM.", "Could not open the OSM map-report page.") }
                        },
                        onBack = { route = "home" }
                    )
                    "starting" -> Column(
                        Modifier.fillMaxSize().padding(28.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text(tx(language, "جارٍ بدء خدمة التتبّع…", "Starting trip monitoring…"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp), textAlign = TextAlign.Center)
                        Text(tx(language, "ننتظر تأكيد حفظ الرحلة وبدء الخدمة. أبقِ التطبيق مفتوحاً.", "Waiting for the trip to be saved and the service to start. Keep the app open."), modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
                        TextButton(onClick = {
                            waitingForService = false
                            runCatching { context.startService(Intent(context, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_STOP)) }
                            route = "home"
                        }, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) { Text(tx(language, "إلغاء البدء", "Cancel start")) }
                    }
                    "alarm" -> AlarmScreen(
                        language = language, trip = trip,
                        onStop = { runCatching { context.startService(Intent(context, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_STOP)) }; route = "home" },
                        onBack = { route = "trip" }
                    )
                    else -> HomeScreen(
                        language = language, locationReady = isLocationEnabled(context), internetAvailable = internetAvailable,
                        trip = trip, places = appData.places,
                        selectedDestination = selectedDestination, selectedName = selectedName, selectedRadius = selectedRadius,
                        note = listOfNotNull(
                            statusNote,
                            if (dataCorrupt) tx(language, "تعذّر قراءة بعض بيانات Wasal السابقة؛ احتُفظ بنسخة محلية تالفة قبل أي حفظ جديد.", "Some prior Wasal data could not be read; a local corrupt-data copy was preserved before any new save.") else null,
                            if (tripCorrupt) tx(language, "تعذّر استعادة بيانات الرحلة السابقة؛ احتفظ التطبيق بنسخة محلية محدودة قبل استبدالها.", "The previous trip record could not be restored; a bounded local copy was preserved before replacement.") else null
                        ).joinToString("\n\n").ifBlank { null },
                        onChooseDestination = { route = "destination" },
                        onStartSelected = { selectedDestination?.let { requestStart(PendingTrip(it, selectedName, selectedRadius)) } },
                        onStartSaved = { place -> requestStart(PendingTrip(place.coordinate, place.name, place.radiusMeters)) },
                        onOpenTrip = { route = if (trip?.alarmTriggered == true) "alarm" else "trip" },
                        onOpenSaved = { refreshLocalData(); route = "saved" },
                        onOpenHistory = { refreshLocalData(); route = "history" },
                        onOpenSettings = { refreshLocalData(); settingsReturnRoute = "home"; route = "settings" }
                    )
                }
                SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp))
                }
            }
        }
    }

    if (permissionDialog.isNotEmpty()) {
        val notifications = permissionDialog == "notifications"
        AlertDialog(
            onDismissRequest = { permissionDialog = "" },
            title = { Text(if (notifications) tx(language, "إذن الإشعارات", "Notification permission") else tx(language, "إذن الموقع للرحلة", "Location permission for this trip")) },
            text = { Text(if (notifications)
                tx(language, "يستخدم Wasal إشعاراً مستمراً لحالة التتبّع وإشعاراً لإيقاف التنبيه. يمكن المتابعة دون إذن الإشعارات، لكن قد يخفي Android الإشعار من درج الإشعارات.", "Wasal uses an ongoing tracking notification and an alert notification. You can continue without notification permission, but Android may hide it from the notification drawer.")
            else tx(language, "يحتاج Wasal إلى الموقع أثناء الرحلة التي تبدأها بنفسك فقط، ولا يطلب إذن الموقع في الخلفية. افتراضياً لا تُرسل الإحداثيات إلى خادم خارجي؛ إذا فعّلت مسافة طريق السيارة، يُرسل الموقع الحالي والوجهة لخادم التوجيه، وقد تُسجل الطلبات. لا يُرفع مسار GPS متصل. الموقع التقريبي قد لا يكون دقيقاً بما يكفي.", "Wasal needs location only during a trip you start and does not request background-location permission. Coordinates are not sent to an external server by default; if you enable car road distance, the current point and destination are sent to the routing server and requests may be logged. No continuous GPS trace is uploaded. Approximate-only location may not be accurate enough.")) },
            confirmButton = { TextButton(onClick = {
                permissionDialog = ""
                if (notifications) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }) { Text(tx(language, "متابعة", "Continue")) } },
            dismissButton = { TextButton(onClick = {
                permissionDialog = ""
                if (notifications) { notificationDeclined = true; pendingTrip?.let(::startNow) } else pendingTrip = null
            }) { Text(if (notifications) tx(language, "المتابعة دونها", "Continue without it") else tx(language, "ليس الآن", "Not now")) } }
        )
    }

    if (gpsDialog) AlertDialog(
        onDismissRequest = { gpsDialog = false },
        title = { Text(tx(language, "خدمة الموقع متوقفة", "Location services are off")) },
        text = { Text(tx(language, "فعّل خدمات الموقع قبل بدء التتبّع. لا يلزم اتصال إنترنت مستمر إذا توفرت قراءة GPS صالحة.", "Turn on location services before monitoring. A continuous internet connection is not required if GPS fixes are available.")) },
        confirmButton = { TextButton(onClick = { gpsDialog = false; runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }) { Text(tx(language, "إعدادات الموقع", "Location settings")) } },
        dismissButton = { TextButton(onClick = { gpsDialog = false }) { Text(tx(language, "إلغاء", "Cancel")) } }
    )

    pendingDuplicate?.let { draft ->
        AlertDialog(
            onDismissRequest = { pendingDuplicate = null },
            title = { Text(tx(language, "مكان قريب موجود بالفعل", "A nearby place already exists")) },
            text = { Text(tx(language, "يوجد مكان بالاسم نفسه ضمن 50 متراً. يمكنك إلغاء الحفظ أو الاحتفاظ بهما كمكانين منفصلين.", "A place with the same name is within 50 metres. Cancel or keep both as separate places.")) },
            confirmButton = { TextButton(onClick = { persistPlace(draft); pendingDuplicate = null }) { Text(tx(language, "حفظهما منفصلين", "Keep both")) } },
            dismissButton = { TextButton(onClick = { pendingDuplicate = null }) { Text(tx(language, "إلغاء", "Cancel")) } }
        )
    }
}

private suspend fun geocode(context: Context, query: String): List<GeocodeResult> {
    if (!Geocoder.isPresent()) return emptyList()
    val geocoder = Geocoder(context, Locale.getDefault())
    val addresses: List<Address> = if (Build.VERSION.SDK_INT >= 33) {
        suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
                override fun onGeocode(results: MutableList<Address>) {
                    if (continuation.isActive) continuation.resume(results)
                }
                override fun onError(errorMessage: String?) {
                    if (continuation.isActive) continuation.resume(emptyList())
                }
            })
        }
    } else {
        @Suppress("DEPRECATION")
        withContext(Dispatchers.IO) { geocoder.getFromLocationName(query, 5).orEmpty() }
    }
    return addresses.mapNotNull { address ->
        val point = Coordinate(address.latitude, address.longitude)
        if (!ArrivalLogic.validCoordinate(point)) null else {
            val name = address.getAddressLine(0)?.takeIf { it.isNotBlank() }
                ?: address.featureName?.takeIf { it.isNotBlank() }
                ?: query
            GeocodeResult(name.take(80), point)
        }
    }.distinctBy { "${it.coordinate.latitude}:${it.coordinate.longitude}" }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun isLocationEnabled(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    if (Build.VERSION.SDK_INT >= 28) manager.isLocationEnabled
    else @Suppress("DEPRECATION") (manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
}.getOrDefault(false)

private fun isInternetAvailable(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}.getOrDefault(false)

private fun testSound(context: Context, uriString: String?) {
    val uri = uriString?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ?: return
    runCatching {
        val player = MediaPlayer()
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        player.setDataSource(context, uri)
        player.setOnPreparedListener { it.start() }
        player.setOnCompletionListener { runCatching { it.release() } }
        player.setOnErrorListener { failed, _, _ -> runCatching { failed.release() }; true }
        player.prepareAsync()
    }
}

private fun testVibration(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createOneShot(650, VibrationEffect.DEFAULT_AMPLITUDE))
}

private fun openAppSettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }
}

private fun openFullScreenSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData("package:${context.packageName}".toUri())
    else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
}

@Composable
private fun MapPickerScreen(initial: Coordinate?, radius: Int, language: AppLanguage, onBack: () -> Unit, onConfirm: (Coordinate) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selected by remember { mutableStateOf(initial) }
    var zoom by remember { mutableDoubleStateOf(12.0) }
    var ready by remember { mutableStateOf(false) }
    var destroyed by remember { mutableStateOf(false) }
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    DisposableEffect(mapView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> { if (!destroyed) { mapView.onDestroy(); destroyed = true } }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!destroyed) runCatching { mapView.onPause(); mapView.onStop(); mapView.onDestroy() }
        }
    }
    LaunchedEffect(mapView) {
        mapView.getMapAsync { loaded ->
            loaded.setStyle(Style.Builder().fromJson(OSM_STYLE_JSON)) {
                val target = initial?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(30.0444, 31.2357)
                loaded.cameraPosition = CameraPosition.Builder().target(target).zoom(12.0).build()
                selected = initial ?: Coordinate(target.latitude, target.longitude)
                ready = true
            }
            loaded.addOnCameraIdleListener {
                loaded.cameraPosition.target?.let { target -> selected = Coordinate(target.latitude, target.longitude) }
                zoom = loaded.cameraPosition.zoom
            }
            loaded.addOnMapClickListener { point ->
                loaded.animateCamera(CameraUpdateFactory.newLatLng(point))
                selected = Coordinate(point.latitude, point.longitude)
                true
            }
        }
    }
    val density = LocalDensity.current.density
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = tx(language, "حرّك الخريطة لوضع مركز نطاق الوصول", "Move the map to position the arrival-zone center") }) {
            val latitude = selected?.latitude ?: 30.0444
            val mpp = (2.0 * PI * 6_378_137.0 * cos(Math.toRadians(latitude)) / (512.0 * 2.0.pow(zoom))).coerceAtLeast(0.1)
            val radiusPx = ((radius / mpp) * density).toFloat().coerceIn(18f * density, 140f * density)
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(color = WasalTeal.copy(alpha = 0.20f), radius = radiusPx, center = center)
            drawCircle(color = WasalTeal, radius = radiusPx, center = center, style = Stroke(width = 2f * density))
            drawCircle(color = Color.White, radius = 8f * density, center = center)
            drawCircle(color = WasalTeal, radius = 5f * density, center = center)
        }
        Column(Modifier.align(Alignment.TopCenter).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), shadowElevation = 5.dp, color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tx(language, "رجوع", "Back")) }
                    Column(Modifier.weight(1f)) {
                        Text(tx(language, "اختر الوجهة", "Choose destination"), fontWeight = FontWeight.Bold)
                        Text(tx(language, "حرّك الخريطة واجعل مركز الدائرة على النقطة المطلوبة.", "Move the map so the circle centre is on your destination."), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shadowElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)) {
                    Text(selected?.let { String.format(Locale.US, "%.5f, %.5f", it.latitude, it.longitude) } ?: tx(language, "حرّك الخريطة لتحديد نقطة", "Move map to choose a point"), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Text("© OpenStreetMap contributors · ${tx(language, "النطاق المرسوم تقريبي", "circle is approximate")}", style = MaterialTheme.typography.labelSmall, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.openstreetmap.org/copyright".toUri())) } })
                }
            }
            Button(onClick = { selected?.let(onConfirm) }, enabled = ready && selected != null, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), shape = RoundedCornerShape(18.dp)) {
                if (!ready) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else { Icon(Icons.Default.Map, null); Spacer(Modifier.width(8.dp)); Text(tx(language, "تأكيد الوجهة", "Confirm destination")) }
            }
        }
    }
}

private const val OSM_STYLE_JSON = """
{
  "version": 8,
  "name": "Wasal OSM",
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "layers": [{ "id": "osm-raster", "type": "raster", "source": "osm" }]
}
"""
