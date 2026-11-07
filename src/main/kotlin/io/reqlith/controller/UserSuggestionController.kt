package io.reqlith.controller

import io.reqlith.model.dto.CurrentUser
import io.reqlith.service.ProjectService
import io.reqlith.service.UserSuggestionService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

/** `<option>` elements for the invitation e-mail fields (HTMX fills a `<datalist>` while typing). */
@Controller
class UserSuggestionController(private val suggestions: UserSuggestionService, private val projects: ProjectService) {
    @GetMapping("/users/suggest")
    fun suggest(
        @AuthenticationPrincipal user: CurrentUser,
        @RequestParam(defaultValue = "") q: String,
        @RequestParam(required = false) project: String?,
        model: Model,
    ): String {
        model.addAttribute("people", suggestions.suggest(user, q, project?.let(projects::get)))
        return "fragments/suggestions :: options"
    }
}
