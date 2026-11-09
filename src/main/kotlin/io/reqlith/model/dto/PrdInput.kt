package io.reqlith.model.dto

data class ExportOptions(val traceabilityMatrix: Boolean = true, val approvalRecord: Boolean = true, val reviewComments: Boolean = false) {
    /** Stable cache key component. */
    val key: String get() = "t${traceabilityMatrix.bit()}a${approvalRecord.bit()}c${reviewComments.bit()}"

    private fun Boolean.bit() = if (this) 1 else 0
}

/** One row of the version and approval record. */
data class ApprovalRow(val version: String, val status: String, val submitted: String, val decisions: String)

/** One review comment or suggestion for the optional appendix. */
data class ReviewNote(val version: String, val target: String, val author: String, val text: String)

/** A baseline non-functional requirement as printed in the PRD. */
data class BaselineItem(val key: String, val category: String, val title: String, val body: String?)

data class PrdInput(
    val document: ProjectDocument,
    /** "v2", "v3-rc1" or "DRAFT". */
    val versionLabel: String,
    val status: String,
    val date: String,
    val contentHash: String?,
    val approvals: List<ApprovalRow>,
    val notes: List<ReviewNote>,
    /** The current global baseline; it is not versioned with the project. */
    val baseline: List<BaselineItem> = emptyList(),
)
