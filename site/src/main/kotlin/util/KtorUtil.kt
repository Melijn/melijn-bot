package util

import io.ktor.server.request.*
import io.ktor.server.response.*

object KtorUtil {

    private const val MELIJN_COOKIE_NAME = "jwt"
    private const val MELIJN_COOKIE_MAXAGE = 604_800L

    fun ApplicationRequest.getMelijnSession(): String? {
        return cookies[MELIJN_COOKIE_NAME]
    }

    fun ApplicationResponse.setMelijnSession(value: String) {
        cookies.append(MELIJN_COOKIE_NAME, value, maxAge = MELIJN_COOKIE_MAXAGE)
    }

    fun ApplicationResponse.clearMelijnSession() {
        cookies.appendExpired(MELIJN_COOKIE_NAME)
    }
}