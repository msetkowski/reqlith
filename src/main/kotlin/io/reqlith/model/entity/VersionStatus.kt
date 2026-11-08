package io.reqlith.model.entity

enum class VersionStatus(val label: String) {
    IN_REVIEW("In review"),
    APPROVED("Approved"),
    CHANGES_REQUESTED("Changes requested"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn"),
}
