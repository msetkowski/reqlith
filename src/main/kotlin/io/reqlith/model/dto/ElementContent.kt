package io.reqlith.model.dto

import io.reqlith.model.entity.Priority
import io.reqlith.model.entity.TraceLink

data class ElementContent(val title: String, val bodyMd: String?, val priority: Priority?, val tags: Set<String>, val links: Set<TraceLink>)
