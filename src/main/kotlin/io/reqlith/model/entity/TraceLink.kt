package io.reqlith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

/** Outgoing traceability link; points at a key so it survives unchanged in snapshots. */
@Embeddable
data class TraceLink(
    @Enumerated(EnumType.STRING)
    @Column(name = "link_kind", nullable = false)
    val kind: LinkKind,
    @Column(name = "to_key", nullable = false)
    val toKey: String,
)
