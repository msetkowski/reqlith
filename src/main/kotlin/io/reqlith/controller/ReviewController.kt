package io.reqlith.controller

import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.ChangeKind
import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.dto.ProjectDocument
import io.reqlith.model.dto.RenderedDocument
import io.reqlith.model.entity.Decision
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.Project
import io.reqlith.model.entity.Suggestion
import io.reqlith.service.DocumentChanges
import io.reqlith.service.MembershipService
import io.reqlith.service.ProjectDiff
import io.reqlith.service.ProjectDocumentService
import io.reqlith.service.ProjectService
import io.reqlith.service.ReviewService
import io.reqlith.service.UserService
import io.reqlith.service.VersionService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.UUID

/**
 * The review screen. While a round is open it shows the frozen candidate (by default only what changed
 * since the approved version it is based on); otherwise it offers submitting and handling earlier feedback.
 */
@Controller
class ReviewController(
    private val projects: ProjectService,
    private val reviews: ReviewService,
    private val versions: VersionService,
    private val documents: ProjectDocumentService,
    private val memberships: MembershipService,
    private val users: UserService,
    private val support: ProjectViewSupport,
) {
    @GetMapping("/p/{key}/review")
    fun review(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, @RequestParam(required = false) full: Boolean?, model: Model): String {
        val project = projects.get(key)
        val workingCopy = documents.fromWorkingCopy(project)
        support.populate(model, project, user, workingCopy, active = "review")
        model.addAttribute("types", ElementType.entries)
        val candidate = versions.openCandidate(project)
        if (candidate == null) {
            populateSubmit(model, project, user, workingCopy)
            return "review/submit"
        }

        val document = versions.document(candidate)
        val basedOn = candidate.basedOnVersion?.let { versions.approvedDocument(project, it) }
        val changes = DocumentChanges(basedOn, document)
        val changesOnly = basedOn != null && full != true
        val assignments = reviews.assignments(candidate)
        val suggestions = reviews.suggestions(candidate)
        val comments = reviews.comments(candidate)
        val names = users.byIds(assignments.map { it.reviewerId } + suggestions.map { it.authorId } + comments.map { it.authorId } + candidate.submittedBy)
        val mine = assignments.firstOrNull { it.reviewerId == user.userId }

        model.addAttribute("candidate", candidate)
        model.addAttribute("document", document)
        // A first version has nothing to compare against, so no element gets a "New" badge.
        model.addAttribute("changes", if (basedOn != null) changes else null)
        model.addAttribute("changeSummary", changes.summary)
        model.addAttribute("itemDiff", basedOn?.let { ProjectDiff(it, document) })
        model.addAttribute("changesOnly", changesOnly)
        model.addAttribute(
            "visibleKeys",
            if (changesOnly) {
                changes.elements.filterValues { it != ChangeKind.UNCHANGED }.keys +
                    changes.roles.filterValues { it != ChangeKind.UNCHANGED }.keys
            } else {
                null
            },
        )
        model.addAttribute("html", RenderedDocument.of(document) { support.renderInPage(it, document) })
        model.addAttribute("incoming", document.incomingLinks())
        model.addAttribute("assignments", assignments)
        model.addAttribute("mine", mine)
        model.addAttribute("canReview", mine != null)
        model.addAttribute("canDecide", mine?.decision == Decision.PENDING)
        model.addAttribute("suggestionsByKey", suggestions.groupBy { it.elementKey })
        model.addAttribute("mySuggestionCount", suggestions.count { it.authorId == user.userId })
        model.addAttribute("suggestionHtml", suggestions.associate { it.id to support.renderInPage(it.proposedMd, document) })
        model.addAttribute("commentsByKey", comments.filter { it.elementKey != null }.groupBy { it.elementKey!! })
        model.addAttribute("generalComments", comments.filter { it.elementKey == null })
        model.addAttribute("names", names)
        model.addAttribute("reviewBlock", "review/blocks")
        model.addAttribute(
            "bodies",
            document.elements.associate { it.key to it.body.orEmpty() } +
                (Suggestion.BASIC_INFO to document.basicInfo) + (Suggestion.SOLUTION_DESIGN to document.solutionDesign),
        )
        return "review/review"
    }

    private fun populateSubmit(model: Model, project: Project, user: CurrentUser, workingCopy: ProjectDocument) {
        val approved = project.latestApprovedVersion?.let { versions.approvedDocument(project, it) }
        val members = memberships.members(project).filter { it.userId != user.userId }
        val history = versions.history(project)
        val roundAssignments = history.associate { it.id to reviews.assignments(it) }
        val open = reviews.openSuggestions(project)
        val names = users.byIds(members.map { it.userId } + roundAssignments.values.flatten().map { it.reviewerId } + open.map { it.authorId })
        model.addAttribute("changes", DocumentChanges(approved, workingCopy))
        model.addAttribute("candidates", members.sortedWith(compareBy({ it.role != MemberRole.REVIEWER }, { names[it.userId]?.displayName })))
        model.addAttribute("history", history)
        model.addAttribute("roundAssignments", roundAssignments)
        model.addAttribute("openSuggestions", open)
        model.addAttribute("suggestionHtml", open.associate { it.id to support.render(it.proposedMd, project, workingCopy) })
        model.addAttribute("names", names)
    }

    @PostMapping("/p/{key}/review/submit")
    fun submit(
        @AuthenticationPrincipal user: CurrentUser,
        @PathVariable key: String,
        @RequestParam(required = false) reviewers: List<UUID>?,
        redirect: RedirectAttributes,
    ): String = guarded(key, redirect) { reviews.submit(user, projects.get(key), reviewers.orEmpty()) }

    @PostMapping("/p/{key}/review/decide")
    fun decide(
        @AuthenticationPrincipal user: CurrentUser,
        @PathVariable key: String,
        @RequestParam decision: Decision,
        @RequestParam(required = false) comment: String?,
        redirect: RedirectAttributes,
    ): String {
        val target = guarded(key, redirect) { reviews.decide(user, projects.get(key), decision, comment) }
        if (redirect.flashAttributes.containsKey("flashError")) redirect.addFlashAttribute("decisionComment", comment)
        return target
    }

    @PostMapping("/p/{key}/review/withdraw")
    fun withdraw(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, redirect: RedirectAttributes): String =
        guarded(key, redirect) { reviews.withdraw(user, projects.get(key)) }

    @PostMapping("/p/{key}/review/suggest")
    fun suggest(
        @AuthenticationPrincipal user: CurrentUser,
        @PathVariable key: String,
        @RequestParam elementKey: String,
        @RequestParam(required = false) proposedMd: String?,
        @RequestParam(required = false) comment: String?,
        redirect: RedirectAttributes,
    ): String = guarded(key, redirect, anchor = elementKey) { reviews.suggest(user, projects.get(key), elementKey, proposedMd, comment) }

    @PostMapping("/p/{key}/review/comment")
    fun comment(
        @AuthenticationPrincipal user: CurrentUser,
        @PathVariable key: String,
        @RequestParam(required = false) elementKey: String?,
        @RequestParam body: String,
        redirect: RedirectAttributes,
    ): String = guarded(key, redirect, anchor = elementKey ?: "discussion") { reviews.comment(user, projects.get(key), elementKey, body) }

    @PostMapping("/p/{key}/suggestions/{id}/{action}")
    fun resolve(
        @AuthenticationPrincipal user: CurrentUser,
        @PathVariable key: String,
        @PathVariable id: UUID,
        @PathVariable action: String,
        @RequestParam(required = false) back: String?,
        redirect: RedirectAttributes,
    ): String {
        val target = guarded(key, redirect) { reviews.resolve(user, projects.get(key), id, apply = action == "apply") }
        return if (back != null && back.startsWith("/p/$key/")) "redirect:$back" else target
    }

    private fun guarded(key: String, redirect: RedirectAttributes, anchor: String? = null, action: () -> Unit): String {
        try {
            action()
        } catch (e: ValidationException) {
            redirect.addFlashAttribute("flashError", e.message)
        }
        return "redirect:/p/$key/review" + (anchor?.let { "#$it" } ?: "")
    }
}
