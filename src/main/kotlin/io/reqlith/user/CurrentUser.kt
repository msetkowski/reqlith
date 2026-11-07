package io.reqlith.user

import java.util.UUID

/** The signed-in person, whatever the authentication mechanism (browser session, JWT or access token). */
interface CurrentUser {
    val userId: UUID
    val displayName: String
    val initials: String
    val emailAddress: String?
    val isAdmin: Boolean
}
