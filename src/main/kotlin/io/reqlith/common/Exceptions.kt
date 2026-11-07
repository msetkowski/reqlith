package io.reqlith.common

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/** User input that breaks a business rule; shown next to [field] in forms. */
class ValidationException(val field: String?, message: String) : RuntimeException(message)

@ResponseStatus(HttpStatus.NOT_FOUND)
class NotFoundException(message: String) : RuntimeException(message)

/** The working copy cannot change while a review is open. */
@ResponseStatus(HttpStatus.CONFLICT)
class ProjectLockedException(projectKey: String) :
    RuntimeException("$projectKey is in review. Withdraw the review to make changes.")

/** Someone else saved the same item since the form was opened. */
@ResponseStatus(HttpStatus.CONFLICT)
class ConcurrentEditException(what: String) :
    RuntimeException("$what was changed by someone else in the meantime. Reload to see the latest version.")
