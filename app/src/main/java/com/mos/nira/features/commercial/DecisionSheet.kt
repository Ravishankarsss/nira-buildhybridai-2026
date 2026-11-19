package com.mos.nira.features.commercial

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.mos.nira.R
import com.mos.nira.core.advice.BaseSource
import com.mos.nira.core.advice.DecisionCard
import com.mos.nira.core.advice.Fibre
import com.mos.nira.core.commercial.CostDownKind
import com.mos.nira.core.commercial.CostDownOption
import com.mos.nira.core.commercial.CostMethod
import com.mos.nira.core.commercial.CostResult
import com.mos.nira.core.commercial.FactoryRates
import com.mos.nira.core.image.ColourName
import com.mos.nira.core.image.GarmentPreview
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything the one-page Decision Sheet shows; all of it is already on the decision card. */
data class SheetData(
    val card: DecisionCard,
    val cost: CostResult?,
    val rates: FactoryRates,
    val chosenOption: CostDownOption?,
    val contrast: List<GarmentPreview.ContrastWarning>,
    val preview: Bitmap?
)

/**
 * The one-tap Print Decision Sheet: a single image for WhatsApp or email, as a Buyer Quote
 * (English) or a Floor Job Sheet (Tamil). Drawn on the phone from the decision card; no AI and no
 * colour codes. Texts that come from the rules files (method names, risks, steps) stay as written.
 */
object DecisionSheet {

    private const val W = 1080
    private const val PAD = 56f

    private fun localized(ctx: Context, language: String): Context {
        val conf = Configuration(ctx.resources.configuration)
        conf.setLocale(Locale(language))
        return ctx.createConfigurationContext(conf)
    }

    /** A colour name in the sheet's language. */
    fun colourName(c: Context, hex: String): String {
        val n = ColourName.of(hex) ?: return hex
        val base = c.getString(
            when (n.base) {
                ColourName.Base.WHITE -> R.string.colour_white
                ColourName.Base.LIGHT_GREY -> R.string.colour_light_grey
                ColourName.Base.GREY -> R.string.colour_grey
                ColourName.Base.DARK_GREY -> R.string.colour_dark_grey
                ColourName.Base.BLACK -> R.string.colour_black
                ColourName.Base.RED -> R.string.colour_red
                ColourName.Base.PINK -> R.string.colour_pink
                ColourName.Base.MAGENTA -> R.string.colour_magenta
                ColourName.Base.ORANGE -> R.string.colour_orange
                ColourName.Base.BROWN -> R.string.colour_brown
                ColourName.Base.YELLOW -> R.string.colour_yellow
                ColourName.Base.OLIVE -> R.string.colour_olive
                ColourName.Base.CREAM -> R.string.colour_cream
                ColourName.Base.GREEN -> R.string.colour_green
                ColourName.Base.TEAL -> R.string.colour_teal
                ColourName.Base.BLUE -> R.string.colour_blue
                ColourName.Base.NAVY -> R.string.colour_navy
                ColourName.Base.PURPLE -> R.string.colour_purple
            }
        )
        val loc = c.resources.configuration.locales[0]
        val text = when (n.shade) {
            ColourName.Shade.NONE -> return base
            ColourName.Shade.DARK -> c.getString(R.string.colour_dark, base.lowercase(loc))
            ColourName.Shade.LIGHT -> c.getString(R.string.colour_light, base.lowercase(loc))
        }
        return text.replaceFirstChar { it.titlecase(loc) }
    }

    private fun label(c: Context, name: String) = if (Regex("^#[0-9A-Fa-f]{6}$").matches(name)) colourName(c, name) else name

    private fun fibre(c: Context, f: Fibre) = c.getString(
        when (f) {
            Fibre.COTTON -> R.string.fibre_cotton
            Fibre.POLYESTER -> R.string.fibre_polyester
            Fibre.COTTON_POLY_BLEND -> R.string.fibre_blend
            Fibre.LINEN -> R.string.fibre_linen
            Fibre.NYLON -> R.string.fibre_nylon
        }
    )

    private fun fabric(c: Context, id: String) = when (id) {
        "single_jersey" -> c.getString(R.string.fabric_single_jersey)
        "pique" -> c.getString(R.string.fabric_pique)
        "fleece" -> c.getString(R.string.fabric_fleece)
        "interlock" -> c.getString(R.string.fabric_interlock)
        "other" -> c.getString(R.string.fabric_other)
        else -> id
    }

