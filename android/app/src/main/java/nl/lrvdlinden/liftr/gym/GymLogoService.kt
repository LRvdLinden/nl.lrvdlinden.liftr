package nl.lrvdlinden.liftr.gym

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.lrvdlinden.liftr.core.GymTemplate
import nl.lrvdlinden.liftr.core.GymTemplates
import nl.lrvdlinden.liftr.core.Prefs
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the official icon each gym chain publishes on its own website (apple-touch-icon or site icon),
 * so passes show the chain's current logo (port of GymLogoService.swift). Until then, or offline, the
 * logo that ships with the app is shown.
 */
object GymLogoService {
    /** Bumped when a new logo was saved, so cards redraw. */
    val version = mutableIntStateOf(0)

    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    suspend fun refresh(context: Context, template: GymTemplate, force: Boolean = false) = withContext(Dispatchers.IO) {
        if (template.retired || !template.usesWebsiteLogo || template.domain.isEmpty()) return@withContext
        val p = Prefs.prefs
        val checked = runCatching { JSONObject(p.getString(Prefs.GYM_LOGO_CHECKED, "{}") ?: "{}") }.getOrElse { JSONObject() }
        val last = checked.optLong(template.domain, 0L)
        if (!force && System.currentTimeMillis() - last < 14 * 86_400_000L) return@withContext
        val data = download(template.domain)
        checked.put(template.domain, System.currentTimeMillis())
        p.edit().putString(Prefs.GYM_LOGO_CHECKED, checked.toString()).apply()
        if (data != null) {
            val file = GymTemplates.downloadedLogoFile(context, template.domain)
            if (!file.exists() || !file.readBytes().contentEquals(data)) {
                file.writeBytes(data)
                withContext(Dispatchers.Main) { version.intValue += 1 }
            }
        }
    }

    suspend fun refreshAll(context: Context) {
        for (t in GymTemplates.selectable) runCatching { refresh(context, t) }
    }

    private fun get(url: String, max: Int): Pair<ByteArray, String>? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept-Language", "nl-NL,nl;q=0.9,en;q=0.8")
        try {
            if (conn.responseCode >= 400) null
            else conn.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (out.size() < max) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                out.toByteArray() to conn.url.toString()
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    private fun download(domain: String): ByteArray? {
        val candidates = mutableListOf<String>()
        for (host in listOf("https://www.$domain/", "https://$domain/")) {
            val page = get(host, 800_000) ?: continue
            candidates += iconLinks(String(page.first, Charsets.UTF_8), page.second)
            break
        }
        candidates += listOf("https://www.$domain/apple-touch-icon.png", "https://$domain/apple-touch-icon.png")
        for (url in candidates.distinct()) {
            val bytes = get(url, 3_000_000)?.first ?: continue
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            if (minOf(bmp.width, bmp.height) < 96) continue
            return png(bmp, 256)
        }
        return null
    }

    private fun png(image: Bitmap, maxSide: Int): ByteArray {
        val factor = minOf(1.0, maxSide.toDouble() / maxOf(image.width, image.height))
        val scaled = Bitmap.createScaledBitmap(image, (image.width * factor).toInt().coerceAtLeast(1), (image.height * factor).toInt().coerceAtLeast(1), true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    /** `<link rel="apple-touch-icon" …>` and `<link rel="icon" sizes="…">` from a page, best first. */
    fun iconLinks(html: String, base: String): List<String> {
        val tagPattern = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
        val attrPattern = Regex("([a-zA-Z-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
        val found = mutableListOf<Pair<String, Int>>()
        for (tag in tagPattern.findAll(html)) {
            val attrs = HashMap<String, String>()
            for (a in attrPattern.findAll(tag.value)) {
                val value = listOf(2, 3, 4).firstNotNullOfOrNull { a.groups[it]?.value } ?: ""
                attrs[a.groupValues[1].lowercase()] = value
            }
            val rel = attrs["rel"]?.lowercase() ?: ""
            val href = attrs["href"] ?: continue
            if (!rel.contains("icon") || rel.contains("mask")) continue
            val lower = href.lowercase()
            if (lower.endsWith(".svg") || lower.endsWith(".ico")) continue
            val url = runCatching { URL(URL(base), href.replace("&amp;", "&")).toString() }.getOrNull() ?: continue
            val declared = attrs["sizes"]?.lowercase()?.split("x")?.firstOrNull()?.toIntOrNull() ?: 0
            found += url to ((if (rel.contains("apple-touch-icon")) 10_000 else 0) + declared)
        }
        return found.sortedByDescending { it.second }.map { it.first }
    }
}
