package app.truid.trupal

import jakarta.servlet.http.HttpSession

fun createOauth2State(session: HttpSession): String {
    // Retrieve oauth2-state attribute from session, or generate and set a new one
    val oauth2State: String? = session.getAttribute("oauth2-state") as String?
    return oauth2State ?: run {
        val newState = base64url(random(20))
        session.setAttribute("oauth2-state", newState)
        return newState
    }
}

fun verifyOauth2State(session: HttpSession, state: String?): Boolean {
    val savedState= session.getAttribute("oauth2-state") as String?

    return savedState != null && state != null && state == savedState
}

fun createOauth2CodeChallenge(session: HttpSession): String {
    // Create code verifier,
    // according to https://www.rfc-editor.org/rfc/rfc7636#section-4.1
    val codeVerifier = base64url(random(32))
    session.setAttribute("oauth2-code-verifier", codeVerifier)

//    Create code challenge,
//    according to https://www.rfc-editor.org/rfc/rfc7636#section-4.2
    return base64url(sha256(codeVerifier))
}

fun getOauth2CodeVerifier(session: HttpSession): String? {
    return session.getAttribute("oauth2-code-verifier") as String?
}

