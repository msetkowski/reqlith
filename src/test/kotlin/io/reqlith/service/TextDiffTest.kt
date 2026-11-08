package io.reqlith.service

import io.reqlith.model.dto.LineChange
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TextDiffTest {
    @Test
    fun `changed words are marked inside the line`() {
        val lines = TextDiff.lines("Refund within 14 days", "Refund within 30 days")
        assertEquals(LineChange.CHANGED, lines.single().change)
        assertContains(lines.single().html, "<del>14</del>")
        assertContains(lines.single().html, "<ins>30</ins>")
    }

    @Test
    fun `added and removed lines are detected`() {
        val lines = TextDiff.lines("a\nb\nc", "a\nc\nd")
        assertEquals(listOf(LineChange.EQUAL, LineChange.REMOVED, LineChange.EQUAL, LineChange.ADDED), lines.map { it.change })
    }

    @Test
    fun `html in markdown is escaped`() {
        val html = TextDiff.lines("<b>x</b>", "<script>alert(1)</script>").joinToString { it.html }
        assertFalse(html.contains("<script>"), html)
        assertFalse(html.contains("<b>"), html)
    }

    @Test
    fun `unchanged lines far from changes are collapsed`() {
        val before = (1..10).joinToString("\n") { "line $it" }
        val after = before.replace("line 9", "line nine")
        val shown = TextDiff.withContext(TextDiff.lines(before, after), context = 1)
        assertNull(shown.first(), "gap marker for lines 1-7")
        assertEquals(4, shown.size) // gap, line 8, changed line 9, line 10
    }
}
