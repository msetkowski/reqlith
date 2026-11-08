package io.reqlith.model.entity

enum class MemberRole(val label: String) {
    OWNER("Owner"),
    CONTRIBUTOR("Contributor"),
    REVIEWER("Reviewer"),
}
