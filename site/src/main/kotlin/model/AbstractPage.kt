package model

import io.ktor.application.*
import io.ktor.http.*
import org.koin.core.component.KoinComponent

abstract class AbstractPage(
    /** use "{variable}" to match anything but slashes **/
    val route: String,
    val contentType: ContentType
) : KoinComponent {
    abstract val src: String
    open val aliasRoutes: Array<String> = arrayOf()

    open suspend fun render(call: ApplicationCall): String = src
}
