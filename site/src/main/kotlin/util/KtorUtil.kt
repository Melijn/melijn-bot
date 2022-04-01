package util

import io.ktor.request.*
import io.ktor.response.*

object KtorUtil {

    const val MELIJN_COOKIE_NAME = "jwt"
    const val MELIJN_COOKIE_MAXAGE = 7L

    fun ApplicationRequest.getMelijnSession(): String? {
        return this.cookies[MELIJN_COOKIE_NAME]
    }

    fun ApplicationResponse.setMelijnSession(value: String) {
        this.cookies.append(MELIJN_COOKIE_NAME, value, maxAge = MELIJN_COOKIE_MAXAGE)
    }
}