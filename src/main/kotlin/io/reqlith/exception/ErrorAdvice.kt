package io.reqlith.exception

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.ModelAndView

/** Renders business exceptions as a friendly error page; their messages are written for end users. */
@ControllerAdvice
class ErrorAdvice {

    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: Exception, response: HttpServletResponse) = page(response, HttpStatus.NOT_FOUND, "Not found", e)

    @ExceptionHandler(ProjectLockedException::class)
    fun locked(e: Exception, response: HttpServletResponse) = page(response, HttpStatus.CONFLICT, "Project is in review", e)

    @ExceptionHandler(ConcurrentEditException::class)
    fun conflict(e: Exception, response: HttpServletResponse) = page(response, HttpStatus.CONFLICT, "Changed in the meantime", e)

    @ExceptionHandler(AccessDeniedException::class)
    fun denied(e: Exception, response: HttpServletResponse) = page(response, HttpStatus.FORBIDDEN, "Not allowed", e)

    @ExceptionHandler(PdfRenderingException::class)
    fun pdf(e: Exception, response: HttpServletResponse) = page(response, HttpStatus.SERVICE_UNAVAILABLE, "PDF export is unavailable", e)

    @ExceptionHandler(ValidationException::class)
    fun invalid(e: Exception, response: HttpServletResponse) = page(response, HttpStatus.BAD_REQUEST, "Cannot do that", e)

    private fun page(response: HttpServletResponse, status: HttpStatus, title: String, e: Exception): ModelAndView {
        response.status = status.value()
        return ModelAndView("error", mapOf("status" to status.value(), "title" to title, "message" to e.message), status)
    }
}
