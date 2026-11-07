package io.reqlith.support

import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.dto.DocElement
import io.reqlith.model.dto.DocProject
import io.reqlith.model.dto.ProjectDocument
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.Project
import io.reqlith.model.entity.Workspace
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Plain objects for unit tests of services (no Spring context, no database). */
object UnitFixtures {
    val NOW: Instant = Instant.parse("2026-11-07T10:00:00Z")
    val clock: Clock = Clock.fixed(NOW, ZoneOffset.UTC)

    data class TestUser(
        override val userId: UUID = UUID.randomUUID(),
        override val displayName: String = "Test User",
        override val initials: String = "TU",
        override val emailAddress: String? = "test.user@reqlith.test",
        override val isAdmin: Boolean = false,
    ) : CurrentUser

    fun user(name: String = "Test User", email: String? = null, admin: Boolean = false) =
        TestUser(displayName = name, emailAddress = email ?: name.lowercase().replace(' ', '.') + "@reqlith.test", isAdmin = admin)

    val workspace = Workspace("PAY", "Payments", NOW)

    fun project(owner: CurrentUser = user(), key: String = "PRJ-PAY-001") =
        Project(key, workspace, "Self-service refunds", "Summary", "## Problem statement", NOW, owner.userId, NOW, owner.userId)

    fun document(project: Project, vararg elements: DocElement, basicInfo: String = "## Problem statement") = ProjectDocument(
        project = DocProject(project.key, project.title, project.summary, workspace.name, emptyList(), emptyList()),
        basicInfo = basicInfo,
        elements = elements.toList(),
        roles = emptyList(),
    )

    fun element(key: String, body: String? = "Body", title: String = key) =
        DocElement(key, ElementType.ofKey(key)!!, title, body, null, emptyList(), emptyList(), key.substringAfter('-').toInt())
}
