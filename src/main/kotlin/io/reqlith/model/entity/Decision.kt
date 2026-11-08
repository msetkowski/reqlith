package io.reqlith.model.entity

enum class Decision(val label: String) {
    PENDING("Pending"),
    ACCEPT("Accepted"),
    CHANGES("Changes requested"),
    REJECT("Rejected"),
}
