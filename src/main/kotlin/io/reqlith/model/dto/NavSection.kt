package io.reqlith.model.dto

/** One entry of the project side navigation. */
data class NavSection(val label: String, val count: Int?, val href: String, val id: String)
