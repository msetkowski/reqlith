package io.reqlith.repository

import io.reqlith.model.dto.ElementContent
import io.reqlith.model.entity.Decision
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.LinkKind
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.TraceLink
import io.reqlith.service.ElementService
import io.reqlith.service.ProjectService
import io.reqlith.service.ReviewService
import io.reqlith.service.VersionService
import io.reqlith.support.Fixtures
import io.reqlith.support.IntegrationTest
import io.reqlith.support.TestUsers
import jakarta.persistence.EntityManagerFactory
import org.hibernate.Hibernate
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Guards against query regressions: N+1 selects on element collections and snapshots loaded with version lists. */
@IntegrationTest
@AutoConfigureMockMvc
class QueryEfficiencyIT(
    @Autowired private val mvc: MockMvc,
    @Autowired private val users: TestUsers,
    @Autowired private val fixtures: Fixtures,
    @Autowired private val projects: ProjectService,
    @Autowired private val elements: ElementService,
    @Autowired private val reviews: ReviewService,
    @Autowired private val versions: VersionService,
    @Autowired entityManagerFactory: EntityManagerFactory,
) {
    private val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics

    @Test
    fun `the project overview needs about the same number of queries for 5 and 40 elements`() {
        val owner = users.create()
        fun projectWith(count: Int): String {
            val project = fixtures.project(owner)
            repeat(count) { i ->
                elements.create(
                    owner,
                    projects.get(project.key),
                    ElementType.FR,
                    ElementContent(
                        "Requirement $i",
                        "Body $i",
                        null,
                        setOf("tag-$i", "ux"),
                        if (i > 0) setOf(TraceLink(LinkKind.DEPENDS_ON, "FR-%02d".format(i))) else emptySet(),
                    ),
                )
            }
            return project.key
        }
        fun queriesFor(key: String): Long {
            statistics.clear()
            mvc.get("/p/$key/draft") { with(oidcLogin().oidcUser(owner)) }.andExpect { status { isOk() } }
            return statistics.prepareStatementCount
        }
        val small = queriesFor(projectWith(5))
        val large = queriesFor(projectWith(40))

        assertTrue(large <= small + 6, "overview queries grew with the elements: 5 elements -> $small, 40 elements -> $large")
    }

    @Test
    fun `version lists do not load snapshots`() {
        val owner = users.create("Olive Owens")
        val reviewer = users.create("Rita Reyes")
        val project = fixtures.project(owner)
        fixtures.addMember(project, reviewer, MemberRole.REVIEWER)
        elements.create(owner, project, ElementType.BR, ElementContent("Goal", "x".repeat(50_000), null, emptySet(), emptySet()))
        reviews.submit(owner, project, listOf(reviewer.userId))
        reviews.decide(reviewer, projects.get(project.key), Decision.ACCEPT, null)

        val listed = versions.history(projects.get(project.key)).single()
        assertFalse(Hibernate.isPropertyInitialized(listed, "snapshot"), "history must not read the snapshot column")
        assertTrue(versions.document(listed).elements.single().body!!.length == 50_000, "documents still load on demand")
    }
}
