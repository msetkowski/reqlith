package io.reqlith.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/** Someone else saved the same item since the form was opened. */
@ResponseStatus(HttpStatus.CONFLICT)
class ConcurrentEditException(what: String) :
    RuntimeException("$what was changed by someone else in the meantime. Reload to see the latest version.")
