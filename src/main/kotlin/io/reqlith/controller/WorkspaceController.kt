package io.reqlith.controller

import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.CurrentUser
import io.reqlith.service.WorkspaceService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class WorkspaceController(private val workspaces: WorkspaceService) {

    @GetMapping("/workspaces")
    fun list(model: Model): String {
        model.addAttribute("workspaces", workspaces.all())
        return "workspace/list"
    }

    @PostMapping("/workspaces")
    fun create(@AuthenticationPrincipal user: CurrentUser, @RequestParam key: String, @RequestParam name: String, model: Model): String =
        try {
            workspaces.create(user, key, name)
            "redirect:/workspaces"
        } catch (e: ValidationException) {
            model.addAttribute("workspaces", workspaces.all())
            model.addAttribute("error", e)
            model.addAttribute("newKey", key)
            model.addAttribute("newName", name)
            "workspace/list"
        }
}
