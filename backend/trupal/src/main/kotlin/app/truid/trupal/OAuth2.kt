package app.truid.trupal

import jakarta.servlet.http.HttpSession
import org.springframework.web.server.WebSession

//Ersätt och spara i en mutable map eller något liknande, eller i databasen (för att undvika webflux):

//Original

//fun createOauth2State(session: WebSession): String {
//    return session.attributes.compute("oauth2-state") { _, _ ->
//        // Use state parameter to prevent CSRF,
//        // according to https://www.rfc-editor.org/rfc/rfc6749#section-10.12
//        base64url(random(20))
//    } as String
//}

// Från GPT

//fun createOauth2State(session: HttpSession): String {
//    // Retrieve oauth2-state attribute from session, or generate and set a new one
//    val oauth2State: String? = session.getAttribute("oauth2-state") as String?
//    return oauth2State ?: run {
//        // If oauth2-state is null, generate a new one
//        val newState = base64url(random(20))
//        session.setAttribute("oauth2-state", newState)
//        newState
//    }
//}

// Min variant

fun createOauth2State(session: HttpSession): String {
    // Retrieve oauth2-state attribute from session, or generate and set a new one
//    val oauth2State: String? = session.getAttribute("oauth2-state") as String?
//    return oauth2State ?: run {
//        // If oauth2-state is null, generate a new one
//        val newState = base64url(random(20))
//        session.setAttribute("oauth2-state", newState)
//        newState
//    }
    val newState = base64url(random(20))
    session.setAttribute("oauth2-state", newState)
    return newState
}

// Originalvariant

//fun verifyOauth2State(session: WebSession, state: String?): Boolean {
//    val savedState = session.attributes.remove("oauth2-state") as String?
//    return savedState != null && state != null && state == savedState
//}

// Min variant

fun verifyOauth2State(session: HttpSession, state: String?): Boolean {
//    val savedState = session.attributes.remove("oauth2-state") as String?
    val savedState = session.removeAttribute("oauth2-state") as String?
    return savedState != null && state != null && state == savedState
}

// Originalvariant

//fun createOauth2CodeChallenge(session: WebSession): String {
//    val codeVerifier = session.attributes.compute("oauth2-code-verifier") { _, _ ->
//        // Create code verifier,
//        // according to https://www.rfc-editor.org/rfc/rfc7636#section-4.1
//        base64url(random(32))
//    } as String
//
//    // Create code challenge,
//    // according to https://www.rfc-editor.org/rfc/rfc7636#section-4.2
//    return base64url(sha256(codeVerifier))
//}

// Min variant

fun createOauth2CodeChallenge(session: HttpSession): String {
    val codeVerifier = base64url(random(32))
    session.setAttribute("oauth2-code-verifier", codeVerifier)
    return base64url(sha256(codeVerifier))
}

// Originalvariant

//fun getOauth2CodeVerifier(session: WebSession): String? {
//    return session.attributes["oauth2-code-verifier"] as String?
//}

// Min variant

fun getOauth2CodeVerifier(session: HttpSession): String? {
//    Kanske blir fel eftersom den tar emot en attribut som array i originalet
    return session.getAttribute("oauth2-code-verifier") as String?
}

