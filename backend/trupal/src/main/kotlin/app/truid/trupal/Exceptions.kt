package app.truid.trupal

open class Forbidden(val error: String, message: String?, cause: Throwable? = null) : RuntimeException(message, cause)
class Unauthorized(val error: String, message: String?) : RuntimeException(message)
