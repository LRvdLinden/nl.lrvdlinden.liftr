package nl.lrvdlinden.liftr.core

import android.content.Context
import org.json.JSONObject
import java.text.DateFormatSymbols
import java.util.Locale

/**
 * Liftr's localization, ported from Shared/L10n.swift: English (keys), Dutch (written inline next to the
 * English) and every other European language from the `liftr-<code>.json` tables in assets/localization
 * (copied at build time from Shared/Localization). Anything not translated falls back to English.
 */
object L10n {
    /** European languages Liftr ships, besides English and Dutch. */
    val tableLanguages = listOf(
        "bg", "ca", "cs", "da", "de", "el", "es", "et", "fi", "fr", "ga", "hr", "hu", "is", "it", "lt", "lv",
        "mk", "mt", "nb", "pl", "pt-PT", "ro", "ru", "sk", "sl", "sq", "sr", "sv", "tr", "uk",
    )

    @Volatile var language: String = normalize(Locale.getDefault().toLanguageTag())
        private set

    @Volatile private var table: Map<String, String> = emptyMap()

    /** Reads the table for the device language. Call once from Application.onCreate (cheap to repeat). */
    fun init(context: Context) {
        val tag = context.resources.configuration.locales.get(0)?.toLanguageTag() ?: Locale.getDefault().toLanguageTag()
        val lang = normalize(tag)
        if (lang == language && (table.isNotEmpty() || lang == "en" || lang == "nl")) return
        language = lang
        table = if (lang == "en" || lang == "nl") emptyMap() else loadTable(context, lang)
    }

    private fun loadTable(context: Context, lang: String): Map<String, String> = try {
        val text = context.assets.open("localization/liftr-$lang.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = JSONObject(text)
        val map = HashMap<String, String>(json.length() * 2)
        val keys = json.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = json.optString(k)
        }
        map
    } catch (e: Exception) {
        emptyMap()
    }

    fun normalize(code: String): String {
        val lower = code.lowercase(Locale.ROOT)
        if (lower.startsWith("nl")) return "nl"
        if (lower.startsWith("pt")) return "pt-PT"
        if (lower.startsWith("nb") || lower.startsWith("no") || lower.startsWith("nn")) return "nb"
        if (lower.startsWith("sr")) return "sr"
        val base = lower.takeWhile { it != '-' && it != '_' }
        return if (tableLanguages.contains(base)) base else "en"
    }

    val isDutch: Boolean get() = language == "nl"

    val locale: Locale
        get() = when (language) {
            "en" -> Locale.UK
            "nl" -> Locale("nl", "NL")
            else -> Locale.forLanguageTag(language)
        }

    fun lookup(en: String, nl: String): String = when (language) {
        "en" -> en
        "nl" -> nl
        else -> table[en] ?: en
    }

    /** Name of a built-in exercise in the current language. */
    fun exerciseName(id: String, en: String, nl: String): String = when (language) {
        "en" -> en
        "nl" -> nl
        else -> table["exercise.$id"] ?: en
    }

    /** Weekday names Monday first, in the app language ("M", "D", …) or short ("ma", "di", …). */
    fun weekdaySymbols(veryShort: Boolean): List<String> {
        val symbols = DateFormatSymbols.getInstance(locale)
        val raw = symbols.shortWeekdays // index 1 = Sunday … 7 = Saturday
        val ordered = listOf(2, 3, 4, 5, 6, 7, 1).map { raw.getOrNull(it).orEmpty().trimEnd('.') }
        if (ordered.any { it.isEmpty() }) return listOf("M", "T", "W", "T", "F", "S", "S")
        return if (veryShort) ordered.map { it.take(1).uppercase(locale) } else ordered
    }
}

/** The text in the app language. Pass the English and the Dutch text; other languages come from the tables. */
fun tr(en: String, nl: String): String = L10n.lookup(en, nl)

/** Same as [tr], with values for the placeholders {1}, {2}, … (each language may put them in its own order). */
fun trf(en: String, nl: String, vararg args: Any): String {
    var text = L10n.lookup(en, nl)
    args.forEachIndexed { index, arg -> text = text.replace("{${index + 1}}", arg.toString()) }
    return text
}
