package com.xbertz.onsite

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AddressSuggestion(
    val label: String,
    val latitude: Double,
    val longitude: Double
)

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

// Bounding box of Australia (minLon,minLat,maxLon,maxLat). Without it Photon ranks results
// globally and e.g. "Darlington" resolves to Darlington, England instead of Darlington NSW.
private const val AUSTRALIA_BBOX = "112.9,-43.8,153.7,-10.6"

suspend fun searchAddresses(query: String): List<AddressSuggestion> = withContext(Dispatchers.IO) {
    try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://photon.komoot.io/api/?q=$encodedQuery&limit=5&bbox=$AUSTRALIA_BBOX")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext emptyList()

        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val features = JSONObject(body).optJSONArray("features") ?: return@withContext emptyList()

        (0 until features.length()).mapNotNull { index ->
            val feature = features.optJSONObject(index) ?: return@mapNotNull null
            val geometry = feature.optJSONObject("geometry") ?: return@mapNotNull null
            val coordinates = geometry.optJSONArray("coordinates") ?: return@mapNotNull null
            if (coordinates.length() < 2) return@mapNotNull null

            val longitude = coordinates.optDouble(0)
            val latitude = coordinates.optDouble(1)

            val properties = feature.optJSONObject("properties") ?: JSONObject()
            val streetLine = listOfNotNull(
                properties.optStringOrNull("housenumber"),
                properties.optStringOrNull("street")
            ).joinToString(" ").ifBlank { null }

            // "district" is the suburb (e.g. Darlington) and "city" the metro area (Sydney);
            // state + postcode go on one segment, as addresses are normally written here.
            val stateLine = listOfNotNull(
                properties.optStringOrNull("state"),
                properties.optStringOrNull("postcode")
            ).joinToString(" ").ifBlank { null }

            // Show the street address only; OSM "name" (a building/business) is used just
            // when the hit has no street, e.g. a whole street or a suburb.
            val label = listOfNotNull(
                streetLine ?: properties.optStringOrNull("name"),
                properties.optStringOrNull("district"),
                properties.optStringOrNull("city"),
                stateLine
            ).distinct().joinToString(", ")

            if (label.isBlank()) null else AddressSuggestion(label, latitude, longitude)
        }
    } catch (e: Exception) {
        emptyList()
    }
}
