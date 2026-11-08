package io.reqlith.exception

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.ModelAndView

private val logger = KotlinLogging.logger {}

/** Renders business exceptions as a friendly error page; their messages are written for end users. */
@ControllerAdvice
class ErrorAdvice {
    // Expected situations: the user gets a message, engineers a DEBUG hint.

    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: Exception, response: HttpServletResponse) = expected(response, HttpStatus.NOT_FOUND, "Not found", e)

    @ExceptionHandler(ProjectLockedException::class)
    fun locked(e: Exception, response: HttpServletResponse) = expected(response, HttpStatus.CONFLICT, "Project is in review", e)

    @ExceptionHandler(ConcurrentEditException::class)
    fun conflict(e: Exception, response: HttpServletResponse) = expected(response, HttpStatus.CONFLICT, "Changed in the meantime", e)

    @ExceptionHandler(ValidationException::class)
    fun invalid(e: Exception, response: HttpServletResponse) = expected(response, HttpStatus.BAD_REQUEST, "Cannot do that", e)

    /** Refused actions are relevant for auditing, so they are logged at INFO. */
    @ExceptionHandler(AccessDeniedException::class)
    fun denied(e: Exception, response: HttpServletResponse): ModelAndView {
        logger.info { "Access denied: ${e.message}" }
        return page(response, HttpStatus.FORBIDDEN, "Not allowed", e)
    }

    /** The rendering service is down or failing: an operational error. */
    @ExceptionHandler(PdfRenderingException::class)
    fun pdf(e: Exception, response: HttpServletResponse): ModelAndView {
        logger.error(e.cause) { "PDF export failed: ${e.message}" }
        return page(response, HttpStatus.SERVICE_UNAVAILABLE, "PDF export is unavailable", e)
    }

    private fun expected(response: HttpServletResponse, status: HttpStatus, title: String, e: Exception): ModelAndView {
        logger.debug { "$title (${status.value()}): ${e.message}" }
        return page(response, status, title, e)
    }

    private fun page(response: HttpServletResponse, status: HttpStatus, title: String, e: Exception): ModelAndView {
        response.status = status.value()
        return ModelAndView("error", mapOf("status" to status.value(), "title" to title, "message" to e.message), status)
    }
}
