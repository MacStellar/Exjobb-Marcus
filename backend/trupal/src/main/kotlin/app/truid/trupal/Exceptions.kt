package app.truid.trupal

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

open class Forbidden(val error: String, message: String?, cause: Throwable? = null) : RuntimeException(message, cause)
class Unauthorized(val error: String, message: String?) : RuntimeException(message)

class SessionNotFound(message: String? = null, cause: Throwable? = null) : Exception(message)

@ResponseStatus(HttpStatus.NOT_FOUND)
class P2PSessionNotFound(message: String? = null, cause: Throwable? = null) : Exception(message)

class UserSessionsNotFound(message: String? = null, cause: Throwable? = null) : Exception(message)

class SessionAlreadyComplete() : Exception("Session is already complete")