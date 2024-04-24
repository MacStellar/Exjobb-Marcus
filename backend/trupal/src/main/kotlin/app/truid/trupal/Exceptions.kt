package app.truid.trupal

open class Forbidden(val error: String, message: String?, cause: Throwable? = null) : RuntimeException(message, cause)

open class P2PSessionStatusException(message: String?, cause: Throwable? = null) :
    RuntimeException(
        message,
        cause,
    )

open class CookieSessionNotFound(cause: Throwable? = null) :
    RuntimeException("Cookie session not found on server-side", cause)

open class P2PSessionNotFound(cause: Throwable? = null) : RuntimeException("P2P session not found in database", cause)

open class UserSessionsNotFound(cause: Throwable? = null) :
    RuntimeException("No users were found to be connected to the p2p session", cause)

open class PersonNotFound(cause: Throwable? = null) : RuntimeException("Person could not be found", cause)
