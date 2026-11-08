package io.reqlith.controller

import io.reqlith.config.ReqlithProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** OAuth 2.0 Protected Resource Metadata (RFC 9728): tells MCP clients where to get tokens for `/mcp`. */
@RestController
class ProtectedResourceMetadata(private val properties: ReqlithProperties) {

    @GetMapping("/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp")
    fun metadata(): Map<String, Any> = mapOf(
        "resource" to properties.baseUrl.trimEnd('/') + "/mcp",
        "authorization_servers" to listOf(properties.idp.issuer),
        "bearer_methods_supported" to listOf("header"),
        "scopes_supported" to listOf("openid", "profile", "email"),
        "resource_name" to "Reqlith requirements (read-only)",
    )
}
