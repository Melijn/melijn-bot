package model

import io.ktor.http.*

class FriendlyHttpException(
    val httpStatusCode: HttpStatusCode,
    val info: String
) : Throwable()