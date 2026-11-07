package io.reqlith.diff

import com.github.difflib.text.DiffRow
import com.github.difflib.text.DiffRowGenerator

enum class LineChange { EQUAL, ADDED, REMOVED, CHANGED }

/**
 * One line of a markdown diff. [html] is escaped text with `<del>`/`<ins>` around changed words
 * (for [LineChange.CHANGED] both old and new words appear on the same line).
 */
data class DiffLine(val change: LineChange, val html: String) {
    val sign: String get() = when (change) {
        LineChange.ADDED -> "+"
        LineChange.REMOVED -> "−"
        LineChange.CHANGED -> "~"
        LineChange.EQUAL -> ""
    }
}

/** Line-based diff of markdown with word-level highlights inside changed lines. */
object TextDiff {

    private val generator: DiffRowGenerator = DiffRowGenerator.create()
        .showInlineDiffs(true)
        .inlineDiffByWord(true)
        .mergeOriginalRevised(true)
        .oldTag { start -> if (start) "<del>" else "</del>" }
        .newTag { start -> if (start) "<ins>" else "</ins>" }
        .build()

    fun lines(before: String?, after: String?): List<DiffLine> {
        val old = split(before)
        val new = split(after)
        if (old == new) return old.map { DiffLine(LineChange.EQUAL, escape(it)) }
        return generator.generateDiffRows(old, new).map { row ->
            when (row.tag) {
                DiffRow.Tag.INSERT -> DiffLine(LineChange.ADDED, row.oldLine)
                DiffRow.Tag.DELETE -> DiffLine(LineChange.REMOVED, row.oldLine)
                DiffRow.Tag.CHANGE -> DiffLine(LineChange.CHANGED, row.oldLine)
                else -> DiffLine(LineChange.EQUAL, row.oldLine)
            }
        }
    }

    /** Equal lines far from any change are dropped; null marks a gap ("…"). */
    fun withContext(lines: List<DiffLine>, context: Int = 2): List<DiffLine?> {
        val keep = BooleanArray(lines.size)
        lines.forEachIndexed { i, line ->
            if (line.change != LineChange.EQUAL) {
                for (j in (i - context).coerceAtLeast(0)..(i + context).coerceAtMost(lines.lastIndex)) keep[j] = true
            }
        }
        val result = mutableListOf<DiffLine?>()
        lines.forEachIndexed { i, line ->
            if (keep[i]) result += line else if (result.isEmpty() || result.last() != null) result += null
        }
        return if (result.lastOrNull() == null) result.dropLast(1) else result
    }

    /** Word-level diff of a single line of text (titles). */
    fun inline(before: String?, after: String?): String = lines(before, after).joinToString(" ") { it.html }

    private fun split(text: String?): List<String> = text.orEmpty().replace("\r\n", "\n").let { if (it.isEmpty()) emptyList() else it.split('\n') }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
