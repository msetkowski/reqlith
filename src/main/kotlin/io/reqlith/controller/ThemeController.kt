package io.reqlith.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.Duration

/** The colour theme each user picks for themselves; kept in a cookie, so it needs no account storage. */
@Controller
class ThemeController {
    /** Stores "light" or "dark"; anything else ("system") removes the cookie so the OS setting applies. */
    @PostMapping("/account/theme")
    fun choose(@RequestParam theme: String, @RequestParam(required = false) back: String?, request: HttpServletRequest, response: HttpServletResponse): String {
        val value = theme.takeIf { it in THEMES }
        val cookie = ResponseCookie.from(COOKIE, value.orEmpty())
            .path("/")
            .maxAge(if (value == null) Duration.ZERO else Duration.ofDays(365))
            .sameSite("Lax")
            .secure(request.isSecure)
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
        return "redirect:" + (back?.takeIf { it.startsWith("/") && !it.startsWith("//") && !it.startsWith("/\\") } ?: "/")
    }

    companion object {
        const val COOKIE = "reqlith-theme"
        val THEMES = setOf("light", "dark")
    }
}

/** Exposes the chosen theme (null: follow the OS) and the current path (for returning after a switch) to every view. */
@ControllerAdvice
class ThemeAdvice {
    @ModelAttribute("theme")
    fun theme(@CookieValue(ThemeController.COOKIE, required = false) theme: String?): String? = theme?.takeIf { it in ThemeController.THEMES }

    @ModelAttribute("currentPath")
    fun currentPath(request: HttpServletRequest): String = request.requestURI + (request.queryString?.let { "?$it" }.orEmpty())
}
