package nl.lrvdlinden.liftr.core

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Reads the cell text of an Excel workbook (.xlsx): a zip archive of XML files (port of XLSXReader.swift). */
object XlsxReader {
    data class Sheet(val name: String, val rows: List<List<String>>)

    class ReadException(message: String) : Exception(message)

    fun sheets(data: ByteArray): List<Sheet> {
        val files = unzip(data)
        fun file(path: String): ByteArray? = files[path] ?: files.entries.firstOrNull { it.key.equals(path, ignoreCase = true) }?.value
        val shared = file("xl/sharedStrings.xml")?.let { parseSharedStrings(it) } ?: emptyList()

        val list = mutableListOf<Pair<String, String>>()
        val workbook = file("xl/workbook.xml")
        if (workbook != null) {
            val rels = file("xl/_rels/workbook.xml.rels")?.let { parseRelationships(it) } ?: emptyMap()
            for ((name, relId) in parseWorkbook(workbook)) {
                var target = rels[relId] ?: continue
                target = if (target.startsWith("/")) target.substring(1) else "xl/$target"
                list += name to target
            }
        }
        if (list.isEmpty()) {
            files.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }.sorted()
                .forEach { list += it.substringAfterLast('/') to it }
        }
        if (list.isEmpty()) throw ReadException("worksheets")
        return list.mapNotNull { (name, path) -> file(path)?.let { Sheet(name, parseSheet(it, shared)) } }
    }

    private fun unzip(data: ByteArray): Map<String, ByteArray> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(data)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (!entry.isDirectory && (name.endsWith(".xml") || name.endsWith(".rels"))) files[name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        if (files.isEmpty()) throw ReadException("not a zip")
        return files
    }

    private fun parser(bytes: ByteArray): XmlPullParser {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(ByteArrayInputStream(bytes), null)
        return p
    }

    fun local(name: String): String = name.substringAfterLast(':')

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val p = parser(bytes)
        val current = StringBuilder()
        var inText = false
        var inPhonetic = false
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (local(p.name)) {
                    "si" -> current.setLength(0)
                    "t" -> inText = !inPhonetic
                    "rPh" -> inPhonetic = true
                }
                XmlPullParser.TEXT -> if (inText) current.append(p.text)
                XmlPullParser.END_TAG -> when (local(p.name)) {
                    "si" -> strings += current.toString()
                    "t" -> inText = false
                    "rPh" -> inPhonetic = false
                }
            }
            event = p.next()
        }
        return strings
    }

    private fun attr(p: XmlPullParser, name: String): String? {
        for (i in 0 until p.attributeCount) if (p.getAttributeName(i) == name) return p.getAttributeValue(i)
        return null
    }

    private fun parseRelationships(bytes: ByteArray): Map<String, String> {
        val map = HashMap<String, String>()
        val p = parser(bytes)
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && local(p.name) == "Relationship") {
                val id = attr(p, "Id")
                val target = attr(p, "Target")
                if (id != null && target != null) map[id] = target
            }
            event = p.next()
        }
        return map
    }

    private fun parseWorkbook(bytes: ByteArray): List<Pair<String, String>> {
        val sheets = mutableListOf<Pair<String, String>>()
        val p = parser(bytes)
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && local(p.name) == "sheet") {
                val name = attr(p, "name")
                if (name != null) {
                    var rel = attr(p, "r:id")
                    if (rel == null) {
                        for (i in 0 until p.attributeCount) if (p.getAttributeName(i).endsWith(":id")) rel = p.getAttributeValue(i)
                    }
                    sheets += name to (rel ?: "")
                }
            }
            event = p.next()
        }
        return sheets
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val cells = HashMap<Int, HashMap<Int, String>>()
        val p = parser(bytes)
        var row = 0
        var column = 0
        var type = ""
        val value = StringBuilder()
        var collecting = false
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (local(p.name)) {
                    "row" -> {
                        val r = attr(p, "r")?.toIntOrNull()
                        row = if (r != null) r - 1 else row + 1
                        column = -1
                    }
                    "c" -> {
                        val ref = attr(p, "r")
                        if (ref != null) {
                            val pos = position(ref, row)
                            row = pos.first
                            column = pos.second
                        } else {
                            column += 1
                        }
                        type = attr(p, "t") ?: ""
                        value.setLength(0)
                    }
                    "v", "t" -> collecting = true
                }
                XmlPullParser.TEXT -> if (collecting) value.append(p.text)
                XmlPullParser.END_TAG -> when (local(p.name)) {
                    "v", "t" -> collecting = false
                    "c" -> {
                        var text = value.toString()
                        if (type == "s") text.trim().toIntOrNull()?.let { if (it in shared.indices) text = shared[it] }
                        if (type == "b") text = if (text == "1") "TRUE" else "FALSE"
                        text = text.trim()
                        if (text.isNotEmpty()) cells.getOrPut(row) { HashMap() }[column] = text
                    }
                }
            }
            event = p.next()
        }
        val maxRow = cells.keys.maxOrNull() ?: return emptyList()
        val maxColumn = cells.values.flatMap { it.keys }.maxOrNull() ?: 0
        return (0..maxRow).map { r -> (0..maxColumn).map { c -> cells[r]?.get(c) ?: "" } }
    }

    /** "AB12" → (row 11, column 27). */
    fun position(ref: String, fallbackRow: Int): Pair<Int, Int> {
        var column = 0
        val digits = StringBuilder()
        for (ch in ref.uppercase()) {
            if (ch in 'A'..'Z') column = column * 26 + (ch - 'A' + 1)
            else if (ch.isDigit()) digits.append(ch)
        }
        return ((digits.toString().toIntOrNull() ?: (fallbackRow + 1)) - 1) to maxOf(0, column - 1)
    }
}
