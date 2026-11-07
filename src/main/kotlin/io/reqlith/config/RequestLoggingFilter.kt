package io.reqlith.config

import io.reqlith.model.dto.CurrentUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Puts a request id and the signed-in user's id into the logging context (MDC), so every log line of a
 * request can be correlated and attributed. The request id is taken from `X-Request-Id` when a proxy sets
 * one and returned in the response. Runs after Spring Security, which has resolved the user by then.
 */
class RequestLoggingFilter : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val requestId = request.getHeader(HEADER)?.takeIf { VALID_ID.matches(it) } ?: UUID.randomUUID().toString().take(8)
        val user = SecurityContextHolder.getContext().authentication?.principal as? CurrentUser
        MDC.put(REQUEST_ID, requestId)
        MDC.put(USER, user?.userId?.toString() ?: "-")
        response.setHeader(HEADER, requestId)
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(REQUEST_ID)
            MDC.remove(USER)
        }
    }

    companion object {
        const val HEADER = "X-Request-Id"
        const val REQUEST_ID = "requestId"
        const val USER = "user"
        private val VALID_ID = Regex("[A-Za-z0-9._-]{1,64}")
    }
}
