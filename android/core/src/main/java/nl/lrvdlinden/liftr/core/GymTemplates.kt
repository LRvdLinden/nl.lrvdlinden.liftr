package nl.lrvdlinden.liftr.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** A known gym chain: name, website, card colour and the code type its pass uses (from Shared/GymTemplates.swift). */
data class GymTemplate(
    val id: String,
    val name: String,
    val domain: String,
    /** The chain's house colour, used for the card. */
    val colorHex: String,
    val format: BarcodeFormat,
    /** true: wide wordmark shown on a pill; false: square artwork shown as a rounded icon. */
    val wordmark: Boolean = false,
    /** Tile behind a see-through logo so it always reads on the card (null: logo is opaque artwork). */
    val logoBackgroundHex: String? = null,
    /** Replace the bundled logo with the icon from the chain's website (false: always the bundled logo). */
    val usesWebsiteLogo: Boolean = true,
    /** Kept so existing passes still find their chain, but no longer offered in the picker. */
    val retired: Boolean = false,
    /** Card colours earlier builds gave this chain; passes still on one of these move to the current colour. */
    val formerColors: List<String> = emptyList(),
)

object GymTemplates {
    val all: List<GymTemplate> = listOf(
        GymTemplate("basicfit", "Basic-Fit", "basic-fit.com", "#FB7819", BarcodeFormat.QR, wordmark = true, usesWebsiteLogo = false),
        GymTemplate("sportcity", "SportCity", "sportcity.nl", "#F7B72C", BarcodeFormat.QR, formerColors = listOf("#E30613")),
        GymTemplate("anytime", "Anytime Fitness", "anytimefitness.nl", "#5E2D91", BarcodeFormat.CODE128, logoBackgroundHex = "#5E2D91"),
        GymTemplate("trainmore", "TrainMore", "trainmore.nl", "#111111", BarcodeFormat.QR, formerColors = listOf("#E30613")),
        GymTemplate("snapfitness", "Snap Fitness", "snapfitness.com", "#B3302A", BarcodeFormat.QR, logoBackgroundHex = "#FFFFFF", formerColors = listOf("#6A1B9A")),
        GymTemplate("biggym", "Big Gym", "biggym.nl", "#111111", BarcodeFormat.QR, formerColors = listOf("#1A1A1A")),
        GymTemplate("fit20", "fit20", "fit20.nl", "#8DC21F", BarcodeFormat.QR, wordmark = true, logoBackgroundHex = "#FFFFFF", formerColors = listOf("#E30613")),
        GymTemplate("healthcity", "HealthCity", "healthcity.nl", "#1A1A1A", BarcodeFormat.QR, logoBackgroundHex = "#FFFFFF", formerColors = listOf("#E30613")),
        // fitforfree.nl now serves another chain's logo and site, so it is no longer offered as a separate chain.
        GymTemplate("fitforfree", "Fit For Free", "fitforfree.nl", "#F7B72C", BarcodeFormat.QR, retired = true, formerColors = listOf("#E30613")),
    )

    /** The chains offered in the picker. */
    val selectable: List<GymTemplate> get() = all.filter { !it.retired }

    fun template(id: String?): GymTemplate? = id?.let { i -> all.firstOrNull { it.id == i } }

    /** The logo that ships with the app (assets/gymlogos/<domain>.png, copied from Shared/GymLogos). */
    fun bundledLogo(context: Context, template: GymTemplate?): Bitmap? {
        if (template == null || template.retired || template.domain.isEmpty()) return null
        return try {
            context.assets.open("gymlogos/${template.domain}.png").use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }

    /** Where the official icon downloaded from the chain's website is kept. */
    fun downloadedLogoFile(context: Context, domain: String): File {
        val folder = File(context.filesDir, "gymlogos").apply { mkdirs() }
        return File(folder, "$domain.png")
    }

    /** The official website icon if it was downloaded, otherwise the logo in the app. Second value: from the website. */
    fun logo(context: Context, template: GymTemplate?): Pair<Bitmap, Boolean>? {
        if (template == null || template.retired) return null
        if (template.usesWebsiteLogo) {
            val file = downloadedLogoFile(context, template.domain)
            if (file.exists()) BitmapFactory.decodeFile(file.path)?.let { return it to true }
        }
        return bundledLogo(context, template)?.let { it to false }
    }

    /** Passes still on a colour an earlier build gave their chain get the chain's current colour. */
    fun updateCardColors(repository: LiftrRepository) {
        repository.commit { data ->
            for (pass in data.passes.values) {
                val t = pass.template ?: continue
                if (t.formerColors.any { it.equals(pass.colorHex, ignoreCase = true) }) passes += pass.copy(colorHex = t.colorHex)
            }
        }
    }

    fun parseHex(hex: String): Long {
        var s = hex.trim()
        if (s.startsWith("#")) s = s.substring(1)
        return s.toLongOrNull(16) ?: 0xFF6B00L
    }

    /** ARGB int for a "#RRGGBB" colour. */
    fun argb(hex: String): Int = (0xFF000000L or (parseHex(hex) and 0xFFFFFFL)).toInt()

    /** White or black, whichever reads best on this card colour. */
    fun inkIsBlack(hex: String): Boolean {
        val v = parseHex(hex)
        val r = ((v shr 16) and 0xFF) / 255.0
        val g = ((v shr 8) and 0xFF) / 255.0
        val b = (v and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.62
    }
}
