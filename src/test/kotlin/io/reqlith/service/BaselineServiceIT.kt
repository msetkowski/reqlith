package io.reqlith.service

import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.ElementContent
import io.reqlith.model.entity.BaselineCategory
import io.reqlith.model.entity.Decision
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.MemberRole
import io.reqlith.support.Fixtures
import io.reqlith.support.IntegrationTest
import io.reqlith.support.TestUsers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@IntegrationTest
class BaselineServiceIT(
    @Autowired private val baseline: BaselineService,
    @Autowired private val projects: ProjectService,
    @Autowired private val elements: ElementService,
    @Autowired private val reviews: ReviewService,
    @Autowired private val versions: VersionService,
    @Autowired private val users: TestUsers,
    @Autowired private val fixtures: Fixtures,
) {
    @Test
    fun `architects maintain the baseline, keys are never reused`() {
        val architect = users.create("Ada Archer", architect = true)
        val first = baseline.create(architect, BaselineCategory.SECURITY, " Encrypt data at rest ", "AES-256\r\n")
        assertTrue(first.key.matches(Regex("BL-\\d{2,}")))
        assertEquals("Encrypt data at rest", baseline.get(first.key).title)
        assertEquals("AES-256\n", baseline.get(first.key).bodyMd)

        baseline.update(architect, baseline.get(first.key), BaselineCategory.COMPLIANCE, "Encrypt personal data", " ")
        val changed = baseline.get(first.key)
        assertEquals(BaselineCategory.COMPLIANCE, changed.category)
        assertEquals(null, changed.bodyMd)

        baseline.delete(architect, changed)
        assertFalse(baseline.all().any { it.key == first.key })
        val second = baseline.create(architect, BaselineCategory.PERFORMANCE, "Fast pages", null)
        assertNotEquals(first.key, second.key)
        assertFailsWith<ValidationException> { baseline.create(architect, BaselineCategory.OTHER, "  ", null) }
    }

    @Test
    fun `only architects change the baseline`() {
        val architect = users.create("Ada Archer", architect = true)
        val owner = users.create("Olive Owens")
        fixtures.project(owner)
        val requirement = baseline.create(architect, BaselineCategory.AVAILABILITY, "99.9% uptime", null)

        assertFalse(baseline.canEdit(owner))
        assertFailsWith<AccessDeniedException> { baseline.create(owner, BaselineCategory.OTHER, "Mine", null) }
        assertFailsWith<AccessDeniedException> { baseline.update(owner, requirement, BaselineCategory.OTHER, "Mine", null) }
        assertFailsWith<AccessDeniedException> { baseline.delete(owner, requirement) }
    }

    @Test
    fun `adding or removing baseline requirements leaves an approved project approved`() {
        val architect = users.create("Ada Archer", architect = true)
        val owner = users.create("Olive Owens")
        val reviewer = users.create("Rita Reyes")
        val project = fixtures.project(owner)
        fixtures.addMember(project, reviewer, MemberRole.REVIEWER)
        elements.create(owner, project, ElementType.NFR, ElementContent("Audit log", "Every change", null, emptySet(), emptySet()))
        reviews.submit(owner, project, listOf(reviewer.userId))
        reviews.decide(reviewer, projects.get(project.key), Decision.ACCEPT, null)
        val approved = projects.get(project.key)
        assertFalse(versions.hasChangesSinceApproval(approved))
        val fingerprint = baseline.fingerprint()

        val added = baseline.create(architect, BaselineCategory.USABILITY, "Works on mobile", "Down to 360 px")
        assertFalse(versions.hasChangesSinceApproval(projects.get(project.key)))
        assertNotEquals(fingerprint, baseline.fingerprint())

        baseline.delete(architect, added)
        val after = projects.get(project.key)
        assertFalse(versions.hasChangesSinceApproval(after))
        assertEquals(approved.updatedAt, after.updatedAt)
    }
}
