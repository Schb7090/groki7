package app.groki.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

object Format {
    private val zone = ZoneId.of("Europe/Budapest")
    private val dayTime = DateTimeFormatter.ofPattern("MMM d. HH:mm", java.util.Locale("hu"))

    /** "5 perce", "3 órája", "tegnap", vagy dátum. */
    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        val d = (now - ms) / 60_000
        return when {
            ms == 0L -> "még soha"
            d < 1 -> "épp most"
            d < 60 -> "$d perce"
            d < 24 * 60 -> "${d / 60} órája"
            d < 48 * 60 -> "tegnap"
            else -> dayTime.format(Instant.ofEpochMilli(ms).atZone(zone))
        }
    }

    /** Határidő visszaszámláló: "még 5 óra", "lejárt 2 napja". */
    fun countdown(dueMs: Long, now: Long = System.currentTimeMillis()): String {
        val diff = dueMs - now
        val h = abs(diff) / 3_600_000
        val text = when {
            h < 1 -> "${abs(diff) / 60_000} perc"
            h < 48 -> "$h óra"
            else -> "${h / 24} nap"
        }
        return if (diff >= 0) "még $text" else "lejárt $text"
    }

    fun date(ms: Long): String = dayTime.format(Instant.ofEpochMilli(ms).atZone(zone))
}
