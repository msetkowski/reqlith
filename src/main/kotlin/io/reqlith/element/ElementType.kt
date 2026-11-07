package io.reqlith.element

/** BABOK-based element types. The name doubles as the key prefix (FR-12). Declaration order is document order (roles go before scenarios). */
enum class ElementType(val label: String, val section: String) {
    BR("Business requirement", "Business requirements"),
    SR("Stakeholder requirement", "Stakeholder requirements"),
    FR("Solution requirement · Functional", "Solution · functional"),
    NFR("Solution requirement · Non-functional", "Solution · non-functional"),
    AS("Assumption", "Assumptions"),
    RK("Risk", "Risks"),
    SC("Scenario", "Scenarios"),
    DP("Dependency", "Dependencies"),
    ;

    /** Short label for tables ("Functional", "Risk"). */
    val shortLabel: String get() = label.substringAfter(" · ").substringBefore(" requirement")

    companion object {
        fun ofKey(key: String): ElementType? = entries.firstOrNull { key.startsWith(it.name + "-") }
    }
}

enum class Priority(val label: String) {
    MUST("Must"),
    SHOULD("Should"),
    COULD("Could"),
    WONT("Won't"),
}

enum class LinkKind(val label: String, val inverseLabel: String) {
    DERIVED_FROM("Derived from", "Refined by"),
    VERIFIED_BY("Verified by", "Verifies"),
    DEPENDS_ON("Depends on", "Required by"),
    MITIGATES("Mitigates", "Mitigated by"),
}
