package io.reqlith.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/** The working copy cannot change while a review is open. */
@ResponseStatus(HttpStatus.CONFLICT)
class ProjectLockedException(projectKey: String) :
    RuntimeException("$projectKey is in review. Withdraw the review to make changes.")
