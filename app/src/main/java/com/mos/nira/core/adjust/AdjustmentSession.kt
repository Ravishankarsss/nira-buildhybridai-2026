package com.mos.nira.core.adjust

import com.mos.nira.core.image.ImageOps
import com.mos.nira.features.artwork.data.MeshDepositRule
import com.mos.nira.features.artwork.domain.PrintJobInputs
import java.util.Locale
import kotlin.math.abs

/** A change that has been calculated but not yet accepted. */
class AdjustmentPreview(
    val op: AdjustmentOp,
    val before: SeparationState,
    val after: SeparationState,
    val beforeProof: IntArray,
    val afterProof: IntArray,
    val summary: ChangeSummary
)

/** What changed, in numbers and in plain lines ready to show. */
class ChangeSummary(
    val description: String,
    val before: SeparationMetrics,
    val after: SeparationMetrics,
    val lines: List<String>,
    val notes: List<String>
) {
    val hasChange: Boolean get() = lines.isNotEmpty()
}

/** Before and after of the last accepted change, for COMPARE_BEFORE_AFTER. */
class BeforeAfter(
    val beforeProof: IntArray,
    val afterProof: IntArray,
    val summary: ChangeSummary
)

/**
 * Holds the separation being adjusted and enforces the workflow:
 * preview -> Accept or Cancel, with one level of Undo.
 *
 * Nothing changes until accept() is called. After every accepted change the proof, colour
 * error, screens and ink quantity are recalculated and reported in a [ChangeSummary].
 */
class AdjustmentSession(
    initial: SeparationState,
    private val imageOps: ImageOps,
    private val inputs: PrintJobInputs,
    private val depositRules: Map<Int, MeshDepositRule>
) {
    var current: SeparationState = initial
        private set

    /** The state before the last accepted change. Null when there is nothing to undo. */
    private var undoState: SeparationState? = null
    private var lastDescription: String? = null

    var pending: AdjustmentPreview? = null
        private set

    val canUndo: Boolean get() = undoState != null

    /** Calculates a change without applying it. Replaces any earlier preview. */
    fun preview(op: AdjustmentOp): AdjustmentPreview {
        val before = current
        val after = op.apply(before, imageOps)
        val beforeProof = SeparationProof.render(before)
        val afterProof = SeparationProof.render(after)
        val preview = AdjustmentPreview(
            op = op,
            before = before,
            after = after,
            beforeProof = beforeProof,
            afterProof = afterProof,
            summary = summarise(op.description, before, after, beforeProof, afterProof, op.notes(before))
        )
        pending = preview
        return preview
    }

    /** Applies the previewed change. Returns its summary, or null if there is no preview. */
    fun accept(): ChangeSummary? {
        val preview = pending ?: return null
        undoState = current
        lastDescription = preview.op.description
        current = preview.after
        pending = null
        return preview.summary
    }

    /** Drops the previewed change. The separation is untouched. */
    fun cancel() {
        pending = null
    }

    /** Restores the state before the last accepted change. One level only. */
    fun undo(): Boolean {
        val previous = undoState ?: return false
        current = previous
        undoState = null
        lastDescription = null
        pending = null
        return true
    }

    /** Before and after of the last accepted change. Null if nothing has been accepted. */
    fun compareBeforeAfter(): BeforeAfter? {
        val before = undoState ?: return null
        val beforeProof = SeparationProof.render(before)
        val afterProof = SeparationProof.render(current)
        return BeforeAfter(
            beforeProof = beforeProof,
            afterProof = afterProof,
            summary = summarise(lastDescription ?: "Last change", before, current, beforeProof, afterProof, emptyList())
        )
    }

    fun currentMetrics(): SeparationMetrics = SeparationProof.metrics(current, inputs, depositRules)

    private fun summarise(
        description: String,
        before: SeparationState,
        after: SeparationState,
        beforeProof: IntArray,
        afterProof: IntArray,
        notes: List<String>
    ): ChangeSummary {
        val m0 = SeparationProof.metrics(before, inputs, depositRules, beforeProof)
        val m1 = SeparationProof.metrics(after, inputs, depositRules, afterProof)
        val lines = ArrayList<String>()

        if (m0.screens != m1.screens) lines.add("Screens: ${m0.screens} -> ${m1.screens}")
        for (name in m0.channelHex.keys - m1.channelHex.keys) lines.add("Removed screen: $name")
        for (name in m1.channelHex.keys - m0.channelHex.keys) lines.add("Added screen: $name")
        for ((name, hex) in m1.channelHex) {
            val old = m0.channelHex[name] ?: continue
            if (!old.equals(hex, ignoreCase = true)) lines.add("$name colour: $old -> $hex")
        }
        for ((name, coverage) in m1.channelCoveragePercent) {
            val old = m0.channelCoveragePercent[name] ?: continue
            if (abs(old - coverage) >= 0.05) lines.add("$name coverage: ${f1(old)}% -> ${f1(coverage)}%")
        }
        if (abs(m0.meanDeltaE00 - m1.meanDeltaE00) >= 0.005) {
            lines.add("Mean colour error: dE00 ${f2(m0.meanDeltaE00)} -> ${f2(m1.meanDeltaE00)}")
        }
        if (abs(m0.percentile95DeltaE00 - m1.percentile95DeltaE00) >= 0.005) {
            lines.add("95% colour error: dE00 ${f2(m0.percentile95DeltaE00)} -> ${f2(m1.percentile95DeltaE00)}")
        }
        if (abs(m0.inkGramsPerGarment - m1.inkGramsPerGarment) >= 0.005) {
            lines.add("Ink per garment (estimate): ${f2(m0.inkGramsPerGarment)} g -> ${f2(m1.inkGramsPerGarment)} g")
        }
        for (name in after.colourways.keys - before.colourways.keys) lines.add("Saved colourway: $name")

        return ChangeSummary(description, m0, m1, lines, notes)
    }

    private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)
}
