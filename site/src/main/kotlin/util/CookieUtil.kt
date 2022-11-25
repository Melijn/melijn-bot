package util

import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.impl.DefaultJwtBuilder
import io.jsonwebtoken.security.Keys
import io.ktor.util.*
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaInstant
import me.melijn.gen.Settings
import me.melijn.gen.UserCookieData
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

object CookieUtil {

    fun isValid(userCookieData: UserCookieData): Boolean {
        val instant = userCookieData.created.toInstant(UtcOffset.ZERO).toJavaInstant()
        val aliveDuration = Duration.between(instant, Instant.now())
        val aliveDurationKt = aliveDuration.toKotlinDuration()

        val expireDuration = userCookieData.expiresInSeconds.seconds
        if (aliveDurationKt > expireDuration) {
            return false
        }
        return true
    }

    fun generateRandomCookie(): String {
        val key = Keys.hmacShaKeyFor(Settings.service.jwtKey.decodeBase64Bytes())
        val prevUid = lastSubId++
        val newUid = (prevUid + 1).toString() + Random.nextLong()

        return DefaultJwtBuilder()
            .setPayload(newUid)
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    private var lastSubId = System.currentTimeMillis()
}