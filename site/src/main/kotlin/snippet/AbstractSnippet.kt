package snippet

import io.ktor.application.*
import org.koin.core.component.KoinComponent

abstract class AbstractSnippet<T> : KoinComponent {

    abstract val name: String
    abstract val src: String
    open suspend fun render(call: ApplicationCall, prop: T): String = src
}