package io.reqlith.model.dto

data class ProjectDetails(
    val title: String,
    val summary: String?,
    val tags: Set<String>,
    val domains: Set<String>,
)