    private fun method(c: Context, m: CostMethod) = c.getString(
        when (m) {
            CostMethod.SCREEN -> R.string.method_screen
            CostMethod.DIGITAL -> R.string.method_digital
            CostMethod.DISCHARGE_DIGITAL -> R.string.method_discharge_digital
        }
    )

    private fun optionTitle(c: Context, o: CostDownOption) = when (o.kind) {
        CostDownKind.MERGE_COLOURS -> c.getString(R.string.costdown_merge, colourName(c, o.fromHex ?: ""), colourName(c, o.intoHex ?: ""))
        CostDownKind.SWITCH_METHOD -> c.getString(R.string.costdown_switch, method(c, o.after.method), o.after.quantity ?: 0)
        CostDownKind.LIGHT_GARMENT -> c.getString(R.string.costdown_light)
    }

    private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)

    /** Draws the sheet. [floor] = Floor Job Sheet in Tamil; otherwise Buyer Quote in English. */
    fun render(ctx: Context, d: SheetData, floor: Boolean): Bitmap {
        val c = localized(ctx, if (floor) "ta" else "en")
        val card = d.card
        val best = card.best ?: error("no method")
        val bmp = Bitmap.createBitmap(W, 5200, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(0xFFFFFFFF.toInt())
        var y = PAD
        val ink = 0xFF14182A.toInt()
        val muted = 0xFF5B6478.toInt()
        val accent = 0xFF4A3FC4.toInt()
        val green = 0xFF1E8A4C.toInt()
        val amber = 0xFFB86E00.toInt()

        fun text(s: String, size: Float, colour: Int = ink, bold: Boolean = false, gapAfter: Float = 10f) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; color = colour; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, p, (W - 2 * PAD).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(4f, 1f).build()
            cv.save(); cv.translate(PAD, y); layout.draw(cv); cv.restore()
            y += layout.height + gapAfter
        }
        fun rule() {
            val p = Paint().apply { color = 0xFFE1E4EC.toInt(); strokeWidth = 2f }
            cv.drawLine(PAD, y, W - PAD, y, p); y += 22f
        }
        fun section(title: String) { y += 8f; text(title.uppercase(), 26f, accent, bold = true, gapAfter = 6f) }

        // Header
        text("NIRA", 44f, accent, bold = true, gapAfter = 0f)
        text(c.getString(if (floor) R.string.sheet_floor_title else R.string.sheet_buyer_title), 52f, ink, bold = true, gapAfter = 4f)
        val colours = card.screens.filter { !it.isBaseWhite && !it.isProcess }.map { colourName(c, it.hex).lowercase(c.resources.configuration.locales[0]) }.distinct()
        val jobColours = if (colours.isNotEmpty()) colours.joinToString(", ") else card.screens.map { label(c, it.name) }.joinToString(", ")
        if (jobColours.isNotBlank()) text(c.getString(R.string.sheet_job, jobColours), 30f, muted, gapAfter = 2f)
        text(SimpleDateFormat("d MMM yyyy", c.resources.configuration.locales[0]).format(Date()), 26f, muted)
        rule()

        // Garment and brief
        val a = card.answers
        val brief = listOfNotNull(
            a.garmentHex?.let { c.getString(R.string.sheet_garment, colourName(c, it)) },
            a.fibre?.let { fibre(c, it) + (a.fabric?.let { fb -> ", " + fabric(c, fb) } ?: "") },
            a.quantity?.let { c.getString(R.string.sheet_quantity, it) }
        ).joinToString("  ·  ")
        text(brief, 30f)

        // Preview
        d.preview?.let { p ->
            val maxW = W - 2 * PAD
            val h = (maxW * p.height / p.width.toFloat()).coerceAtMost(900f)
            val w = h * p.width / p.height
            val left = (W - w) / 2f
            val box = RectF(left, y, left + w, y + h)
            cv.drawBitmap(p, Rect(0, 0, p.width, p.height), box, Paint(Paint.FILTER_BITMAP_FLAG))
            // A thin frame shows the garment's edge even when the garment is white like the page.
            cv.drawRect(box, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0xFFC9CED9.toInt() })
            y += h + 8f
            text(c.getString(R.string.sheet_preview_note), 22f, muted)
        }
        d.contrast.forEach { text("⚠ " + c.getString(R.string.card_contrast_risk, colourName(c, it.inkHex), it.deltaL), 26f, amber) }

        // Method
        section(c.getString(R.string.card_method))
        text(best.advice.name, 36f, ink, bold = true, gapAfter = 4f)
        if (best.advice.whatItIs.isNotBlank()) text(best.advice.whatItIs, 26f, muted, gapAfter = 4f)
        text(c.getString(if (best.advice.verifiedByRavi) R.string.card_verified else R.string.card_not_verified), 24f, if (best.advice.verifiedByRavi) green else amber)

        // Screens
        section(c.getString(R.string.card_screens))
        val q = a.quantity
        if (card.baseSource == BaseSource.DIGITAL) {
            val n = best.advice.screens
            text(if (n != null && n > 0) c.getString(R.string.card_screens_value, n, best.advice.screensDetail ?: "") else c.getString(R.string.card_screens_digital), 28f)
            best.advice.processSteps.forEachIndexed { i, s -> text("${i + 1}. $s", 26f, gapAfter = 4f) }
        } else {
            card.screens.forEach { s ->
                val inkKg = s.gramsPerGarment?.let { g -> q?.let { qq -> String.format(Locale.US, "%.2f", g * qq / 1000.0) } }
                val line = label(c, s.name) + " — " + c.getString(R.string.sep_mesh) + " " + s.mesh +
                    (inkKg?.let { "  ·  " + c.getString(R.string.sheet_ink_kg, it) } ?: s.gramsPerGarment?.let { "" } ?: ("  ·  " + c.getString(R.string.card_grams_none)))
                text("• $line", 28f, gapAfter = 4f)
            }
        }
        text(
            c.getString(R.string.card_base) + ": " + when {
                card.baseSource == BaseSource.DISCHARGE || "discharge" in best.advice.id -> c.getString(R.string.card_base_discharge)
                card.baseNeeded == true -> c.getString(R.string.card_base_yes)
                else -> c.getString(R.string.card_base_no)
            },
            26f, muted
        )

        // Order cost
        val cost = d.cost
        if (cost?.perPc != null && cost.total != null && cost.quantity != null) {
            section(c.getString(R.string.cost_title))
            text(c.getString(R.string.cost_per_pc, money(d.rates, cost.perPc, 2)) + "   ·   " + c.getString(R.string.cost_total, money(d.rates, cost.total, 0), cost.quantity), 34f, ink, bold = true, gapAfter = 4f)
            text(c.getString(if (d.rates.isSample) R.string.rates_sample_badge else R.string.rates_factory_badge), 24f, if (d.rates.isSample) amber else green)
            if (!cost.complete) text(c.getString(R.string.sheet_cost_partial), 24f, amber)
            d.chosenOption?.let { o ->
                text(c.getString(R.string.sheet_costdown, optionTitle(c, o)), 28f, gapAfter = 2f)
                text(c.getString(R.string.costdown_new, money(d.rates, o.after.perPc ?: 0.0, 2), money(d.rates, o.savingPerPc, 2), money(d.rates, o.savingTotal, 0)), 24f, green, gapAfter = 2f)
                when (o.kind) {
                    CostDownKind.MERGE_COLOURS -> text(c.getString(if (o.closeMatch == true) R.string.badge_close else R.string.badge_visible), 24f, if (o.closeMatch == true) green else amber)
                    CostDownKind.LIGHT_GARMENT -> text(c.getString(R.string.badge_buyer), 24f, amber)
                    else -> Unit
                }
            }
        }

        // Risks and the honest close
        if (best.advice.risks.isNotEmpty()) {
            section(c.getString(R.string.card_risks))
            best.advice.risks.forEach { text("• $it", 26f, gapAfter = 4f) }
        }
        rule()
        text(c.getString(R.string.card_disclaimer), 26f, amber, bold = true)
        text(c.getString(R.string.sheet_footer), 22f, muted, gapAfter = 0f)
        y += PAD
        return Bitmap.createBitmap(bmp, 0, 0, W, y.toInt().coerceAtMost(bmp.height))
    }

    /** Saves the sheet in the app's cache and opens the Android share sheet. */
    fun share(ctx: Context, sheet: Bitmap, floor: Boolean) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "NIRA_Decision_Sheet_${if (floor) "floor_ta" else "buyer_en"}.png")
        FileOutputStream(file).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "NIRA Print Decision Sheet")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.sheet_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
