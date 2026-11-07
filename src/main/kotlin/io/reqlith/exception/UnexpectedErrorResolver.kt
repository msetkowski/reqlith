package io.reqlith.exception

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerExceptionResolver
import org.springframework.web.servlet.ModelAndView

private val logger = KotlinLogging.logger {}

/**
 * Last resort for exceptions nobody else handled: business exceptions are handled by [ErrorAdvice] and
 * Spring MVC's own 4xx cases by its default resolvers, which all run before this one. What arrives here
 * is a defect: logged at ERROR with an error id that the error page shows, so users can report it.
 */
@Component
class UnexpectedErrorResolver : HandlerExceptionResolver, Ordered {
    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    override fun resolveException(request: HttpServletRequest, response: HttpServletResponse, handler: Any?, ex: Exception): ModelAndView? {
        if (response.isCommitted) return null
        val errorId = UUID.randomUUID().toString().take(8)
        logger.error(ex) { "Unexpected error id=$errorId on ${request.method} ${request.requestURI}" }
        response.status = HttpStatus.INTERNAL_SERVER_ERROR.value()
        return ModelAndView(
            "error",
            mapOf(
                "status" to 500,
                "title" to "Something went wrong",
                "message" to "Please try again. If it keeps happening, report error id $errorId.",
            ),
            HttpStatus.INTERNAL_SERVER_ERROR,
        )
    }
}
