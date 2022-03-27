package model

import io.ktor.application.*
import io.ktor.http.*

abstract class AbstractPage(
    /** use "{variable}" to match anything but slashes **/
    val route: String,
    val contentType: ContentType
) {
    abstract val src: String
    open val aliasRoutes: Array<String> = arrayOf()

    open suspend fun render(call: ApplicationCall): String = src
}
