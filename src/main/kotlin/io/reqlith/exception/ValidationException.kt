package io.reqlith.exception

/** User input that breaks a business rule; shown next to [field] in forms. */
class ValidationException(val field: String?, message: String) : RuntimeException(message)
