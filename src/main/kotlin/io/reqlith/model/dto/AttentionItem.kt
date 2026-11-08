package io.reqlith.model.dto

import java.util.UUID

/** A card in "Needs your attention". */
data class AttentionItem(val badge: String, val badgeClass: String, val title: String, val detail: String, val href: String?, val invitationId: UUID?)
