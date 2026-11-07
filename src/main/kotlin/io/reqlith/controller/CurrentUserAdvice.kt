package io.reqlith.controller

import io.reqlith.model.dto.CurrentUser
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/** Exposes the signed-in user to every view as `currentUser`. */
@ControllerAdvice
class CurrentUserAdvice {
    @ModelAttribute("currentUser")
    fun currentUser(): CurrentUser? = SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
}
