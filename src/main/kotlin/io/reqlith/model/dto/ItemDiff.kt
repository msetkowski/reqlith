package io.reqlith.model.dto


/** How one element (or role) differs between two documents. */
data class ItemDiff(
    val key: String,
    val title: String,
    val typeLabel: String,
    val kind: ChangeKind,
    val titleHtml: String,
    val titleChanged: Boolean,
    /** Body diff with context; null entries mark skipped unchanged lines. */
    val lines: List<DiffLine?>,
    /** Property changes such as "Priority: Should → Must". */
    val properties: List<String>,
    val oldBody: String?,
    val newBody: String?,
)
