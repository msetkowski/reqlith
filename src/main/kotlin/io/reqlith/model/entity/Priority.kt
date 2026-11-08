package io.reqlith.model.entity

enum class Priority(val label: String) {
    MUST("Must"),
    SHOULD("Should"),
    COULD("Could"),
    WONT("Won't"),
}
