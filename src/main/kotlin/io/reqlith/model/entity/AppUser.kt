package io.reqlith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

/** A person known from the IDP, provisioned on first login. */
@Entity
@Table(name = "app_user")
class AppUser(
    @Column(nullable = false, updatable = false)
    val subject: String,
    var email: String?,
    @Column(name = "display_name", nullable = false)
    var displayName: String,
    @Column(nullable = false)
    var initials: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,
) : AbstractEntity() {

    companion object {
        /** "Olive Owens" -> "OO", "olive" -> "OL". */
        fun initialsOf(name: String): String {
            val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val letters = when {
                parts.isEmpty() -> "?"
                parts.size == 1 -> parts[0].take(2)
                else -> "${parts.first().first()}${parts.last().first()}"
            }
            return letters.uppercase()
        }
    }
}
