package io.reqlith.model.dto

import java.util.UUID

/** The signed-in person, whatever the authentication mechanism (browser session, JWT or access token). */
interface CurrentUser {
    val userId: UUID
    val displayName: String
    val initials: String
    val emailAddress: String?

    /** May maintain the non-functional requirements baseline (application role from the IDP, see `reqlith.idp.architect-roles`). */
    val isArchitect: Boolean get() = false

    companion object {
        const val ARCHITECT_AUTHORITY = "ROLE_ARCHITECT"
    }
}
