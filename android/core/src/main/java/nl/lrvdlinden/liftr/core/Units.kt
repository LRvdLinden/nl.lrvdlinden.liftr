package nl.lrvdlinden.liftr.core

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

enum class WeightUnit(val raw: String) {
    KG("kg"), LB("lb");

    val symbol: String get() = raw

    /** Smallest plate step used by the +/− buttons. */
    val step: Double get() = if (this == KG) 2.5 else 5.0

    fun fromKg(kg: Double): Double = if (this == KG) kg else kg * 2.2046226
    fun toKg(value: Double): Double = if (this == KG) value else value / 2.2046226

    /** "42,5" (number only, in this unit). */
    fun number(kg: Double): String = LiftrFormat.decimal(fromKg(kg))

    /** "42,5 kg" */
    fun format(kg: Double): String = "${number(kg)} $symbol"

    /** Volume in tonnes / kilos: "12,4 t" or "860 kg". */
    fun volume(kg: Double): String {
        val value = fromKg(kg)
        if (this == KG && value >= 10_000) return "${LiftrFormat.decimal(value / 1000)} t"
        if (this == LB && value >= 10_000) return "${LiftrFormat.decimal(value / 1000)}k lb"
        return "${LiftrFormat.decimal(Math.round(value).toDouble())} $symbol"
    }

    companion object {
        fun from(raw: String?): WeightUnit = if (raw == "lb") LB else KG
    }
}

object LiftrFormat {
    val zone: ZoneId get() = ZoneId.systemDefault()

    fun decimal(value: Double, maxFraction: Int = 1): String {
        val f = NumberFormat.getNumberInstance(Locale.getDefault())
        f.minimumFractionDigits = 0
        f.maximumFractionDigits = maxFraction
        val v = if (abs(value) < 1e-9) 0.0 else value
        return f.format(v)
    }

    fun integer(value: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

    fun duration(seconds: Long): String {
        val total = max(0, seconds)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    /** "1 u 12 min" / "1 h 12 min" */
    fun longDuration(millis: Long): String {
        val total = max(0, millis) / 60_000
        val h = total / 60
        val m = total % 60
        return if (h > 0) "$h ${tr("h", "u")} $m min" else "$m min"
    }

    fun zoned(millis: Long): ZonedDateTime = Instant.ofEpochMilli(millis).atZone(zone)
    fun localDate(millis: Long): LocalDate = zoned(millis).toLocalDate()

    private fun pattern(p: String): DateTimeFormatter = DateTimeFormatter.ofPattern(p, L10n.locale)

    /** "woensdag 4 november" */
    fun day(millis: Long): String = pattern("EEEE d MMMM").format(zoned(millis)).replaceFirstChar { it.titlecase(L10n.locale) }

    /** "4 nov" */
    fun shortDay(millis: Long): String = pattern("d MMM").format(zoned(millis)).trimEnd('.')

    /** "4 nov 2026" */
    fun mediumDate(millis: Long): String = pattern("d MMM yyyy").format(zoned(millis))

    fun time(millis: Long): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(L10n.locale).format(zoned(millis))

    fun dateTime(millis: Long): String = "${mediumDate(millis)} ${time(millis)}"

    /** "november 2026" */
    fun monthYear(date: LocalDate): String = pattern("LLLL yyyy").format(date).replaceFirstChar { it.titlecase(L10n.locale) }

    /** Parses "42,5" as well as "42.5". */
    fun parse(text: String): Double? {
        val cleaned = text.trim().replace(",", ".")
        if (cleaned.isEmpty()) return null
        return cleaned.toDoubleOrNull()
    }

    fun round2(value: Double): Double = (value * 100).roundToLong() / 100.0
}
