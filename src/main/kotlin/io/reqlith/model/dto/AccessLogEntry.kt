package io.reqlith.model.dto

import java.time.Instant

data class AccessLogEntry(
    val occurredAt: Instant,
    val clientName: String?,
    val userName: String,
    val tokenName: String?,
    val tool: String,
    val projectKey: String?,
    val argsJson: String?,
)
