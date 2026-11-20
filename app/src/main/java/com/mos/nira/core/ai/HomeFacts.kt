package com.mos.nira.core.ai

import com.mos.nira.core.advice.TechniqueLibrary
import com.mos.nira.core.commercial.Money
import com.mos.nira.core.image.ColourName
import com.mos.nira.features.advice.JobSummary
import com.mos.nira.features.artwork.domain.ArtworkAnalysisRecord
import com.mos.nira.features.artwork.domain.mainPrintHexes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The facts Ask NIRA on Home answers from: the saved jobs (with their decision when the job page
 * worked it out), every technique in the rules file, the AI activity totals and whether factory
 * rates are set. Plain English, colour names only, no codes.
 */
object HomeFacts {

    const val TEXT_PROMPT = "prompts/ask_home_v1.txt"
    const val VOICE_PROMPT = "prompts/ask_home_voice_v1.txt"

    /** Most saved jobs listed (newest first). */
    const val MAX_JOBS = 12

    private val CAMERA_NAME = Regex("^(IMG|PXL|DSC|Screenshot|garment)?[_-]?[0-9_ -]{6,}\\.(png|jpe?g|webp)$", RegexOption.IGNORE_CASE)

    /** A job's name in plain English, the same way for the facts and for "open_job". */
    fun jobName(r: ArtworkAnalysisRecord): String {
        val colours = r.mainPrintHexes().map { ColourName.english(it).lowercase() }.distinct()
        val kind = if (r.inputKind == "PHOTO") "Garment photo" else "Artwork"
        return if (CAMERA_NAME.matches(r.fileName.trim()) && colours.isNotEmpty()) "$kind · ${colours.joinToString(", ")}"
        else r.fileName.substringBeforeLast('.')
    }

    fun jobs(saved: List<ArtworkAnalysisRecord>): List<Pair<String, String>> {
        val newest = saved.sortedByDescending { it.timestamp }.take(MAX_JOBS)
        // Two jobs with the same name get a number so "open_job" is never ambiguous.
        val seen = mutableMapOf<String, Int>()
        return newest.map { r ->
            val base = jobName(r)
            val n = (seen[base] ?: 0) + 1
            seen[base] = n
            r.id to if (n == 1) base else "$base ($n)"
        }
    }

    fun build(
        saved: List<ArtworkAnalysisRecord>,
        summaries: Map<String, JobSummary>,
        library: TechniqueLibrary.Library?,
        activity: AiActivityLog.Totals,
        ratesSet: Boolean,
        ratesSample: Boolean,
        currency: String
    ): List<String> = buildList {
        val byId = saved.associateBy { it.id }
        val date = SimpleDateFormat("d MMM yyyy", Locale.US)
        val list = jobs(saved)
        add("Saved jobs on this phone: ${saved.size}" + if (saved.size > list.size) " (the newest ${list.size} are listed)." else ".")
        list.forEach { (id, name) ->
            val r = byId.getValue(id)
            val s = summaries[id]
            val decision = if (s?.method != null) {
                "method ${s.method}" + (s.screens?.let { "; $it screens" } ?: "") +
                    (s.perPc?.let { "; cost ${Money.plain(s.currency, it, 2)} per piece" + if (s.sampleRates) " (sample rates, not a quote)" else "" } ?: "") +
                    if (s.verifiedByRavi) "; verified by Ravi on the floor" else ""
            } else "method not worked out yet (open the job and answer the four brief questions)"
            add("Saved job \"$name\": analysed ${date.format(Date(r.timestamp))}; ${r.printColourCount} print colours; $decision.")
        }
        library?.entries?.forEach { e ->
            val t = e.rule
            add(
                "Technique ${t.name} (${t.group.replace('_', ' ')}): suitable fibres ${t.suitableFibres.joinToString(", ").ifBlank { "not listed" }}" +
                    (if (t.notSuitableFibres.isNotEmpty()) "; not suitable ${t.notSuitableFibres.joinToString(", ")}" else "") +
                    "; " + if (t.verifiedByRavi) "verified by Ravi on the floor." else "not floor-verified."
            )
        }
        add("AI on this phone so far: ${activity.onDevice} answers on the phone, ${activity.cloud} in the cloud, ${activity.held} held back by NIRA's checks.")
        add(if (ratesSet) "Factory rates: set in $currency" + (if (ratesSample) " (sample rates, not a quote)." else ".") else "Factory rates: not set yet (Settings).")
        add("NIRA decides print methods with its engine and Ravi's rules files when a job's four brief questions are answered.")
    }
}
