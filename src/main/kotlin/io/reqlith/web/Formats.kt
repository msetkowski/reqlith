package io.reqlith.web

import io.reqlith.config.ReqlithProperties
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Date formatting for templates: `${@formats.dateTime(project.updatedAt)}`. */
@Component("formats")
class Formats(private val clock: Clock, private val properties: ReqlithProperties) {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val dateTime = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH).withZone(zone)
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH).withZone(zone)
    private val fullDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(zone)

    /** Absolute URL for links people paste elsewhere (JIRA, ADO). */
    fun absolute(path: String): String = properties.baseUrl.trimEnd('/') + path

    fun dateTime(instant: Instant?): String = instant?.let(dateTime::format).orEmpty()

    /** "3 Nov" within the current year, "3 Nov 2025" otherwise. */
    fun date(instant: Instant?): String {
        if (instant == null) return ""
        val sameYear = instant.atZone(zone).year == Instant.now(clock).atZone(zone).year
        return (if (sameYear) shortDate else fullDate).format(instant)
    }

    /** "just now", "5 min ago", "3 h ago", otherwise the date. */
    fun relative(instant: Instant?): String {
        if (instant == null) return ""
        val minutes = ChronoUnit.MINUTES.between(instant, Instant.now(clock))
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 -> "${minutes / 60} h ago"
            else -> date(instant)
        }
    }
}
