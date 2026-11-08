package io.reqlith.model.dto

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
