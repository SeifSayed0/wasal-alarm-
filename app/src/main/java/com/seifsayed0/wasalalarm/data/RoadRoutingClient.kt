package com.seifsayed0.wasalalarm.data

import com.seifsayed0.wasalalarm.domain.ArrivalLogic
import com.seifsayed0.wasalalarm.domain.Coordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Optional online road-distance lookup. Coordinates are sent only when the caller explicitly enables it. */
class RoadRoutingClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    suspend fun routeDistanceMeters(origin: Coordinate, destination: Coordinate): Float? {
        if (!ArrivalLogic.validCoordinate(origin) || !ArrivalLogic.validCoordinate(destination)) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val points = "${format(origin)};${format(destination)}"
                val url = "$BASE_URL/route/v1/driving/$points?overview=false&steps=false&alternatives=false"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", PROJECT_URL)
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string() ?: return@use null
                    val route = JSONObject(body).optJSONArray("routes")?.optJSONObject(0) ?: return@use null
                    val meters = route.optDouble("distance", Double.NaN)
                    meters.takeIf { it.isFinite() && it >= 0.0 && it <= MAX_ROUTE_DISTANCE_METERS }?.toFloat()
                }
            }.getOrNull()
        }
    }

    private fun format(point: Coordinate): String =
        String.format(Locale.US, "%.5f,%.5f", point.longitude, point.latitude)

    private companion object {
        const val BASE_URL = "https://routing.openstreetmap.de/routed-car"
        const val PROJECT_URL = "https://github.com/SeifSayed0/wasal-alarm-"
        const val USER_AGENT = "Wasal-Android/1.2.0 (+$PROJECT_URL)"
        const val MAX_ROUTE_DISTANCE_METERS = 10_000_000.0
    }
}
