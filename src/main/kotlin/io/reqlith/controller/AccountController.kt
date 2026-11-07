package io.reqlith.controller

import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.CurrentUser
import io.reqlith.repository.McpAccessLog
import io.reqlith.service.AccessTokenService
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** Personal access tokens for agents using the MCP server, and their recent use. */
@Controller
class AccountController(
    private val tokens: AccessTokenService,
    private val accessLog: McpAccessLog,
    private val formats: Formats,
) {
    @GetMapping("/account/tokens")
    fun tokens(@AuthenticationPrincipal user: CurrentUser, model: Model): String {
        model.addAttribute("tokens", tokens.forUser(user))
        model.addAttribute("mcpUrl", formats.absolute("/mcp"))
        model.addAttribute("recent", accessLog.recent(userId = user.userId))
        return "account/tokens"
    }

    @PostMapping("/account/tokens")
    fun create(@AuthenticationPrincipal user: CurrentUser, @RequestParam name: String, redirect: RedirectAttributes): String {
        try {
            val created = tokens.create(user, name)
            redirect.addFlashAttribute("newToken", created.value)
            redirect.addFlashAttribute("newTokenName", created.token.name)
        } catch (e: ValidationException) {
            redirect.addFlashAttribute("flashError", e.message)
        }
        return "redirect:/account/tokens"
    }

    @PostMapping("/account/tokens/{id}/revoke")
    fun revoke(@AuthenticationPrincipal user: CurrentUser, @PathVariable id: UUID): String {
        tokens.revoke(user, id)
        return "redirect:/account/tokens"
    }
}
