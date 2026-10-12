package com.seifsayed0.wasalalarm.data

import com.seifsayed0.wasalalarm.domain.*
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

object LocalAppDataCodec {
    const val SCHEMA_VERSION = 2
    const val MAX_PLACES = 100
    const val MAX_HISTORY = 200
    const val MAX_ENCODED_CHARS = 1_000_000

    fun encode(data: LocalAppData): String {
        require(valid(data)) { "Invalid local application data" }
        val p = Properties()
        p.setProperty("version", SCHEMA_VERSION.toString())
        val s = data.settings
        p.setProperty("settings.radius", s.defaultRadiusMeters.toString())
        p.setProperty("settings.warningEnabled", s.earlyWarningEnabled.toString())
        p.setProperty("settings.warningDistance", s.earlyWarningDistanceMeters.toString())
        p.setProperty("settings.sound", s.soundEnabled.toString())
        p.setProperty("settings.vibration", s.vibrationEnabled.toString())
        p.setProperty("settings.language", s.language.name)
        p.setProperty("settings.theme", s.theme.name)
        p.setProperty("settings.historyEnabled", s.historyEnabled.toString())
        p.setProperty("settings.historyLimit", s.historyLimit.toString())
        p.setProperty("settings.roadDistanceEnabled", s.roadDistanceEnabled.toString())
        s.alarmSoundUri?.let { p.setProperty("settings.alarmSoundUri", it) }
        p.setProperty("places.count", data.places.size.toString())
        data.places.forEachIndexed { index, place ->
            val prefix = "place.$index."
            p.setProperty(prefix + "id", place.id)
            p.setProperty(prefix + "name", place.name.trim())
            p.setProperty(prefix + "lat", place.coordinate.latitude.toString())
            p.setProperty(prefix + "lon", place.coordinate.longitude.toString())
            p.setProperty(prefix + "category", place.category.name)
            p.setProperty(prefix + "radius", place.radiusMeters.toString())
            p.setProperty(prefix + "created", place.createdAtMillis.toString())
            p.setProperty(prefix + "updated", place.updatedAtMillis.toString())
        }
        p.setProperty("history.count", data.history.size.toString())
        data.history.forEachIndexed { index, record ->
            val prefix = "history.$index."
            p.setProperty(prefix + "id", record.id)
            p.setProperty(prefix + "tripId", record.tripId)
            p.setProperty(prefix + "name", record.destinationName.take(80))
            p.setProperty(prefix + "lat", record.destination.latitude.toString())
            p.setProperty(prefix + "lon", record.destination.longitude.toString())
            p.setProperty(prefix + "radius", record.radiusMeters.toString())
            p.setProperty(prefix + "started", record.startedAtMillis.toString())
            p.setProperty(prefix + "ended", record.endedAtMillis.toString())
            p.setProperty(prefix + "outcome", record.outcome.name)
            p.setProperty(prefix + "warning", record.warningTriggered.toString())
            p.setProperty(prefix + "alarm", record.alarmTriggered.toString())
            p.setProperty(prefix + "earlyWarningEnabled", record.earlyWarningEnabled.toString())
            p.setProperty(prefix + "earlyWarningDistance", record.earlyWarningDistanceMeters.toString())
            p.setProperty(prefix + "soundEnabled", record.soundEnabled.toString())
            p.setProperty(prefix + "vibrationEnabled", record.vibrationEnabled.toString())
        }
        return StringWriter().also { p.store(it, null) }.toString().also {
            require(it.length <= MAX_ENCODED_CHARS) { "Local data exceeds storage limit" }
        }
    }

