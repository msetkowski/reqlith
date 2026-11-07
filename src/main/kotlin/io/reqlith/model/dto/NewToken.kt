package io.reqlith.model.dto

import io.reqlith.model.entity.AccessToken

/** The raw token, returned exactly once when it is created. */
class NewToken(val token: AccessToken, val value: String)
