package io.reqlith.model.entity

enum class LinkKind(val label: String, val inverseLabel: String) {
    DERIVED_FROM("Derived from", "Refined by"),
    VERIFIED_BY("Verified by", "Verifies"),
    DEPENDS_ON("Depends on", "Required by"),
    MITIGATES("Mitigates", "Mitigated by"),
}
