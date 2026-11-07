package io.reqlith.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Puts a request id into the logging context (MDC) so all log lines of a request can be correlated. Taken from
 * `X-Request-Id` when a proxy sets one, and returned in the response. Runs before Spring Security, so lines
 * logged during authentication (e.g. sign-in) carry it too. The user is added later by [UserLoggingFilter].
 */
class RequestLoggingFilter : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val requestId = request.getHeader(HEADER)?.takeIf { VALID_ID.matches(it) } ?: UUID.randomUUID().toString().take(8)
        MDC.put(REQUEST_ID, requestId)
        response.setHeader(HEADER, requestId)
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(REQUEST_ID)
        }
    }

    companion object {
        const val HEADER = "X-Request-Id"
        const val REQUEST_ID = "requestId"
        private val VALID_ID = Regex("[A-Za-z0-9._-]{1,64}")
    }
}
