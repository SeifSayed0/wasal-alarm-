package com.seifsayed0.wasalalarm.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.seifsayed0.wasalalarm.data.RoadRoutingClient
import com.seifsayed0.wasalalarm.domain.*
import kotlinx.coroutines.delay
import java.util.Locale

private val Teal = androidx.compose.ui.graphics.Color(0xFF0D766E)
private val Muted = androidx.compose.ui.graphics.Color(0xFF607370)
private val Amber = androidx.compose.ui.graphics.Color(0xFF9A5B00)
private val Danger = androidx.compose.ui.graphics.Color(0xFF8B2E26)

@Composable
fun HomeScreen(
    language: AppLanguage,
    locationReady: Boolean,
    internetAvailable: Boolean,
    trip: Trip?,
    places: List<SavedPlace>,
    selectedDestination: Coordinate?,
    selectedName: String,
    selectedRadius: Int,
    note: String?,
    onChooseDestination: () -> Unit,
    onStartSelected: () -> Unit,
    onStartSaved: (SavedPlace) -> Unit,
    onOpenTrip: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Teal), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Navigation, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("وصّال · Wasal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(tx(language, "منبّه وجهة يعمل محلياً", "A destination alarm that works locally"), style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = tx(language, "الإعدادات", "Settings"))
                }
            }
        }
        if (trip != null) item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tx(language, "رحلتك الحالية", "Your current trip"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(trip.destinationName.ifBlank { tx(language, "وجهة محددة", "Selected destination") }, style = MaterialTheme.typography.titleMedium)
                    Text(trip.status.label(language), color = Muted)
                    Button(onClick = onOpenTrip, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(if (trip.alarmTriggered) tx(language, "عرض التنبيه", "View arrival alert") else tx(language, "متابعة الرحلة", "Open active trip"))
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tx(language, "إلى أين تذهب؟", "Where are you going?"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(tx(language, "اختر مكاناً محفوظاً أو حدّد وجهة جديدة بالبحث أو الدبوس أو الإحداثيات.", "Choose a saved place or select a new destination by search, map pin, or coordinates."), color = Muted)
                    Button(onClick = onChooseDestination, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.AddLocationAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text(tx(language, "اختيار وجهة", "Choose destination"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (selectedDestination != null) item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tx(language, "الوجهة المحددة", "Selected destination"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(selectedName.ifBlank { tx(language, "وجهة جديدة", "New destination") }, fontWeight = FontWeight.SemiBold)
                    Text(String.format(Locale.US, "%.5f, %.5f", selectedDestination.latitude, selectedDestination.longitude), color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("${tx(language, "نطاق الوصول", "Arrival radius")}: ${formatDistance(selectedRadius.toFloat(), language)}", color = Muted)
                    Button(onClick = onStartSelected, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text(tx(language, "ابدأ الرحلة إلى هذه الوجهة", "Start trip to this destination"), fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            StatusCard(
                title = tx(language, if (locationReady) "خدمة الموقع جاهزة" else "خدمة الموقع متوقفة", if (locationReady) "Location services are ready" else "Location services are off"),
                body = tx(language,
                    if (locationReady) "سيطلب التطبيق إذن الموقع عند بدء الرحلة. يمكن أن يعمل GPS من دون إنترنت." else "فعّل خدمات الموقع قبل بدء الرحلة. GPS والإنترنت حالتان منفصلتان.",
                    if (locationReady) "Location permission is requested when a trip starts. GPS can work without internet." else "Turn on location services before starting. GPS and internet are separate."),
                warning = !locationReady
            )
        }
        item {
            StatusCard(
                title = tx(language, if (internetAvailable) "الاتصال متاح" else "لا يوجد إنترنت حالياً", if (internetAvailable) "Internet is available" else "You appear to be offline"),
                body = tx(language, "البحث النصي والبلاطات الجديدة تحتاج اتصالاً؛ الأماكن المحفوظة والرحلة والتنبيه تعمل محلياً متى توفر GPS.", "Text search and new map tiles need a connection; saved places, trip calculations, and alarms stay local when GPS is available."),
                warning = !internetAvailable
            )
        }
        note?.let { item { StatusCard(tx(language, "ملاحظة", "Notice"), it, warning = true) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onOpenSaved, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Icon(Icons.Default.Home, null); Spacer(Modifier.width(6.dp)); Text(tx(language, "الأماكن", "Places"))
                }
                OutlinedButton(onClick = onOpenHistory, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Icon(Icons.Default.History, null); Spacer(Modifier.width(6.dp)); Text(tx(language, "السجل", "History"))
                }
            }
        }
        item { Text(tx(language, "لا يوجد حساب أو إعلانات أو رفع لمسار الرحلة. المسافة المعروضة خط مستقيم وليست مسافة طريق.", "No account, ads, or trip-track uploads. Displayed distance is straight-line, not road distance."), style = MaterialTheme.typography.bodySmall, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        if (places.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tx(language, "أماكنك السريعة", "Quick destinations"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenSaved) { Text(tx(language, "عرض الكل", "See all")) }
                }
                places.take(3).forEach { place ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(place.name, fontWeight = FontWeight.SemiBold)
                                Text("${place.category.label(language)} · ${formatDistance(place.radiusMeters.toFloat(), language)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = { onStartSaved(place) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(tx(language, "ابدأ", "Start")) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DestinationScreen(
    language: AppLanguage,
    places: List<SavedPlace>,
    query: String,
    searchResults: List<GeocodeResult>,
    searching: Boolean,
    searchError: String?,
    internetAvailable: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectSearchResult: (GeocodeResult) -> Unit,
    onMap: () -> Unit,
    onUseSaved: (SavedPlace) -> Unit,
    onBack: () -> Unit,
    onManualCoordinate: (Coordinate, String) -> Unit
) {
    var showCoordinates by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Header(tx(language, "اختيار الوجهة", "Choose a destination"), language, onBack) }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tx(language, "بحث باسم المكان", "Search by place name"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(tx(language, "اكتب اسم مكان ثم اضغط بحث", "Enter a place, then tap Search")) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                    Button(onClick = onSearch, enabled = query.isNotBlank() && !searching, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                        if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(tx(language, "بحث", "Search"))
                    }
                    Text(tx(language,
                        "يستخدم البحث Geocoder الذي يوفّره الجهاز عند توفره. تُرسل عبارة البحث إلى مزوّد النظام وقد تحتاج إنترنت؛ لا يوجد اقتراح تلقائي.",
                        "Search uses the device's available Geocoder. The query may be shared with the system provider and may need internet; there is no autocomplete."),
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (!internetAvailable) Text(tx(language, "غير متصل: جرّب الأماكن المحفوظة أو أدخل الإحداثيات يدوياً.", "Offline: use a saved place or enter coordinates manually."), color = Amber)
                    searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (searchResults.isNotEmpty()) item { Text(tx(language, "نتائج البحث", "Search results"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(searchResults) { result ->
            Card(Modifier.fillMaxWidth().clickable { onSelectSearchResult(result) }, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = Teal)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(result.name, fontWeight = FontWeight.SemiBold)
                        Text(coordinateText(result.coordinate), color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onSelectSearchResult(result) }) { Text(tx(language, "اختيار", "Select")) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onMap, modifier = Modifier.weight(1f).heightIn(min = 54.dp)) { Icon(Icons.Default.Map, null); Spacer(Modifier.width(6.dp)); Text(tx(language, "الخريطة", "Map")) }
                OutlinedButton(onClick = { showCoordinates = true }, modifier = Modifier.weight(1f).heightIn(min = 54.dp)) { Text(tx(language, "إحداثيات", "Coordinates")) }
            }
        }
        item { Text(tx(language, "اختيار النقطة من الخريطة يحتاج اتصالاً لتحميل البلاطات الجديدة. يمكن إدخال الإحداثيات أو استخدام مكان محفوظ دون تحميل خريطة.", "Selecting on the map needs a connection for new tiles. You can enter coordinates or use a saved place without loading a map."), style = MaterialTheme.typography.bodySmall, color = Muted) }
        if (places.isNotEmpty()) {
            item { Text(tx(language, "الأماكن المحفوظة · تعمل دون إنترنت", "Saved places · available offline"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(places, key = { "destination-${it.id}" }) { place ->
                Card(shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(place.name, fontWeight = FontWeight.SemiBold)
                            Text("${place.category.label(language)} · ${coordinateText(place.coordinate)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onUseSaved(place) }) { Text(tx(language, "اختيار", "Select")) }
                    }
                }
            }
        } else item { EmptyCard(language, tx(language, "لا توجد أماكن محفوظة بعد.", "You have no saved places yet.")) }
    }
    if (showCoordinates) ManualCoordinateDialog(language, onDismiss = { showCoordinates = false }) { point, name ->
        showCoordinates = false
        onManualCoordinate(point, name)
    }
}

@Composable
private fun ManualCoordinateDialog(language: AppLanguage, onDismiss: () -> Unit, onConfirm: (Coordinate, String) -> Unit) {
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tx(language, "إدخال إحداثيات", "Enter coordinates")) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text(tx(language, "اسم اختياري للوجهة", "Optional destination name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(latitude, { latitude = it.filter { c -> c.isDigit() || c in ".-" }.take(16) }, label = { Text(tx(language, "خط العرض −90 إلى 90", "Latitude −90 to 90")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(longitude, { longitude = it.filter { c -> c.isDigit() || c in ".-" }.take(16) }, label = { Text(tx(language, "خط الطول −180 إلى 180", "Longitude −180 to 180")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (invalid) Text(tx(language, "أدخل إحداثيات صحيحة ضمن الحدود الجغرافية.", "Enter valid coordinates within geographic bounds."), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val point = Coordinate(latitude.toDoubleOrNull() ?: Double.NaN, longitude.toDoubleOrNull() ?: Double.NaN)
                if (ArrivalLogic.validCoordinate(point)) onConfirm(point, name.trim()) else invalid = true
            }) { Text(tx(language, "استخدام النقطة", "Use point")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tx(language, "إلغاء", "Cancel")) } }
    )
}

@Composable
fun SavedPlacesScreen(
    language: AppLanguage,
    places: List<SavedPlace>,
    pendingCoordinate: Coordinate?,
    pendingName: String,
    searchQuery: String,
    searchResults: List<GeocodeResult>,
    searchError: String?,
    searching: Boolean,
    internetAvailable: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectSearchResult: (GeocodeResult) -> Unit,
    onDraftConsumed: () -> Unit,
    onAddFromMap: () -> Unit,
    onSave: (SavedPlace) -> Boolean,
    onDelete: (SavedPlace) -> Unit,
    onUse: (SavedPlace) -> Unit,
    onBack: () -> Unit
) {
    var editing by remember { mutableStateOf<SavedPlace?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<SavedPlace?>(null) }
    LaunchedEffect(pendingCoordinate, pendingName) { if (pendingCoordinate != null) creating = true }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Header(tx(language, "الأماكن المحفوظة", "Saved places"), language, onBack, Modifier.weight(1f))
                IconButton(onClick = onAddFromMap, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.Default.AddLocationAlt, tx(language, "إضافة من الخريطة", "Add from map")) }
            }
        }
        item { Text(tx(language, "تُحفظ الأسماء والإحداثيات محلياً على هذا الجهاز، ويمكن اختيارها دون اتصال.", "Names and coordinates are stored locally on this device and can be selected offline."), color = Muted, style = MaterialTheme.typography.bodySmall) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text(tx(language, "ابحث باسم المكان أو عنوانه", "Search by place name or address")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (!searching) onSearch() }),
                    trailingIcon = {
                        IconButton(onClick = onSearch, enabled = !searching) {
                            Icon(Icons.Default.Search, contentDescription = tx(language, "بحث عن مكان", "Search for a place"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    tx(language,
                        if (internetAvailable) "اكتب الاسم أو العنوان، ثم اختر نتيجة لفتح نموذج الحفظ. قد تختلف النتائج حسب خدمة Geocoder المتاحة." else "البحث قد يحتاج إلى الإنترنت وخدمة Geocoder؛ يمكنك بدلاً منه تحديد المكان من الخريطة.",
                        if (internetAvailable) "Type a name or address, then choose a result to open the save form. Results depend on the available Geocoder." else "Search may need internet and a Geocoder; you can select the place on the map instead."),
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                if (searching) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(tx(language, "جارٍ البحث…", "Searching…"), color = Muted)
                }
                searchError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (searchResults.isNotEmpty()) {
            item { Text(tx(language, "نتائج البحث — اختر المكان المطلوب", "Search results — choose the place"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
            items(searchResults, key = { "${it.coordinate.latitude}:${it.coordinate.longitude}" }) { result ->
                Card(
                    Modifier.fillMaxWidth().clickable { onSelectSearchResult(result) },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(result.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(coordinateText(result.coordinate), color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.LocationOn, contentDescription = tx(language, "اختيار هذا المكان", "Select this place"), tint = Teal)
                    }
                }
            }
        }
        if (places.isEmpty()) item { EmptyCard(language, tx(language, "لا توجد أماكن بعد. أضف المنزل أو المحطة لتبدأ رحلة بسرعة.", "No saved places yet. Add home or a station for faster trips.")) }
        items(places, key = { it.id }) { place ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${place.category.label(language)} · ${formatDistance(place.radiusMeters.toFloat(), language)}", color = Muted)
                            Text(coordinateText(place.coordinate), color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { editing = place }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.Default.Edit, tx(language, "تعديل ${place.name}", "Edit ${place.name}")) }
                        IconButton(onClick = { deleteCandidate = place }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.Default.Delete, tx(language, "حذف ${place.name}", "Delete ${place.name}"), tint = Danger) }
                    }
                    Button(onClick = { onUse(place) }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(tx(language, "استخدام هذه الوجهة", "Use this destination")) }
                }
            }
        }
    }
    if (creating && pendingCoordinate != null) PlaceEditorDialog(language, null, pendingCoordinate, pendingName, onDismiss = { creating = false; onDraftConsumed() }) { place ->
        if (onSave(place)) { creating = false; onDraftConsumed(); true } else false
    }
    editing?.let { place -> PlaceEditorDialog(language, place, place.coordinate, place.name, onDismiss = { editing = null }) { updated ->
        if (onSave(updated)) { editing = null; true } else false
    } }
    deleteCandidate?.let { place ->
        ConfirmDialog(language, tx(language, "حذف المكان؟", "Delete saved place?"), tx(language, "سيُحذف «${place.name}» من الجهاز. لا يمكن التراجع.", "“${place.name}” will be deleted from this device. This cannot be undone."), onDismiss = { deleteCandidate = null }) {
            onDelete(place)
            deleteCandidate = null
        }
    }
}

@Composable
private fun PlaceEditorDialog(language: AppLanguage, existing: SavedPlace?, coordinate: Coordinate, initialName: String, onDismiss: () -> Unit, onSave: (SavedPlace) -> Boolean) {
    var name by remember(existing?.id, initialName) { mutableStateOf(initialName.take(80)) }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: PlaceCategory.OTHER) }
    var radius by remember(existing?.id) { mutableStateOf((existing?.radiusMeters ?: 1_000).toString()) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tx(language, if (existing == null) "حفظ مكان" else "تعديل المكان", if (existing == null) "Save place" else "Edit place")) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text(tx(language, "اسم المكان (حتى 80 حرفاً)", "Place name (up to 80 characters)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(tx(language, "الفئة", "Category"), fontWeight = FontWeight.SemiBold)
                FlowRowCompat { PlaceCategory.entries.forEach { value -> FilterChip(category == value, { category = value }, label = { Text(value.label(language)) }) } }
                OutlinedTextField(radius, { radius = it.filter(Char::isDigit).take(5) }, label = { Text(tx(language, "نطاق الوصول بالمتر (100–50,000)", "Arrival radius in metres (100–50,000)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(coordinateText(coordinate), style = MaterialTheme.typography.bodySmall, color = Muted)
                if (error) Text(tx(language, "لم يتم الحفظ. تحقق من الاسم والنطاق أو أعد المحاولة بعد فشل التخزين.", "Not saved. Check the name/radius or retry after a storage failure."), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val radiusValue = radius.toIntOrNull()
                if (!ArrivalLogic.validPlaceName(name) || radiusValue == null || !ArrivalLogic.validRadius(radiusValue)) error = true
                else if (!onSave(SavedPlace(
                        id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(), coordinate = coordinate, category = category, radiusMeters = radiusValue,
                        createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(), updatedAtMillis = System.currentTimeMillis()
                    ))) error = true
            }) { Text(tx(language, "حفظ", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tx(language, "إلغاء", "Cancel")) } }
    )
}

@Composable
fun TripHistoryScreen(language: AppLanguage, history: List<TripHistoryRecord>, historyEnabled: Boolean, onDelete: (TripHistoryRecord) -> Unit, onClear: () -> Unit, onBack: () -> Unit) {
    var selected by remember { mutableStateOf<TripHistoryRecord?>(null) }
    var deleteOne by remember { mutableStateOf<TripHistoryRecord?>(null) }
    var clearAll by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Header(tx(language, "سجل الرحلات", "Trip history"), language, onBack, Modifier.weight(1f))
                if (history.isNotEmpty()) TextButton(onClick = { clearAll = true }) { Text(tx(language, "مسح الكل", "Clear all"), color = Danger) }
            }
        }
        item { Text(tx(language, "السجل محلي ومحدود. لا يُحفظ مسار GPS مستمر ولا تُرفع الرحلات إلى خادم.", "History is local and capped. No continuous GPS trace is stored or uploaded."), color = Muted, style = MaterialTheme.typography.bodySmall) }
        if (!historyEnabled) item { EmptyCard(language, tx(language, "حفظ الرحلات الجديدة متوقف؛ إن وُجدت سجلات سابقة فستظل ظاهرة حتى تحذفها.", "Recording new trips is off; any existing records remain visible until you delete them.")) }
        else if (history.isEmpty()) item { EmptyCard(language, tx(language, "لا توجد رحلات مسجلة بعد. تظهر الرحلة هنا بعد إيقافها أو وصولها.", "No trips recorded yet. Trips appear after arrival or when you end them.")) }
        items(history, key = { it.id }) { record ->
            Card(Modifier.fillMaxWidth().clickable { selected = record }, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(record.destinationName, fontWeight = FontWeight.SemiBold)
                        Text("${formatTimestamp(record.endedAtMillis, language)} · ${record.outcome.label(language)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                        Text("${tx(language, "النطاق", "Radius")}: ${formatDistance(record.radiusMeters.toFloat(), language)}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { deleteOne = record }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.Default.Delete, tx(language, "حذف السجل", "Delete record"), tint = Danger) }
                }
            }
        }
    }
    selected?.let { record ->
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(record.destinationName) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${tx(language, "البداية", "Started")}: ${formatTimestamp(record.startedAtMillis, language)}")
                Text("${tx(language, "النهاية", "Ended")}: ${formatTimestamp(record.endedAtMillis, language)}")
                Text("${tx(language, "النتيجة", "Outcome")}: ${record.outcome.label(language)}")
                Text("${tx(language, "الإحداثيات", "Coordinates")}: ${coordinateText(record.destination)}")
                Text("${tx(language, "نطاق الوصول", "Arrival radius")}: ${formatDistance(record.radiusMeters.toFloat(), language)}")
                Text(tx(language, if (record.earlyWarningEnabled) "التحذير المبكر مفعّل (${formatDistance(record.earlyWarningDistanceMeters.toFloat(), language)} قبل النطاق)." else "التحذير المبكر غير مفعّل.", if (record.earlyWarningEnabled) "Early warning enabled (${formatDistance(record.earlyWarningDistanceMeters.toFloat(), language)} outside the final zone)." else "Early warning was disabled."))
                Text(tx(language, if (record.warningTriggered) "صدر تنبيه الاقتراب." else "لم يصدر تنبيه الاقتراب.", if (record.warningTriggered) "The early warning fired." else "The early warning did not fire."))
                Text("${tx(language, "الصوت", "Sound")}: ${tx(language, if (record.soundEnabled) "مفعّل" else "معطّل", if (record.soundEnabled) "On" else "Off")}")
                Text("${tx(language, "الاهتزاز", "Vibration")}: ${tx(language, if (record.vibrationEnabled) "مفعّل" else "معطّل", if (record.vibrationEnabled) "On" else "Off")}")
            }
        }, confirmButton = { TextButton(onClick = { selected = null }) { Text(tx(language, "إغلاق", "Close")) } })
    }
    deleteOne?.let { record ->
        ConfirmDialog(language, tx(language, "حذف سجل الرحلة؟", "Delete this trip record?"), tx(language, "لا يمكن التراجع عن هذا الحذف.", "This deletion cannot be undone."), onDismiss = { deleteOne = null }) {
            onDelete(record); deleteOne = null
        }
    }
    if (clearAll) ConfirmDialog(language, tx(language, "مسح سجل الرحلات؟", "Clear trip history?"), tx(language, "سيُحذف كل السجل المحلي. لا يمكن التراجع.", "All local history will be deleted. This cannot be undone."), onDismiss = { clearAll = false }) {
        onClear(); clearAll = false
    }
}

@Composable
fun SettingsScreen(
    language: AppLanguage,
    settings: AppSettings,
    locationPermission: Boolean,
    locationReady: Boolean,
    notificationsEnabled: Boolean,
    internetAvailable: Boolean,
    appVersion: String,
    onSettingsChange: (AppSettings) -> Unit,
    onPickSound: () -> Unit,
    onTestSound: () -> Unit,
    onTestVibration: () -> Unit,
    onLocationSettings: () -> Unit,
    onAppSettings: () -> Unit,
    onBatterySettings: () -> Unit,
    onFullScreenSettings: () -> Unit,
    onBack: () -> Unit
) {
    var confirmRoadDistanceEnable by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Header(tx(language, "الإعدادات", "Settings"), language, onBack) }
        item { SectionCard(tx(language, "اللغة والمظهر", "Language and appearance")) {
            Text(tx(language, "لغة التطبيق", "App language"), fontWeight = FontWeight.SemiBold)
            FlowRowCompat {
                AppLanguage.entries.forEach { value -> FilterChip(settings.language == value, { onSettingsChange(settings.copy(language = value)) }, label = { Text(if (value == AppLanguage.ARABIC) "العربية" else "English") }) }
            }
            Text(tx(language, "المظهر", "Theme"), fontWeight = FontWeight.SemiBold)
            FlowRowCompat {
                ThemePreference.entries.forEach { value ->
                    val label = when (value) { ThemePreference.SYSTEM -> tx(language, "النظام", "System"); ThemePreference.LIGHT -> tx(language, "فاتح", "Light"); ThemePreference.DARK -> tx(language, "داكن", "Dark") }
                    FilterChip(settings.theme == value, { onSettingsChange(settings.copy(theme = value)) }, label = { Text(label) })
                }
            }
        } }
        item { SectionCard(tx(language, "مراحل التنبيه", "Alarm stages")) {
            Text(tx(language, "نطاق الوصول النهائي", "Final arrival radius"), fontWeight = FontWeight.SemiBold)
            FlowRowCompat { listOf(500, 1_000, 2_000, 5_000).forEach { radius -> FilterChip(settings.defaultRadiusMeters == radius, { onSettingsChange(settings.copy(defaultRadiusMeters = radius)) }, label = { Text(formatDistance(radius.toFloat(), language)) }) } }
            var customRadius by remember(settings.defaultRadiusMeters) { mutableStateOf(settings.defaultRadiusMeters.toString()) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(customRadius, { customRadius = it.filter(Char::isDigit).take(5) }, label = { Text(tx(language, "نطاق مخصّص بالمتر", "Custom radius (metres)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = { customRadius.toIntOrNull()?.takeIf(ArrivalLogic::validRadius)?.let { onSettingsChange(settings.copy(defaultRadiusMeters = it)) } }) { Text(tx(language, "تطبيق", "Apply")) }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tx(language, "تحذير مبكر", "Early warning"), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(settings.earlyWarningEnabled, { onSettingsChange(settings.copy(earlyWarningEnabled = it)) }, modifier = Modifier.semantics { contentDescription = tx(language, "تفعيل التحذير المبكر", "Enable early warning") })
            }
            Text(tx(language, "المسافة الإضافية خارج نطاق الوصول التي يبدأ عندها التحذير، بشرط وجود حركة اقتراب وقراءتين صالحَتين.", "Extra distance outside the final zone for the early alert; it requires approaching movement and consecutive valid fixes."), style = MaterialTheme.typography.bodySmall, color = Muted)
            FlowRowCompat { listOf(250, 500, 1_000, 2_000, 5_000).forEach { distance -> FilterChip(settings.earlyWarningDistanceMeters == distance, { onSettingsChange(settings.copy(earlyWarningDistanceMeters = distance)) }, enabled = settings.earlyWarningEnabled, label = { Text(formatDistance(distance.toFloat(), language)) }) } }
        } }
        item { SectionCard(tx(language, "الصوت والاهتزاز", "Sound and vibration")) {
            SettingSwitch(language, tx(language, "صوت التنبيه", "Alarm sound"), settings.soundEnabled) { onSettingsChange(settings.copy(soundEnabled = it)) }
            SettingSwitch(language, tx(language, "الاهتزاز", "Vibration"), settings.vibrationEnabled) { onSettingsChange(settings.copy(vibrationEnabled = it)) }
            OutlinedButton(onClick = onPickSound, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(tx(language, "اختيار صوت من أصوات الجهاز", "Choose a sound from this device")) }
            Text(settings.alarmSoundUri?.let { tx(language, "تم اختيار صوت من الجهاز.", "A device sound is selected.") } ?: tx(language, "يُستخدم صوت المنبّه الافتراضي للجهاز.", "The device's default alarm sound is used."), color = Muted, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTestSound, enabled = settings.soundEnabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(tx(language, "اختبار الصوت", "Test sound")) }
                OutlinedButton(onClick = onTestVibration, enabled = settings.vibrationEnabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(tx(language, "اختبار الاهتزاز", "Test vibration")) }
            }
            Text(tx(language, "لا يتجاوز التطبيق مستوى الصوت أو الصامت أو عدم الإزعاج أو إعدادات قناة الإشعار.", "The app cannot override volume, silent mode, Do Not Disturb, or notification-channel settings."), style = MaterialTheme.typography.bodySmall, color = Muted)
        } }
        item { SectionCard(tx(language, "مسافة الطريق للسيارة/التاكسي", "Car/taxi road distance")) {
            SettingSwitch(
                language,
                tx(language, "تفعيل تقدير مسافة الطريق", "Enable estimated road distance"),
                settings.roadDistanceEnabled
            ) { enabled ->
                if (enabled) confirmRoadDistanceEnable = true
                else onSettingsChange(settings.copy(roadDistanceEnabled = false))
            }
            Text(
                tx(language,
                    "اختياري ومتوقف افتراضياً. عند تشغيله يرسل Wasal موقع GPS الحالي والوجهة إلى خادم FOSSGIS العام لحساب مسار سيارة تقريبي. قد يحتفظ الخادم بسجلات الطلبات. لا تُرسل الرحلة كاملة ولا يُحفظ المسار الناتج؛ ويمكن إيقافه هنا في أي وقت.",
                    "Optional and off by default. When enabled, Wasal sends your current GPS location and destination to the public FOSSGIS server for an estimated car route. The server may retain request logs. No complete trip trace is sent or saved, and you can turn this off here at any time."),
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
            Text(tx(language, "المسافة تقديرية ولا تتضمن حركة المرور ولا تغيّر قاعدة إطلاق منبّه الوصول المحلية.", "The estimate excludes live traffic and does not change the local arrival-alarm logic."), style = MaterialTheme.typography.bodySmall, color = Muted)
        } }
        item { SectionCard(tx(language, "السجل والخصوصية", "History and privacy")) {
            SettingSwitch(language, tx(language, "حفظ سجل الرحلات على الجهاز", "Keep trip history on this device"), settings.historyEnabled) { onSettingsChange(settings.copy(historyEnabled = it)) }
            Text("${tx(language, "حد السجل", "History limit")}: ${settings.historyLimit}", fontWeight = FontWeight.SemiBold)
            Slider(value = settings.historyLimit.toFloat(), onValueChange = { onSettingsChange(settings.copy(historyLimit = (it / 10).toInt() * 10)) }, valueRange = 10f..200f, steps = 18, enabled = settings.historyEnabled)
            Text(tx(language, "تُحفظ الوجهة وملخّص الرحلة فقط؛ لا يُحفظ مسار GPS مستمر. تُمسح البيانات عند حذف التطبيق أو بياناته، ما لم تصدّرها بنفسك.", "Only the destination and trip summary are stored; no continuous GPS trace is kept. Data is removed if the app or its data is uninstalled unless you export it yourself."), style = MaterialTheme.typography.bodySmall, color = Muted)
        } }
        item { SectionCard(tx(language, "حالة الجهاز والأذونات", "Device and permissions")) {
            StatusCard(tx(language, if (locationPermission) "إذن الموقع ممنوح" else "إذن الموقع غير ممنوح", if (locationPermission) "Location permission is granted" else "Location permission is not granted"), tx(language, "يُطلب الموقع عند بدء الرحلة فقط. اختيار الموقع التقريبي قد يقلّل الدقة.", "Location is requested only when a trip starts. Approximate-only access may reduce accuracy."), !locationPermission)
            StatusCard(tx(language, if (locationReady) "خدمات الموقع تعمل" else "خدمات الموقع متوقفة", if (locationReady) "Location services are on" else "Location services are off"), tx(language, if (internetAvailable) "GPS لا يحتاج بالضرورة إلى اتصال بيانات." else "GPS can work without a data connection." , if (internetAvailable) "GPS does not necessarily need a data connection." else "GPS can work without a data connection."), !locationReady)
            StatusCard(tx(language, if (notificationsEnabled) "الإشعارات مفعّلة" else "الإشعارات غير مفعّلة", if (notificationsEnabled) "Notifications are enabled" else "Notifications are disabled"), tx(language, "قد لا يظهر إشعار الخدمة أو الوصول إذا عطّلت الإشعارات أو القناة.", "Trip or arrival notifications may be hidden if notifications or their channel are disabled."), !notificationsEnabled)
            OutlinedButton(onClick = onLocationSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(tx(language, "إعدادات الموقع", "Location settings")) }
            OutlinedButton(onClick = onAppSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(tx(language, "إعدادات التطبيق والأذونات", "App settings and permissions")) }
            OutlinedButton(onClick = onBatterySettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(tx(language, "إعدادات توفير البطارية", "Battery settings")) }
            OutlinedButton(onClick = onFullScreenSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(tx(language, "إعدادات إشعار ملء الشاشة", "Full-screen notification settings")) }
            Text(tx(language, "استمرار الخدمة والظهور فوق شاشة القفل best-effort ويتأثر بالنظام والشركة المصنّعة. لا يمكن ضمان العمل بعد فرض الإيقاف أو إعادة التشغيل.", "Service continuity and lock-screen presentation are best-effort and vary by Android and device maker. Force-stop and reboot behavior is not guaranteed."), style = MaterialTheme.typography.bodySmall, color = Muted)
            Text("Wasal $appVersion · ${tx(language, "بدون حساب أو إعلانات أو تحليلات", "No account, ads, or analytics")}", style = MaterialTheme.typography.bodySmall, color = Muted)
        } }
    }
    if (confirmRoadDistanceEnable) {
        AlertDialog(
            onDismissRequest = { confirmRoadDistanceEnable = false },
            title = { Text(tx(language, "إرسال الإحداثيات لحساب الطريق؟", "Send coordinates to calculate the route?")) },
            text = { Text(tx(language,
                "سيُرسل موقعك الحالي والوجهة إلى خادم FOSSGIS/OSRM عبر الإنترنت. قد تُحفظ الطلبات في سجلات الخادم. المسافة تقريبية لمسار قيادة ولا تعني أن التاكسي سيسلكه فعلياً. يظل منبّه الوصول محلياً، وتستطيع إيقاف هذه الميزة في الإعدادات.",
                "Your current location and destination will be sent over the internet to the FOSSGIS/OSRM server. Requests may be kept in server logs. This is an estimated driving route, not a guarantee of the taxi's actual route. The arrival alarm remains local, and you can turn this feature off in settings.")) },
            confirmButton = {
                TextButton(onClick = {
                    onSettingsChange(settings.copy(roadDistanceEnabled = true))
                    confirmRoadDistanceEnable = false
                }) { Text(tx(language, "موافق وتفعيل", "Agree and enable")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRoadDistanceEnable = false }) { Text(tx(language, "إلغاء", "Cancel")) }
            }
        )
    }
}

private enum class RoadDistanceStatus { DISABLED, WAITING, LOADING, READY, OFFLINE, FAILED, PAUSED }

@Composable
fun TripScreen(
    language: AppLanguage,
    trip: Trip?,
    roadDistanceEnabled: Boolean,
    internetAvailable: Boolean,
    roadRoutingClient: RoadRoutingClient,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit,
    onLocationSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onRoutingAttribution: () -> Unit,
    onBack: () -> Unit
) {
    var confirmCancel by remember { mutableStateOf(false) }
    if (trip == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(tx(language, "جارٍ استعادة حالة الرحلة…", "Restoring trip state…"), modifier = Modifier.padding(top = 16.dp))
        }
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentTrip by rememberUpdatedState(trip)
    val currentInternet by rememberUpdatedState(internetAvailable)
    var roadDistanceMeters by remember(trip.id) { mutableStateOf<Float?>(null) }
    var roadDistanceUpdatedAt by remember(trip.id) { mutableLongStateOf(0L) }
    var roadDistanceStatus by remember(trip.id) {
        mutableStateOf(if (roadDistanceEnabled) RoadDistanceStatus.WAITING else RoadDistanceStatus.DISABLED)
    }

    LaunchedEffect(trip.id, roadDistanceEnabled, lifecycleOwner, roadRoutingClient) {
        if (!roadDistanceEnabled) {
            roadDistanceStatus = RoadDistanceStatus.DISABLED
            return@LaunchedEffect
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastRequestElapsedMillis: Long? = null
            var lastOrigin: Coordinate? = null
            while (true) {
                val activeTrip = currentTrip
                val nowMillis = System.currentTimeMillis()
                val origin = activeTrip.lastLatitude?.let { latitude ->
                    activeTrip.lastLongitude?.let { longitude -> Coordinate(latitude, longitude) }
                }
                val fixAge = activeTrip.lastLocationMillis?.let { nowMillis - it }
                val accuracy = activeTrip.lastAccuracyMeters
                val freshFix = origin != null && ArrivalLogic.validCoordinate(origin) &&
                    fixAge != null && fixAge in 0L..120_000L &&
                    accuracy != null && accuracy.isFinite() && accuracy > 0f && accuracy <= 250f

                when {
                    !activeTrip.active -> roadDistanceStatus = RoadDistanceStatus.WAITING
                    activeTrip.paused || activeTrip.alarmTriggered -> roadDistanceStatus = RoadDistanceStatus.PAUSED
                    !freshFix -> roadDistanceStatus = RoadDistanceStatus.WAITING
                    !currentInternet -> roadDistanceStatus = RoadDistanceStatus.OFFLINE
                    RoadRouteRequestPolicy.shouldRequest(
                        SystemClock.elapsedRealtime(), lastRequestElapsedMillis, lastOrigin, origin
                    ) -> {
                        lastRequestElapsedMillis = SystemClock.elapsedRealtime()
                        lastOrigin = origin
                        roadDistanceStatus = RoadDistanceStatus.LOADING
                        val distance = runCatching {
                            roadRoutingClient.routeDistanceMeters(origin, activeTrip.destination)
                        }.getOrNull()
                        if (distance != null) {
                            roadDistanceMeters = distance
                            roadDistanceUpdatedAt = System.currentTimeMillis()
                            roadDistanceStatus = RoadDistanceStatus.READY
                        } else {
                            roadDistanceStatus = RoadDistanceStatus.FAILED
                        }
                    }
                }
                delay(5_000)
            }
        }
    }
    val roadStatusMessage = when (roadDistanceStatus) {
        RoadDistanceStatus.DISABLED -> tx(language, "مسافة الطريق متوقفة.", "Road distance is off.")
        RoadDistanceStatus.WAITING -> tx(language, "بانتظار موقع GPS صالح وحديث.", "Waiting for a fresh, valid GPS fix.")
        RoadDistanceStatus.LOADING -> tx(language, "جارٍ حساب أو تحديث مسافة الطريق…", "Calculating or updating road distance…")
        RoadDistanceStatus.READY -> tx(language, "المسار المقترح لا يتضمن حركة المرور وقد يختلف عن الطريق الفعلي للتاكسي.", "The suggested route excludes traffic and may differ from the taxi's actual route.")
        RoadDistanceStatus.OFFLINE -> tx(language, "لا يوجد اتصال؛ لا يمكن تحديث مسافة الطريق.", "Offline; road distance cannot be refreshed.")
        RoadDistanceStatus.FAILED -> tx(language, "تعذّر جلب مسافة الطريق؛ قد يظهر آخر تقدير مع وقت حسابه.", "Could not fetch road distance; the last estimate and its time may still be shown.")
        RoadDistanceStatus.PAUSED -> tx(language, "لن يُطلب مسار أثناء إيقاف الرحلة أو بعد الوصول.", "No route is requested while the trip is paused or after arrival.")
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Header(tx(language, "الرحلة النشطة", "Active trip"), language, onBack) }
        item { StatusCard(trip.status.label(language), if (language == AppLanguage.ARABIC) trip.statusMessage else statusExplanation(trip.status, language), trip.status !in setOf(TripStatus.TRACKING, TripStatus.APPROACHING, TripStatus.WAITING_FOR_FIX, TripStatus.ARRIVED, TripStatus.PAUSED)) }
        item {
            SectionCard(tx(language, "الوجهة", "Destination")) {
                Text(trip.destinationName.ifBlank { tx(language, "وجهة محددة", "Selected destination") }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(coordinateText(trip.destination), color = Muted)
                HorizontalDivider()
                Text("${tx(language, "نطاق الوصول", "Arrival radius")}: ${formatDistance(trip.radiusMeters.toFloat(), language)}", fontWeight = FontWeight.SemiBold)
                if (roadDistanceEnabled) {
                    Text(
                        "${tx(language, "مسافة الطريق التقديرية (سيارة/تاكسي)", "Estimated road distance (car/taxi)")}: ${roadDistanceMeters?.let { formatDistance(it, language) } ?: tx(language, "غير متاحة حالياً", "Currently unavailable")}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (roadDistanceUpdatedAt > 0L) Text(
                        "${tx(language, "آخر تقدير للطريق", "Last route estimate")}: ${formatTimestamp(roadDistanceUpdatedAt, language)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted
                    )
                    Text(roadStatusMessage, style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text(
                        "${tx(language, "المسافة المستقيمة المستخدمة لمنبّه الوصول", "Straight-line distance used by the arrival alarm")}: ${trip.lastDistanceMeters?.let { formatDistance(it, language) } ?: tx(language, "بانتظار موقع صالح", "Waiting for a valid fix")}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(tx(language, "يُرسل موقع GPS الحالي والوجهة إلى خادم FOSSGIS عند طلب تحديث المسار. لا تُرفع الرحلة كاملة.", "The current GPS point and destination are sent to FOSSGIS for route updates. The complete trip is not uploaded."), style = MaterialTheme.typography.bodySmall, color = Muted)
                    TextButton(onClick = onRoutingAttribution, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(tx(language, "© مساهمو OpenStreetMap · الإبلاغ عن خطأ بالخريطة", "© OpenStreetMap contributors · Report a map issue"))
                    }
                } else {
                    Text("${tx(language, "المسافة المستقيمة التقريبية", "Approximate straight-line distance")}: ${trip.lastDistanceMeters?.let { formatDistance(it, language) } ?: tx(language, "بانتظار تثبيت موقع صالح", "Waiting for a valid location fix")}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(tx(language, "هذه المسافة فقط هي التي يستخدمها منبّه النطاق. يمكنك تفعيل تقدير طريق السيارة/التاكسي من الإعدادات؛ وهو يتطلب الإنترنت وإرسال الإحداثيات إلى خادم خارجي.", "This is the distance used by the geofence alarm. You can enable a car/taxi road estimate in settings; it requires internet and sends coordinates to an external server."), style = MaterialTheme.typography.bodySmall, color = Muted)
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(tx(language, "إعداد مسافة الطريق", "Set up road distance"))
                    }
                }
                HorizontalDivider()
                Text("${tx(language, "آخر موقع صالح", "Last valid location")}: ${trip.lastLatitude?.let { "$it, ${trip.lastLongitude}" } ?: tx(language, "غير متاح", "Unavailable")}", style = MaterialTheme.typography.bodySmall)
                Text("${tx(language, "دقة القياس", "Fix accuracy")}: ${trip.lastAccuracyMeters?.let { "±${formatDistance(it, language)}" } ?: tx(language, "غير متاحة", "Unavailable")}", style = MaterialTheme.typography.bodySmall)
                Text("${tx(language, "آخر تحديث", "Last update")}: ${trip.lastLocationMillis?.let { formatTimestamp(it, language) } ?: tx(language, "لا يوجد تحديث صالح بعد", "No valid fix yet")}", style = MaterialTheme.typography.bodySmall)
                Text("${tx(language, "بدأت", "Started")}: ${formatTimestamp(trip.startedAtMillis, language)}", style = MaterialTheme.typography.bodySmall)
                if (trip.earlyWarningEnabled) Text(tx(language, "التحذير المبكر: ", "Early warning: ") + formatDistance(trip.earlyWarningDistanceMeters.toFloat(), language) + tx(language, " خارج النطاق النهائي", " outside the final zone"), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (trip.status == TripStatus.GPS_DISABLED || trip.status == TripStatus.PERMISSION_REQUIRED) item {
            OutlinedButton(onClick = onLocationSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(tx(language, "فتح إعدادات الموقع", "Open location settings")) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPauseResume, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(tx(language, if (trip.paused) "استئناف" else "إيقاف مؤقت", if (trip.paused) "Resume" else "Pause")) }
                Button(onClick = { confirmCancel = true }, modifier = Modifier.weight(1f).heightIn(min = 56.dp), colors = ButtonDefaults.buttonColors(containerColor = Danger)) { Text(tx(language, "إنهاء الرحلة", "End trip")) }
            }
        }
        item { Text(tx(language, "التتبّع يعتمد على إذن الموقع وخدمات Google Play وإعدادات الطاقة. لا يمكن ضمان الخدمة بعد فرض الإيقاف أو إعادة تشغيل الجهاز.", "Monitoring depends on location permission, Google Play services, and power settings. Force-stop and reboot behavior cannot be guaranteed."), style = MaterialTheme.typography.bodySmall, color = Muted, textAlign = TextAlign.Center) }
    }
    if (confirmCancel) ConfirmDialog(language, tx(language, "إنهاء الرحلة؟", "End this trip?"), tx(language, "سيتوقف تتبّع الموقع وسيُسجّل سبب الإنهاء محلياً إذا كان السجل مفعّلاً.", "Location tracking will stop. The trip outcome will be saved locally if history is enabled."), onDismiss = { confirmCancel = false }) { onCancel(); confirmCancel = false }
}

@Composable
fun AlarmScreen(language: AppLanguage, trip: Trip?, onStop: () -> Unit, onBack: () -> Unit) {
    var confirmStop by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(104.dp).clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(60.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(tx(language, "وصلت إلى نطاق الوجهة", "Destination zone reached"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(trip?.destinationName?.ifBlank { null } ?: tx(language, "وجهتك المحددة", "Your selected destination"), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(if (trip != null) "${tx(language, "نطاق التنبيه", "Arrival zone")}: ${formatDistance(trip.radiusMeters.toFloat(), language)}" else "", textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(tx(language, "الدخول إلى نطاق جغرافي بخط مستقيم؛ لا يعني الوصول إلى المحطة أو مسافة الطريق. تحقّق بصرياً من المكان.", "Your GPS entered a straight-line zone; this does not confirm a road or station arrival. Verify your surroundings."), textAlign = TextAlign.Center, color = Muted)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { confirmStop = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Danger)) {
            Text(tx(language, "إيقاف التنبيه وإنهاء الرحلة", "Stop alert and end trip"), fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(tx(language, "العودة إلى حالة الرحلة", "Back to trip status")) }
    }
    if (confirmStop) ConfirmDialog(language, tx(language, "إيقاف التنبيه؟", "Stop the alert?"), tx(language, "لن يعاود الصوت أو الاهتزاز بعد الإيقاف. سيتم إنهاء الرحلة.", "Sound and vibration will not restart after dismissal. The trip will end."), onDismiss = { confirmStop = false }) { onStop(); confirmStop = false }
}

@Composable
private fun StatusCard(title: String, body: String, warning: Boolean) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (warning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            if (warning) Icon(Icons.Default.Warning, null, tint = Amber, modifier = Modifier.padding(top = 2.dp))
            else Box(Modifier.padding(top = 7.dp).size(9.dp).clip(CircleShape).background(Teal))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            content()
        })
    }
}

@Composable
private fun SettingSwitch(language: AppLanguage, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
private fun Header(title: String, language: AppLanguage, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tx(language, "رجوع", "Back")) }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyCard(language: AppLanguage, message: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Text(message, Modifier.fillMaxWidth().padding(20.dp), color = Muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ConfirmDialog(language: AppLanguage, title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(tx(language, "تأكيد", "Confirm"), color = Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tx(language, "إلغاء", "Cancel")) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowCompat(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

private fun coordinateText(point: Coordinate): String = String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude)
