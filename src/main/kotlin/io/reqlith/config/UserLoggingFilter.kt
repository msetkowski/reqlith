package io.reqlith.config

import io.reqlith.model.dto.CurrentUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/** Puts the signed-in user's id into the logging context (MDC). Runs after Spring Security has resolved the user. */
class UserLoggingFilter : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val user = SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
        MDC.put(USER, user?.userId?.toString() ?: "-")
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(USER)
        }
    }

    companion object {
        const val USER = "user"
    }
}
