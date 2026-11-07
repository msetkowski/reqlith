package io.reqlith.db

import io.reqlith.support.IntegrationTest
import io.reqlith.user.UserService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The database itself refuses to change approved versions or any snapshot, whatever the application does. */
@IntegrationTest
class VersionImmutabilityIT(
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val userService: UserService,
) {
    @Test
    fun `approved version cannot be updated or deleted`() {
        val versionId = insertVersion(status = "APPROVED", versionNo = 1)

        val update = assertFailsWith<Exception> {
            jdbc.sql("update project_version set closed_at = sysdatetimeoffset() where id = ?").param(versionId).update()
        }
        assertContains(rootMessage(update), "immutable")
        val delete = assertFailsWith<Exception> {
            jdbc.sql("delete from project_version where id = ?").param(versionId).update()
        }
        assertContains(rootMessage(delete), "immutable")
    }

    @Test
    fun `candidate status may change but its snapshot may not`() {
        val versionId = insertVersion(status = "IN_REVIEW", versionNo = null)

        val changed = jdbc.sql("update project_version set status = 'WITHDRAWN', closed_at = sysdatetimeoffset() where id = ?")
            .param(versionId).update()
        assertEquals(1, changed)

        val tamper = assertFailsWith<Exception> {
            jdbc.sql("""update project_version set snapshot = '{"tampered":true}' where id = ?""").param(versionId).update()
        }
        assertContains(rootMessage(tamper), "never be modified")
    }

    private fun insertVersion(status: String, versionNo: Int?): UUID {
        val user = userService.provision("sub-" + UUID.randomUUID(), null, "Test User")
        val workspaceId = jdbc.sql("select id from workspace where ws_key = 'GEN'").query(UUID::class.java).single()
        val projectId = UUID.randomUUID()
        jdbc.sql(
            """
            insert into project (id, project_key, workspace_id, title, created_at, created_by, updated_at, updated_by)
            values (?, ?, ?, 'Immutable test', sysdatetimeoffset(), ?, sysdatetimeoffset(), ?)
            """,
        ).params(projectId, "PRJ-T-" + projectId.toString().take(8), workspaceId, user.id, user.id).update()
        val versionId = UUID.randomUUID()
        jdbc.sql(
            """
            insert into project_version (id, project_id, version_no, candidate_no, target_version, status, snapshot,
                                         schema_version, content_hash, submitted_by, submitted_at)
            values (?, ?, ?, 1, 1, ?, '{"schemaVersion":1}', 1, replicate('0', 64), ?, sysdatetimeoffset())
            """,
        ).param(versionId).param(projectId).param(versionNo).param(status).param(user.id).update()
        return versionId
    }

    private fun rootMessage(e: Throwable): String = generateSequence(e) { it.cause }.last().message.orEmpty()
}
