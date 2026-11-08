package io.reqlith.config

import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class RequiredSettingsTest {
    private val check = RequiredSettings()

    private fun environment(values: Map<String, Any>) = StandardEnvironment().apply {
        propertySources.addFirst(MapPropertySource("test", values))
    }

    private val complete = RequiredSettings.REQUIRED.associate { (property, _) -> property to "value" }

    @Test
    fun `all missing settings are reported at once, with their environment variables`() {
        val values = complete - "spring.datasource.password" - "reqlith.base-url" + ("reqlith.pdf.url" to "  ")
        val e = assertFailsWith<IllegalStateException> { check.postProcessEnvironment(environment(values), SpringApplication()) }
        assertContains(e.message!!, "REQLITH_DB_PASSWORD (spring.datasource.password)")
        assertContains(e.message!!, "REQLITH_BASE_URL (reqlith.base-url)")
        assertContains(e.message!!, "REQLITH_PDF_URL (reqlith.pdf.url)")
        assertContains(e.message!!, "'local' profile")
        assertFalse(e.message!!.contains("REQLITH_IDP_ISSUER"))
    }

    @Test
    fun `unresolvable placeholders count as missing`() {
        val values = complete + ("spring.datasource.password" to "\${REQLITH_TEST_UNSET_VARIABLE}")
        val e = assertFailsWith<IllegalStateException> { check.postProcessEnvironment(environment(values), SpringApplication()) }
        assertContains(e.message!!, "REQLITH_DB_PASSWORD")
    }

    @Test
    fun `a complete configuration starts`() {
        check.postProcessEnvironment(environment(complete), SpringApplication())
    }
}
