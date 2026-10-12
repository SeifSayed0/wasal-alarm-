package com.seifsayed0.wasalalarm.ui

import com.seifsayed0.wasalalarm.domain.AppLanguage
import com.seifsayed0.wasalalarm.domain.PlaceCategory
import com.seifsayed0.wasalalarm.domain.TripOutcome
import com.seifsayed0.wasalalarm.domain.TripStatus
import com.seifsayed0.wasalalarm.domain.Coordinate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GeocodeResult(val name: String, val coordinate: Coordinate)

fun tx(language: AppLanguage, arabic: String, english: String): String =
    if (language == AppLanguage.ARABIC) arabic else english

fun TripStatus.label(language: AppLanguage): String = when (this) {
    TripStatus.WAITING_FOR_FIX -> tx(language, "بانتظار موقع صالح", "Waiting for a valid location")
    TripStatus.TRACKING -> tx(language, "التتبّع نشط", "Trip monitoring is active")
    TripStatus.APPROACHING -> tx(language, "اقتربت من نطاق الوجهة", "Approaching the destination zone")
    TripStatus.ARRIVED -> tx(language, "وصلت إلى نطاق الوجهة", "You entered the destination zone")
    TripStatus.PAUSED -> tx(language, "الرحلة متوقفة مؤقتاً", "Trip is paused")
    TripStatus.GPS_DISABLED -> tx(language, "خدمة الموقع متوقفة", "Location services are off")
    TripStatus.PERMISSION_REQUIRED -> tx(language, "إذن الموقع مطلوب", "Location permission is required")
    TripStatus.LOCATION_UNAVAILABLE -> tx(language, "لا يوجد موقع صالح حالياً", "No valid location is available")
    TripStatus.WEAK_SIGNAL -> tx(language, "إشارة الموقع ضعيفة", "Location signal is weak")
    TripStatus.STALE_FIX -> tx(language, "تحديث الموقع قديم", "Location update is stale")
    TripStatus.INACCURATE_FIX -> tx(language, "دقة الموقع غير كافية", "Location accuracy is insufficient")
    TripStatus.GPS_JUMP -> tx(language, "تم تجاهل قفزة GPS", "A GPS jump was ignored")
    TripStatus.SERVICE_ERROR -> tx(language, "تعذّر تشغيل خدمة التتبّع", "Trip monitoring could not start")
}

fun statusExplanation(status: TripStatus, language: AppLanguage): String = when (status) {
    TripStatus.WAITING_FOR_FIX -> tx(language, "بانتظار قراءة GPS صالحة.", "Waiting for a valid GPS fix.")
    TripStatus.TRACKING -> tx(language, "يُحسب القرب محلياً بخط مستقيم.", "Distance is calculated locally in a straight line.")
    TripStatus.APPROACHING -> tx(language, "تم رصد اقتراب بقراءتين صالحَتين.", "Approach was confirmed by consecutive valid fixes.")
    TripStatus.ARRIVED -> tx(language, "دخل هامش دقة القياس ضمن نطاق الوجهة.", "The fix's uncertainty circle is within the destination zone.")
    TripStatus.PAUSED -> tx(language, "لا تُطلب تحديثات موقع أثناء الإيقاف المؤقت.", "No location updates are requested while the trip is paused.")
    TripStatus.GPS_DISABLED -> tx(language, "فعّل خدمات الموقع ثم استأنف الرحلة.", "Turn on location services, then resume the trip.")
    TripStatus.PERMISSION_REQUIRED -> tx(language, "راجع إذن الموقع في إعدادات التطبيق.", "Review location permission in App settings.")
    TripStatus.LOCATION_UNAVAILABLE -> tx(language, "لا يوجد مزوّد موقع صالح حالياً.", "No location provider is available right now.")
    TripStatus.WEAK_SIGNAL -> tx(language, "أنتظر قراءة أدق؛ لا أطلق التنبيه من قراءة غير موثوقة.", "Waiting for a more accurate fix; an uncertain fix will not trigger an alert.")
    TripStatus.STALE_FIX -> tx(language, "تم تجاهل قراءة أقدم من الحد المسموح.", "An older-than-allowed location fix was ignored.")
    TripStatus.INACCURATE_FIX -> tx(language, "تم تجاهل القراءة لأن هامش الخطأ كبير.", "The fix was ignored because its accuracy margin is too large.")
    TripStatus.GPS_JUMP -> tx(language, "تم تجاهل تغير غير معقول في الموقع.", "An implausible location jump was ignored.")
    TripStatus.SERVICE_ERROR -> tx(language, "قد تحتاج الخدمة إلى إذن أو إعادة بدء يدوية.", "The service may need permission or a manual restart.")
}

fun PlaceCategory.label(language: AppLanguage): String = when (this) {
    PlaceCategory.HOME -> tx(language, "المنزل", "Home")
    PlaceCategory.UNIVERSITY -> tx(language, "الجامعة", "University")
    PlaceCategory.WORK -> tx(language, "العمل", "Work")
    PlaceCategory.STATION -> tx(language, "المحطة", "Station")
    PlaceCategory.OTHER -> tx(language, "أخرى", "Other")
}

fun TripOutcome.label(language: AppLanguage): String = when (this) {
    TripOutcome.ARRIVED -> tx(language, "وصل", "Arrived")
    TripOutcome.CANCELLED -> tx(language, "أُلغي", "Cancelled")
    TripOutcome.INTERRUPTED -> tx(language, "توقّف بسبب مشكلة", "Interrupted")
}

fun formatDistance(meters: Float, language: AppLanguage): String = if (meters >= 1_000f) {
    String.format(Locale.US, "%.1f %s", meters / 1_000f, tx(language, "كم", "km"))
} else String.format(Locale.US, "%.0f %s", meters, tx(language, "م", "m"))

fun formatTimestamp(millis: Long, language: AppLanguage): String {
    val locale = if (language == AppLanguage.ARABIC) Locale.forLanguageTag("ar") else Locale.ENGLISH
    return SimpleDateFormat("EEE, d MMM yyyy · HH:mm", locale).format(Date(millis))
}
