package render

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.runBlocking
import me.melijn.kordkommons.logger.Log
import model.AbstractPage
import model.CustomResponseException
import model.FriendlyHttpException
import snippet.AbstractSnippet

object SourceRenderer {

    val registeredSnippets = HashMap<String, AbstractSnippet<Any>>()
    private val logger by Log

    suspend fun PipelineContext<Unit, ApplicationCall>.render(page: AbstractPage) {
        val done = try {
            val src = page.render(call)

            replaceSnippets(call, src)
        } catch (ex: FriendlyHttpException) {
            call.respondText(ex.info, ContentType.Text.Plain, ex.httpStatusCode)
            return
        } catch (ex: CustomResponseException) {
            return
        } catch (t: Throwable) {
            call.respondText("500 internal server error", ContentType.Text.Plain, HttpStatusCode.InternalServerError)
            logger.error(t) { "Unexpected error occurred while rendering ${page.route}" }
            return
        }
        call.respondText(done, page.contentType, HttpStatusCode.OK)
    }

    private fun replaceSnippets(call: ApplicationCall, src: String): String {
        val replaced = src.replace("\\{\\{\\s* (\\w+) \\s*}}".toRegex()) { match ->
            val snippetName = match.groups[1]!!.value
            runBlocking {
                registeredSnippets[snippetName]?.render(call, "fish") ?: "snippet \"$snippetName\" is unregistered"
            }
        }
        return replaced
    }
}