    fun decode(raw: String): LocalAppData? = try {
        if (raw.length > MAX_ENCODED_CHARS) return null
        val p = Properties().apply { load(StringReader(raw)) }
        val version = p.getProperty("version")?.toIntOrNull() ?: return null
        if (version !in 1..SCHEMA_VERSION) return null
        val settings = AppSettings(
            defaultRadiusMeters = p.int("settings.radius") ?: return null,
            earlyWarningEnabled = p.bool("settings.warningEnabled") ?: return null,
            earlyWarningDistanceMeters = p.int("settings.warningDistance") ?: return null,
            soundEnabled = p.bool("settings.sound") ?: return null,
            vibrationEnabled = p.bool("settings.vibration") ?: return null,
            language = p.enum("settings.language") ?: return null,
            theme = p.enum("settings.theme") ?: return null,
            historyEnabled = p.bool("settings.historyEnabled") ?: return null,
            historyLimit = p.int("settings.historyLimit") ?: return null,
            alarmSoundUri = p.getProperty("settings.alarmSoundUri"),
            roadDistanceEnabled = if (version >= 2) p.bool("settings.roadDistanceEnabled") ?: return null else false
        )
        val placeCount = p.int("places.count") ?: return null
        if (placeCount !in 0..MAX_PLACES) return null
        val places = (0 until placeCount).map { index ->
            val prefix = "place.$index."
            SavedPlace(
                id = p.getProperty(prefix + "id")?.takeIf { it.isNotBlank() && it.length <= 100 } ?: return null,
                name = p.getProperty(prefix + "name")?.trim()?.takeIf(ArrivalLogic::validPlaceName) ?: return null,
                coordinate = Coordinate(p.double(prefix + "lat") ?: return null, p.double(prefix + "lon") ?: return null),
                category = p.enum(prefix + "category") ?: return null,
                radiusMeters = p.int(prefix + "radius") ?: return null,
                createdAtMillis = p.long(prefix + "created") ?: return null,
                updatedAtMillis = p.long(prefix + "updated") ?: return null
            )
        }
        val historyCount = p.int("history.count") ?: return null
        if (historyCount !in 0..MAX_HISTORY) return null
        val history = (0 until historyCount).map { index ->
            val prefix = "history.$index."
            TripHistoryRecord(
                id = p.getProperty(prefix + "id")?.takeIf { it.isNotBlank() && it.length <= 100 } ?: return null,
                tripId = p.getProperty(prefix + "tripId")?.takeIf { it.isNotBlank() && it.length <= 100 } ?: return null,
                destinationName = p.getProperty(prefix + "name")?.takeIf { it.length <= 80 } ?: return null,
                destination = Coordinate(p.double(prefix + "lat") ?: return null, p.double(prefix + "lon") ?: return null),
                radiusMeters = p.int(prefix + "radius") ?: return null,
                startedAtMillis = p.long(prefix + "started") ?: return null,
                endedAtMillis = p.long(prefix + "ended") ?: return null,
                outcome = p.enum(prefix + "outcome") ?: return null,
                warningTriggered = p.bool(prefix + "warning") ?: return null,
                alarmTriggered = p.bool(prefix + "alarm") ?: return null,
                earlyWarningEnabled = p.bool(prefix + "earlyWarningEnabled") ?: false,
                earlyWarningDistanceMeters = p.int(prefix + "earlyWarningDistance") ?: 1_000,
                soundEnabled = p.bool(prefix + "soundEnabled") ?: true,
                vibrationEnabled = p.bool(prefix + "vibrationEnabled") ?: true
            )
        }
        LocalAppData(settings, places, history).takeIf(::valid)
    } catch (_: Exception) {
        null
    }

    fun valid(data: LocalAppData): Boolean {
        val s = data.settings
        if (!ArrivalLogic.validRadius(s.defaultRadiusMeters) ||
            !ArrivalLogic.validWarningDistance(s.earlyWarningDistanceMeters) ||
            (s.alarmSoundUri?.length ?: 0) > 2_048 ||
            s.historyLimit !in 1..MAX_HISTORY || data.places.size > MAX_PLACES || data.history.size > MAX_HISTORY) return false
        if (data.places.any { !ArrivalLogic.validPlaceName(it.name) || it.id.isBlank() || it.id.length > 100 ||
                !ArrivalLogic.validCoordinate(it.coordinate) || !ArrivalLogic.validRadius(it.radiusMeters) ||
                it.createdAtMillis < 0 || it.updatedAtMillis < it.createdAtMillis }) return false
        if (data.places.map { it.id }.toSet().size != data.places.size) return false
        if (data.history.any { it.id.isBlank() || it.tripId.isBlank() || it.destinationName.length > 80 ||
                !ArrivalLogic.validCoordinate(it.destination) || !ArrivalLogic.validRadius(it.radiusMeters) ||
                it.startedAtMillis < 0 || it.endedAtMillis < it.startedAtMillis ||
                !ArrivalLogic.validWarningDistance(it.earlyWarningDistanceMeters) }) return false
        if (data.history.map { it.id }.toSet().size != data.history.size ||
            data.history.map { it.tripId }.toSet().size != data.history.size) return false
        return true
    }

    private inline fun <reified T : Enum<T>> Properties.enum(key: String): T? =
        getProperty(key)?.let { raw -> enumValues<T>().firstOrNull { it.name == raw } }

    private fun Properties.int(key: String): Int? = getProperty(key)?.toIntOrNull()
    private fun Properties.long(key: String): Long? = getProperty(key)?.toLongOrNull()
    private fun Properties.double(key: String): Double? = getProperty(key)?.toDoubleOrNull()?.takeIf(Double::isFinite)
    private fun Properties.bool(key: String): Boolean? = getProperty(key)?.toBooleanStrictOrNull()
}
