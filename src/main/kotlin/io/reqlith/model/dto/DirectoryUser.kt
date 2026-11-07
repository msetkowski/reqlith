package io.reqlith.model.dto

/** A person who could be invited: known from earlier sign-ins or from the identity provider's directory. */
data class DirectoryUser(val name: String, val email: String)
