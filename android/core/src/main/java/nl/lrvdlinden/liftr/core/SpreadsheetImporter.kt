package nl.lrvdlinden.liftr.core

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Turns a spreadsheet (.xlsx, .csv) of past workouts into Liftr workouts (port of SpreadsheetImporter.swift).
 * Layouts: one row per training day with a column per exercise, or one row per set (like Liftr's CSV export).
 * Dates without a year get their year from the order of the rows.
 */
object SpreadsheetImporter {
    typealias Day = NotesImporter.ParsedDay
    typealias Line = NotesImporter.ParsedLine
    typealias PSet = NotesImporter.ParsedSet

    data class Options(val defaultSets: Int = 3, val defaultReps: Int = 10)
    data class Table(val name: String, val rows: List<List<String>>)
    enum class Layout { PerDate, PerSet }
    data class Result(val days: List<Day>, val layout: Layout, val exerciseColumns: List<String>, val skipped: List<String>)

    val unreadableMessage: String
        get() = tr("This file can't be read. Use an Excel file (.xlsx) or a CSV file.",
            "Dit bestand kan niet gelezen worden. Gebruik een Excel-bestand (.xlsx) of een CSV-bestand.")

    class LoadException : Exception()

    // MARK: Loading

    fun load(bytes: ByteArray, fileName: String): List<Table> {
        if (bytes.size > 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            val tables = try {
                XlsxReader.sheets(bytes).map { Table(it.name, it.rows) }.filter { it.rows.isNotEmpty() }
            } catch (e: Exception) {
                throw LoadException()
            }
            if (tables.isEmpty()) throw LoadException()
            return tables
        }
        val text = String(bytes, Charsets.UTF_8).let { if (it.contains('�')) String(bytes, Charsets.ISO_8859_1) else it }
        val rows = csvRows(text)
        if (rows.isEmpty()) throw LoadException()
        return listOf(Table(fileName.substringBeforeLast('.'), rows))
    }

    /** CSV with ";", "," or tab as separator (whichever the header uses most) and "quoted, fields". */
    fun csvRows(input: String): List<List<String>> {
        val clean = input.replace("﻿", "")
        val firstLine = clean.takeWhile { it != '\n' && it != '\r' }
        val separator = listOf(';', '\t', ',').maxByOrNull { c -> firstLine.count { it == c } } ?: ';'
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        fun endRow() {
            row.add(field.toString()); field.setLength(0)
            if (row.any { it.isNotBlank() }) rows += row
            row = mutableListOf()
        }
        while (i < clean.length) {
            val ch = clean[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < clean.length && clean[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(ch)
                i++
                continue
            }
            when (ch) {
                '"' -> quoted = true
                separator -> { row.add(field.toString()); field.setLength(0) }
                '\r' -> { endRow(); if (i + 1 < clean.length && clean[i + 1] == '\n') i++ }
                '\n' -> endRow()
                else -> field.append(ch)
            }
            i++
        }
        row.add(field.toString())
        if (row.any { it.isNotBlank() }) rows += row
        return rows.map { r -> r.map { it.trim() } }
    }

    // MARK: Parsing

    fun parse(table: Table, options: Options, unit: WeightUnit, today: LocalDateTime = LocalDateTime.now()): Result {
        val rows = table.rows
        val headerIndex = rows.take(10).indexOfFirst { r -> r.count { it.isNotEmpty() } >= 2 }
        if (headerIndex < 0) return Result(emptyList(), Layout.PerDate, emptyList(), emptyList())
        val header = rows[headerIndex].map { it.trim() }
        val body = rows.drop(headerIndex + 1)
        val keys = header.map(::key)

        val dateColumn = keys.indexOfFirst { it in setOf("date", "datum", "dag", "day") }.takeIf { it >= 0 }
            ?: bestDateColumn(body, header.size)
            ?: return Result(emptyList(), Layout.PerDate, emptyList(), emptyList())

        val exerciseColumn = keys.indexOfFirst { it in setOf("exercise", "oefening", "naam", "name", "apparaat", "machine") }
        if (exerciseColumn >= 0) return parsePerSet(keys, body, dateColumn, exerciseColumn, headerIndex, options, unit, today)
        return parsePerDate(header, body, dateColumn, headerIndex, options, unit, today)
    }

    /** Lower case letters and digits only: "Weight (kg)" → "weightkg". */
    private fun key(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase().filter { it.isLetterOrDigit() }

    private val metaColumns = setOf(
        "periode", "period", "week", "notitie", "notities", "notes", "note", "opmerking", "opmerkingen",
        "comment", "comments", "fase", "phase", "blok", "block", "workout", "training", "titel", "title", "tijd", "time",
    )

    private fun bestDateColumn(body: List<List<String>>, columns: Int): Int? {
        val scores = (0 until columns).map { c -> body.take(50).count { row -> c < row.size && dayMonth(row[c]) != null } }
        val best = scores.indices.maxByOrNull { scores[it] } ?: return null
        return if (scores[best] > 0) best else null
    }

    private fun columnName(index: Int): String {
        var n = index + 1
        var name = ""
        while (n > 0) {
            val r = (n - 1) % 26
            name = "${'A' + r}$name"
            n = (n - 1) / 26
        }
        return name
    }

    // MARK: Per date

    private fun parsePerDate(header: List<String>, body: List<List<String>>, dateColumn: Int, headerRow: Int,
                             options: Options, unit: WeightUnit, today: LocalDateTime): Result {
        val meta = header.indices.filter { it != dateColumn && key(header[it]) in metaColumns }
        val exerciseColumns = header.indices.filter { c ->
            c != dateColumn && c !in meta && header[c].isNotEmpty() &&
                body.any { row -> c < row.size && row[c].any { it.isDigit() } }
        }
        val raw = mutableListOf<Pair<DayMonth, Int>>()
        body.forEachIndexed { i, row ->
            if (dateColumn < row.size) dayMonth(row[dateColumn])?.let { raw += it to i }
        }
        val dates = resolveYears(raw.map { it.first }, today)

        val days = mutableListOf<Day>()
        val skipped = mutableListOf<String>()
        raw.forEachIndexed { n, (_, rowIndex) ->
            val row = body[rowIndex]
            val lines = mutableListOf<Line>()
            for (c in exerciseColumns) {
                if (c >= row.size || row[c].isEmpty()) continue
                val parsed = parseCell(row[c], options, unit)
                if (parsed != null) {
                    lines += Line(name = header[c], sets = parsed.first, original = "${header[c]}: ${row[c]}", note = parsed.second)
                } else {
                    skipped += "${columnName(c)}${headerRow + rowIndex + 2}: ${row[c]}"
                }
            }
            if (lines.isEmpty()) return@forEachIndexed
            val note = meta.mapNotNull { c -> if (c < row.size && row[c].isNotEmpty()) row[c] else null }.joinToString(" · ")
            days += Day(date = dates[n], lines = lines, note = note)
        }
        return Result(days, Layout.PerDate, exerciseColumns.map { header[it] }, skipped)
    }

    /** One cell: `45`, `27.3`, `27 (rechte 3×10)`, `18 (4×5)`, `25 / 18`, `36/41/45/52`, `23 x 2`, `36 (op)`. */
    fun parseCell(text: String, options: Options, unit: WeightUnit): Pair<List<PSet>, String>? {
        var s = text.lowercase().replace("×", "x").replace("*", "x")
        val note = mutableListOf<String>()
        var setsCount: Int? = null
        var reps: Int? = null

        for (m in NotesImporter.allMatches("""\(([^)]*)\)""", s)) {
            val inner = m[0]
            var words = inner
            NotesImporter.allMatches("""(\d+)\s*x\s*(\d+)""", inner).firstOrNull()?.let { mm ->
                val a = mm[0].toIntOrNull()
                val b = mm[1].toIntOrNull()
                if (a != null && b != null) {
                    setsCount = a
                    reps = b
                    words = words.replace(Regex("""\d+\s*x\s*\d+"""), "")
                }
            }
            val cleaned = words.trim { it.isWhitespace() || it in ".,;:!?()-–'\"" }
            if (cleaned.isNotEmpty()) note += cleaned
        }
        s = s.replace(Regex("""\([^)]*\)"""), " ")
        val lbs = s.contains("lb")
        s = s.replace(Regex("""(kg|kilo|lbs?)"""), " ").trim()

        fun toKg(value: Double): Double {
            val kg = if (lbs || unit == WeightUnit.LB) value / 2.2046226 else value
            return LiftrFormat.round2(kg)
        }

        NotesImporter.allMatches("""^(\d+(?:[.,]\d+)?)\s*x\s*(\d+)$""", s).firstOrNull()?.let { m ->
            val w = LiftrFormat.parse(m[0])
            val count = m[1].toIntOrNull()
            if (w != null && count != null && count > 0) {
                val n = if (count <= 6) count else (setsCount ?: options.defaultSets)
                val r = if (count <= 6) (reps ?: options.defaultReps) else count
                return List(n) { PSet(r, toKg(w)) } to note.joinToString(", ")
            }
        }

        val parts = s.split('/', '-', '+', ';').map { it.trim() }.filter { it.isNotEmpty() }
        val weights = parts.mapNotNull { part -> LiftrFormat.parse(part)?.takeIf { it >= 0 && it < 1000 } }
        if (weights.isEmpty() || weights.size != parts.size) return null
        if (weights.size == 1) {
            val n = setsCount ?: options.defaultSets
            return List(maxOf(1, n)) { PSet(reps ?: options.defaultReps, toKg(weights[0])) } to note.joinToString(", ")
        }
        return weights.map { PSet(reps ?: options.defaultReps, toKg(it)) } to note.joinToString(", ")
    }

    // MARK: Per set

    private fun parsePerSet(header: List<String>, body: List<List<String>>, dateColumn: Int, exerciseColumn: Int, headerRow: Int,
                            options: Options, unit: WeightUnit, today: LocalDateTime): Result {
        fun column(names: List<String>): Int? = header.indexOfFirst { it in names }.takeIf { it >= 0 }
        val setsColumn = column(listOf("sets", "aantalsets", "series"))
        val repsColumn = column(listOf("reps", "herhalingen", "herh", "repetitions", "rep", "aantal"))
        val kgColumn = column(listOf("weightkg", "gewichtkg", "kg", "kilo"))
        val lbColumn = column(listOf("weightlb", "weightlbs", "lb", "lbs"))
        val weightColumn = kgColumn ?: lbColumn ?: column(listOf("weight", "gewicht", "load"))
        val minutesColumn = column(listOf("durationmin", "duur", "duurmin", "minuten", "min", "minutes", "duration"))
        val kmColumn = column(listOf("distancekm", "afstand", "afstandkm", "km", "distance"))
        val titleColumn = column(listOf("workout", "training", "titel", "title"))

        fun cell(row: List<String>, c: Int?): String = if (c != null && c < row.size) row[c] else ""

        val raw = mutableListOf<Pair<DayMonth, Int>>()
        body.forEachIndexed { i, row ->
            val dm = dayMonth(cell(row, dateColumn))
            if (dm != null && cell(row, exerciseColumn).isNotEmpty()) raw += dm to i
        }
        val dates = resolveYears(raw.map { it.first }, today)

        val days = mutableListOf<Day>()
        val skipped = mutableListOf<String>()
        raw.forEachIndexed { n, (_, rowIndex) ->
            val row = body[rowIndex]
            val date = dates[n]
            val title = cell(row, titleColumn)
            val name = cell(row, exerciseColumn)

            val weightText = cell(row, weightColumn)
            var weight = LiftrFormat.parse(weightText.lowercase().replace(Regex("[^0-9.,]"), "")) ?: 0.0
            val inPounds = (lbColumn != null && weightColumn == lbColumn) || weightText.lowercase().contains("lb") ||
                (kgColumn == null && lbColumn == null && unit == WeightUnit.LB)
            if (inPounds) weight /= 2.2046226
            weight = LiftrFormat.round2(weight)
            val repsValue = (LiftrFormat.parse(cell(row, repsColumn)) ?: 0.0).toInt()
            val count = if (setsColumn != null) (LiftrFormat.parse(cell(row, setsColumn)) ?: 1.0).toInt() else 1
            val minutes = LiftrFormat.parse(cell(row, minutesColumn)) ?: 0.0
            val km = LiftrFormat.parse(cell(row, kmColumn)) ?: 0.0
            if (!(weight > 0 || repsValue > 0 || minutes > 0 || km > 0)) {
                skipped += "${headerRow + rowIndex + 2}: $name"
                return@forEachIndexed
            }
            val isCardio = repsValue == 0 && (minutes > 0 || km > 0)
            val one = PSet(if (isCardio) 0 else (if (repsValue > 0) repsValue else options.defaultReps), weight, minutes, km)
            val sets = List(maxOf(1, minOf(count, 20))) { one }

            val sameDay = LiftrFormat.localDate(date)
            var dayIndex = days.indexOfFirst { LiftrFormat.localDate(it.date) == sameDay && it.note == title }
            if (dayIndex < 0) {
                days += Day(date = date, lines = emptyList(), note = title)
                dayIndex = days.size - 1
            }
            val day = days[dayIndex]
            val lines = day.lines.toMutableList()
            if (lines.isNotEmpty() && lines.last().name == name) {
                val last = lines.last()
                lines[lines.size - 1] = last.copy(sets = last.sets + sets, original = last.original + " · " + summary(sets))
            } else {
                lines += Line(name = name, sets = sets, original = summary(sets))
            }
            days[dayIndex] = day.copy(lines = lines)
        }
        return Result(days, Layout.PerSet, emptyList(), skipped)
    }

    private fun summary(sets: List<PSet>): String = sets.joinToString(", ") { set ->
        if (set.reps == 0) "${LiftrFormat.decimal(set.minutes)} min"
        else if (set.weightKg > 0) "${set.reps} × ${LiftrFormat.decimal(set.weightKg, 2)} kg" else "${set.reps} ×"
    }

    // MARK: Dates

    data class DayMonth(val day: Int, val month: Int, val year: Int?, val hour: Int = 18, val minute: Int = 0)

    /** "15 mei", "15 mei 2025", "ma 15 mei", "15-5", "15-05-2025", "2025-05-15T18:00", or an Excel date number. */
    fun dayMonth(text: String): DayMonth? {
        val s = text.lowercase().trim()
        if (s.isEmpty()) return null
        NotesImporter.firstMatch("""^(\d{4})-(\d{1,2})-(\d{1,2})(?:[t\s](\d{1,2}):(\d{2}))?""", s)?.let { m ->
            val y = m[0].toIntOrNull()
            val mo = m[1].toIntOrNull()
            val d = m[2].toIntOrNull()
            if (y != null && mo != null && d != null) return valid(DayMonth(d, mo, y, m[3].toIntOrNull() ?: 18, m[4].toIntOrNull() ?: 0))
        }
        NotesImporter.firstMatch("""^(?:[a-z]+\.?,?\s+)?(\d{1,2})[-/.](\d{1,2})(?:[-/.](\d{2,4}))?$""", s)?.let { m ->
            val d = m[0].toIntOrNull()
            val mo = m[1].toIntOrNull()
            // "27.5" is a weight, "27.5.2025" a date
            val looksLikeWeight = m[2].isEmpty() && Regex("""\d\.\d""").containsMatchIn(s)
            if (d != null && mo != null && !looksLikeWeight) {
                return valid(DayMonth(d, mo, m[2].toIntOrNull()?.let { if (it < 100) it + 2000 else it }))
            }
        }
        NotesImporter.firstMatch("""^(?:[a-z]+\.?,?\s+)?(\d{1,2})\s+([a-z]+)\.?(?:\s+(\d{4}))?$""", s)?.let { m ->
            val d = m[0].toIntOrNull()
            val mo = NotesImporter.months[m[1]] ?: NotesImporter.months[m[1].take(3)]
            if (d != null && mo != null) return valid(DayMonth(d, mo, m[2].toIntOrNull()))
        }
        // Excel stores dates as days since 30 December 1899.
        val serial = s.toDoubleOrNull()
        if (serial != null && serial > 20_000 && serial < 80_000) {
            val utc = Instant.ofEpochMilli(((serial - 25_569) * 86_400_000).toLong()).atOffset(ZoneOffset.UTC)
            val hasTime = serial % 1 != 0.0
            return DayMonth(utc.dayOfMonth, utc.monthValue, utc.year, if (hasTime) utc.hour else 18, if (hasTime) utc.minute else 0)
        }
        return null
    }

    private fun valid(dm: DayMonth): DayMonth? = if (dm.day in 1..31 && dm.month in 1..12) dm else null

    /** Fills in missing years from the order of the rows (oldest at the top or at the bottom both work). Returns epoch millis. */
    fun resolveYears(items: List<DayMonth>, today: LocalDateTime): List<Long> {
        val thisYear = today.year
        fun date(dm: DayMonth, year: Int): LocalDateTime =
            runCatching { LocalDateTime.of(year, dm.month, dm.day, dm.hour.coerceIn(0, 23), dm.minute.coerceIn(0, 59)) }
                .getOrElse { LocalDateTime.of(year, dm.month, 1, 18, 0) }
        fun order(dm: DayMonth): Int = dm.month * 100 + dm.day
        if (items.isEmpty()) return emptyList()

        val withYear = items.mapNotNull { dm -> dm.year?.let { date(dm, it) } }
        val newestFirst = withYear.size >= 2 && withYear.first() > withYear.last()
        val indices = if (newestFirst) items.indices.toList() else items.indices.reversed()

        val years = IntArray(items.size) { thisYear }
        var previous: Pair<DayMonth, Int>? = null
        for (i in indices) {
            val dm = items[i]
            var year: Int
            val prev = previous
            if (dm.year != null) {
                year = dm.year
            } else if (prev != null) {
                year = prev.second
                if (order(dm) > order(prev.first)) year -= 1
            } else {
                year = thisYear
                if (date(dm, year) > today.plusDays(1)) year -= 1
            }
            years[i] = year
            previous = dm to year
        }
        return items.indices.map { date(items[it], years[it]).atZone(LiftrFormat.zone).toInstant().toEpochMilli() }
    }
}
