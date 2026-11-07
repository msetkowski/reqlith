package io.reqlith.service

/** Reading user details from ID or access token claims, across identity providers. */
object IdpClaims {
    /**
     * The e-mail address: `email`, or (Entra ID, where `email` is an optional claim) `preferred_username` or
     * `upn` when it is an address. Invitations are matched by this address.
     */
    fun email(claims: Map<String, Any?>): String? =
        sequenceOf("email", "preferred_username", "upn")
            .mapNotNull { claims[it] as? String }
            .firstOrNull { EMAIL.matches(it) }

    /** Display name: `name`, else given and family name, else `preferred_username`. */
    fun name(claims: Map<String, Any?>): String? =
        (claims["name"] as? String)?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(claims["given_name"] as? String, claims["family_name"] as? String).joinToString(" ").ifBlank { null }
            ?: claims["preferred_username"] as? String

    private val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
}
