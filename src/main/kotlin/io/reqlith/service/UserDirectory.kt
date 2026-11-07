package io.reqlith.service

import io.reqlith.model.dto.DirectoryUser

/** A source of people for invitation suggestions. */
fun interface UserDirectory {
    /** Up to [limit] people whose name or e-mail contains [text]. */
    fun search(text: String, limit: Int): List<DirectoryUser>
}
