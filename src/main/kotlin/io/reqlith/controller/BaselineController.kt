package io.reqlith.controller

import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.entity.BaselineCategory
import io.reqlith.service.BaselineService
import io.reqlith.service.MarkdownRenderer
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

/** The non-functional requirements baseline: everyone reads it, architects maintain it. */
@Controller
class BaselineController(private val baseline: BaselineService, private val markdown: MarkdownRenderer) {

    @GetMapping("/baseline")
    fun list(@AuthenticationPrincipal user: CurrentUser, model: Model): String {
        model.addAttribute("baseline", baseline.all().groupBy { it.category })
        model.addAttribute("categories", BaselineCategory.entries)
        model.addAttribute("canEdit", baseline.canEdit(user))
        return "baseline/list"
    }

    @PostMapping("/baseline")
    fun create(
        @AuthenticationPrincipal user: CurrentUser,
        @RequestParam category: BaselineCategory,
        @RequestParam title: String,
        @RequestParam(required = false) body: String?,
        model: Model,
    ): String = try {
        "redirect:/baseline/${baseline.create(user, category, title, body).key}"
    } catch (e: ValidationException) {
        model.addAttribute("error", e)
        model.addAttribute("newCategory", category)
        model.addAttribute("newTitle", title)
        model.addAttribute("newBody", body)
        list(user, model)
    }

    @GetMapping("/baseline/{key}")
    fun show(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, model: Model): String {
        val requirement = baseline.get(key)
        model.addAttribute("requirement", requirement)
        model.addAttribute("bodyHtml", markdown.render(requirement.bodyMd))
        model.addAttribute("categories", BaselineCategory.entries)
        model.addAttribute("canEdit", baseline.canEdit(user))
        return "baseline/show"
    }

    @PostMapping("/baseline/{key}")
    fun update(
        @AuthenticationPrincipal user: CurrentUser,
        @PathVariable key: String,
        @RequestParam category: BaselineCategory,
        @RequestParam title: String,
        @RequestParam(required = false) body: String?,
        model: Model,
    ): String = try {
        baseline.update(user, baseline.get(key), category, title, body)
        "redirect:/baseline/$key"
    } catch (e: ValidationException) {
        model.addAttribute("error", e)
        show(user, key, model)
    }

    @PostMapping("/baseline/{key}/delete")
    fun delete(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String): String {
        baseline.delete(user, baseline.get(key))
        return "redirect:/baseline"
    }
}
