package me.melijn.bot.commands

import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.api.Response
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.stringChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalBoolean
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.publicButton
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.kord.common.entity.ButtonStyle.Secondary
import dev.kord.common.kColor
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import dev.kord.rest.builder.message.create.embed
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.commands.AnilistExtension.LookupArg.AnilistItemType.CHARACTER
import me.melijn.bot.commands.AnilistExtension.LookupArg.AnilistItemType.USER
import me.melijn.bot.database.manager.AnilistLinkManager
import me.melijn.bot.utils.EnumUtil.ucc
import me.melijn.bot.utils.InferredChoiceEnum
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.Log
import me.melijn.bot.utils.StringsUtil.batchingJoinToString
import me.melijn.bot.utils.StringsUtil.htmlToMarkdown
import me.melijn.bot.web.api.WebManager
import me.melijn.gen.AnilistLinkData
import me.melijn.kordkommons.utils.escapeMarkdown
import me.melijn.kordkommons.utils.remove
import me.melijn.melijnbot.anilist.*
import me.melijn.melijnbot.anilist.type.MediaType
import okio.ByteString.Companion.encodeUtf8
import org.koin.core.component.inject
import java.awt.Color
import kotlin.time.Duration.Companion.minutes

@KordExtension
class AnilistExtension : Extension() {

    override val name: String = "anilist"
    private val webManager by inject<WebManager>()
    private val linkManager by inject<AnilistLinkManager>()
    private val logger by Log

    override suspend fun setup() {
        publicSlashCommand {
            name = this@AnilistExtension.name
            description = "Interact with AniList"

            publicSubCommand(::UsernameArg) {
                name = "link"
                description = "Link your discord and AniList accounts"

                action {
                    val username = arguments.username.parsed.profileStripped
                    val response = FindUserQuery(username).executeQuery()

                    respond {
                        when {
                            response.hasErrors() && response.errors?.any { it.message == "Not found." } == true -> {
                                content = tr("anilist.link.noUser")
                            }
                            response.hasErrors() -> {
                                logger.error { "AniList user lookup failed: " + response.errors?.joinToString { it.message } }
                                content = tr("anilist.link.failed")
                            }
                            else -> {
                                val anilistId = response.data?.user?.fragments?.userFragment?.id
                                    ?: bail(tr("anilist.link.failed"))

                                linkManager.store(
                                    AnilistLinkData(
                                        user.id.value,
                                        anilistId,
                                        AniListLanguagePreference.ROMAJI
                                    )
                                )

                                content = tr("anilist.link.succeeded")
                            }
                        }
                    }
                }
            }

            publicSubCommand(::PreferenceArg) {
                name = "preference"
                description = "Change your preferred AniList result language"

                action {
                    val pref = arguments.preference.parsed
                    val userId = user.id
                    when (val storage = linkManager.get(userId)) {
                        null -> linkManager.store(AnilistLinkData(userId.value, null, pref))
                        else -> linkManager.store(storage.apply {
                            preference = pref
                        })
                    }

                    respond {
                        content = tr("anilist.preference.set")
                    }
                }
            }

            publicSubCommand(::UserArg) {
                name = "profile"
                description = "View AniList profile of discord user"

                action {
                    val id = (arguments.user.parsed
                        ?.let {
                            linkManager.get(it.id)?.anilistId ?: bail(
                                tr(
                                    "anilist.profile.other.noLink",
                                    it.mention
                                )
                            )
                        }
                        ?: (linkManager.get(getUser().id)?.anilistId ?: bail(tr("anilist.profile.you.noLink"))))

                    respond {
                        embeds.add(presentUser(id))
                    }
                }
            }

            publicSubCommand(::LookupArg) {
                name = "lookup"
                description = "View a AniList anime or manga entry"

                action { onLookup() }
            }
        }
    }

    private suspend fun PublicSlashCommandContext<LookupArg>.onLookup() {
        val type = arguments.type.parsed
        val name = arguments.name.parsed
        val search = arguments.search.parsed ?: false

        /**
         * For some generic search query, perform the command flow with a few implementation-specific data points:
         *
         * * Run the query
         * * Bail if erroneous or empty results
         * * Present the user with the results page, if required
         *
         * @param query The GraphQL search query
         * @param resultExtract Returns the list of items the query yielded
         * @param listinator Transform each search result to a human-readable list entry
         * @param presenter Presents, in full detail, one search result, preferably using an embed of sorts
         */
        suspend fun <D, T, V, E> performLookup(
            query: Query<D, T, V>,
            resultExtract: (T) -> List<E>,
            listinator: suspend E.(index: Int) -> String,
            presenter: suspend MessageCreateBuilder.(E) -> Unit,
        ) where D : Operation.Data, V : Operation.Variables {
            val response = query.executeQuery()
            assertNonErroneous(response)
            val results = resultExtract(response.data!!)
            if (results.isEmpty()) bail(tr("anilist.lookup.empty"))

            respond {
                if (search && results.size != 1) {
                    embed {
                        this.title = tr("anilist.lookup.title")
                        description = results.mapIndexed { index, media -> media.listinator(index) }
                            .joinToString("\n")
                    }
                    components(timeout = 5.minutes) {
                        results.forEachIndexed { index, media ->
                            publicButton {
                                style = Secondary
                                label = "$index"

                                action {
                                    if (user != this@onLookup.user) return@action
                                    respond {
                                        presenter(media)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    embeds
                    presenter(results.first())
                }
            }
        }

        val preference = linkManager.get(user.id)?.preference ?: AniListLanguagePreference.ROMAJI

        // if media (anime or manga)
        val mediaType = type.mediaType
        if (mediaType != null) {
            performLookup(
                SearchMediaQuery(name, mediaType),
                resultExtract = {
                    // the items we're interested in
                    it.page?.media?.filterNotNull() ?: emptyList()
                },
                listinator = { index ->
                    tr(
                        "anilist.lookup.entry",
                        index,
                        siteUrl ?: "https://www.horse.com/",
                        preference.prefer(
                            english = this.title?.english,
                            romaji = this.title?.romaji,
                            native = this.title?.native_
                        ) ?: "No Name"
                    )
                },
                presenter = { medium -> embeds.add(presentMedium(medium.id, mediaType)) }
            )
        } else if (type == CHARACTER) {
            performLookup(
                SearchCharacterQuery(name),
                resultExtract = {
                    it.page?.characters?.filterNotNull() ?: emptyList()
                },
                listinator = { index ->
                    // Try {FirstName LastName}, otherwise {NativeName}, otherwise "No name"
                    val cName = this.name?.let {
                        "${it.first ?: ""} ${it.last ?: ""}".ifEmpty { it.native_ }
                    } ?: "No name"

                    tr(
                        "anilist.lookup.entry",
                        index,
                        siteUrl ?: "https://www.horse.com/",
                        cName
                    )
                },
                presenter = { char -> embeds.add(presentCharacter(char.id)) }
            )
        } else if (type == USER) {
            performLookup(
                SearchUserQuery(name),
                resultExtract = {
                    it.page?.users?.filterNotNull() ?: emptyList()
                },
                listinator = { index ->
                    tr(
                        "anilist.lookup.entry",
                        index,
                        siteUrl ?: "https://www.horse.com/",
                        this.name
                    )
                },
                presenter = { user -> embeds.add(presentUser(user.id)) }
            )
        } else {
            throw IllegalStateException()
        }
    }

    private suspend fun CommandContext.presentCharacter(id: Int) = EmbedBuilder().apply {
        val character = getCharacter(id).character?.fragments?.characterFragment!!
        title = character.name?.let {
            "${it.first ?: ""} ${it.last ?: ""}".ifEmpty { it.native_ }
        } ?: "No name"
        url = character.siteUrl
        description = character.description?.let { htmlToMarkdown(it) }

        with(character) {
            image?.let { image ->
                thumbnail {
                    url = image.large!!
                    // TODO: embed colour?
                }
            }

            field(tr("anilist.lookup.embed.firstName"), inline = true) { name?.first ?: "/" }
            field(tr("anilist.lookup.embed.lastName"), inline = true) { name?.last ?: "/" }
            field(tr("anilist.lookup.embed.nativeName"), inline = true) { name?.native_ ?: "/" }
            media?.edges?.let { edge ->
                edge.filterNotNull().batchingJoinToString(1024, "\n") {
                    val tit = (it.node?.title?.romaji ?: "No title").escapeMarkdown()
                    "[$tit](${it.node?.siteUrl!!}) [(${it.node.type?.name!!.ucc()}) ${it.characterRole?.name?.ucc()}]"
                }.forEach { v -> field(tr("anilist.lookup.embed.appearances"), inline = true) { v } }
            }
            favourites?.let { favourites ->
                footer {
                    text = tr("anilist.lookup.embed.favourites", favourites)
                }
            }
        }
    }

    private suspend fun CommandContext.presentMedium(id: Int, mediaType: MediaType) = EmbedBuilder().apply {
        val preference = linkManager.get(getUser()!!.id)?.preference ?: AniListLanguagePreference.ROMAJI

        val media = when (mediaType) {
            MediaType.ANIME -> {
                val anime = getAnime(id).media!!

                field(tr("anilist.lookup.embed.episodes"), inline = true) {
                    val episodes = anime.fragments.animeFragment.episodes ?: 0
                    val len = tr(
                        "anilist.lookup.embed.duration",
                        anime.fragments.mediaFragment.duration ?: 0
                    )

                    "$episodes ($len)"
                }
                anime.fragments.mediaFragment
            }
            MediaType.MANGA -> {
                val manga = getManga(id).media!!
                field(tr("anilist.lookup.embed.volumes"), inline = true) {
                    (manga.fragments.mangaFragment.volumes ?: 0).toString()
                }
                field(tr("anilist.lookup.embed.chapters"), inline = true) {
                    (manga.fragments.mangaFragment.chapters ?: 0).toString()
                }
                manga.fragments.mediaFragment
            }
            MediaType.UNKNOWN__ -> throw IllegalStateException()
        }

        title = preference.prefer(
            english = media.title?.english,
            romaji = media.title?.romaji,
            native = media.title?.native_,
        )
        url = media.siteUrl
        description = media.description?.let { htmlToMarkdown(it) }

        media.favourites?.let { favourites ->
            footer {
                text = tr("anilist.lookup.embed.favourites", favourites)
            }
        }
        media.synonyms?.filterNotNull()?.let { synonyms ->
            if (synonyms.isNotEmpty())
                field(tr("anilist.lookup.embed.names"), inline = true) {
                    synonyms.joinToString("\n")
                }
        }
        media.averageScore?.let { score ->
            field(tr("anilist.lookup.embed.score"), inline = true) { "$score%" }
        }
        media.genres?.joinToString("\n")?.nullIfEmpty?.let { genres ->
            field(tr("anilist.lookup.embed.genres"), inline = true) { genres }
        }
        media.format?.name?.let { v ->
            field(tr("anilist.lookup.embed.format"), inline = true) { v.ucc() }
        }
        media.status?.name?.let { v ->
            field(tr("anilist.lookup.embed.status"), inline = true) { v.ucc() }
        }
        if (media.startDate?.year != null) {
            val (_, y, m, d) = media.startDate
            field(tr("anilist.lookup.embed.startDate"), inline = true) { "${y!!}-${m!!}-${d!!}" }
        }
        if (media.endDate?.year != null) {
            val (_, y, m, d) = media.endDate
            field(tr("anilist.lookup.embed.endDate"), inline = true) { "${y!!}-${m!!}-${d!!}" }
        }

        media.coverImage?.let { coverImage ->
            thumbnail {
                url = coverImage.extraLarge!!
                this@apply.color = coverImage.color?.let { Color.decode(it) }?.kColor
            }
        }
    }

    private suspend fun CommandContext.presentUser(id: Int) = EmbedBuilder().apply {
        val preference = linkManager.get(getUser()!!.id)?.preference ?: AniListLanguagePreference.ROMAJI

        val user = getUser(id).user?.fragments?.userFragment!!
        with(user) {
            title = name
            url = siteUrl
            description = about?.let { htmlToMarkdown(it) }

            avatar?.large?.let { av ->
                thumbnail { url = av }
            }
            options?.profileColor?.let { pc ->
                this@apply.color = when (pc) {
                    "blue" -> Color.BLUE
                    "purple" -> Color.MAGENTA
                    "pink" -> Color.PINK
                    "orange" -> Color.ORANGE
                    "red" -> Color.RED
                    "green" -> Color.GREEN
                    "gray" -> Color.GRAY
                    else -> Color.YELLOW
                }.kColor
            }

            statistics?.anime?.genres?.filterNotNull()?.mapNotNull { it.genre }?.let { genres ->
                if (genres.isNotEmpty()) {
                    field(
                        tr("anilist.lookup.embed.topAnimeGenres"),
                        inline = true
                    ) { genres.mapIndexed { index, s -> "`$index.` $s" }.joinToString("\n") }
                }
            }
            statistics?.manga?.genres?.filterNotNull()?.mapNotNull { it.genre }?.let { genres ->
                if (genres.isNotEmpty()) {
                    field(
                        tr("anilist.lookup.embed.topMangaGenres"),
                        inline = true
                    ) { genres.mapIndexed { index, s -> "`$index.` $s" }.joinToString("\n") }
                }
            }
            field(tr("anilist.lookup.embed.statistics")) {
                var v = ""
                statistics?.anime?.let { s ->
                    v = tr("anilist.lookup.embed.animeStats", s.count, s.episodesWatched, s.minutesWatched) +
                            "\n" + tr("anilist.lookup.embed.meanScore", s.meanScore, s.standardDeviation) +
                            "\n"
                }
                statistics?.manga?.let { s ->
                    v += "\n" + tr("anilist.lookup.embed.mangaStats", s.count, s.chaptersRead) +
                            "\n" + tr("anilist.lookup.embed.meanScore", s.meanScore, s.standardDeviation)
                }
                v.ifEmpty { tr("anilist.lookup.embed.statistics.none") }
            }
            favourites?.anime?.nodes?.filterNotNull()?.let {
                field(tr("anilist.lookup.embed.favAnime"), inline = true) {
                    it.joinToString("\n", limit = 10) {
                        val tit = preference.prefer(
                            romaji = it.title?.romaji,
                            english = it.title?.english,
                        ) ?: "No title"
                        "• [$tit](${it.siteUrl})"
                    }
                }
            }
            favourites?.manga?.nodes?.filterNotNull()?.let {
                field(tr("anilist.lookup.embed.favManga"), inline = true) {
                    it.joinToString("\n", limit = 10) {
                        val tit = preference.prefer(
                            romaji = it.title?.romaji,
                            english = it.title?.english,
                        ) ?: "No title"
                        "• [$tit](${it.siteUrl})"
                    }
                }
            }
            favourites?.characters?.nodes?.filterNotNull()?.let {
                field(tr("anilist.lookup.embed.favCharacter"), inline = true) {
                    it.joinToString("\n", limit = 10) {
                        val name = it.name?.full ?: "No name"
                        "• [$name](${it.siteUrl})"
                    }
                }
            }
        }
    }

    private suspend fun CommandContext.getAnime(id: Int): GetAnimeQuery.Data {
        val response = GetAnimeQuery(id).executeQuery()
        assertNonErroneous(response)
        return response.data!!
    }

    private suspend fun CommandContext.getManga(id: Int): GetMangaQuery.Data {
        val response = GetMangaQuery(id).executeQuery()
        assertNonErroneous(response)
        return response.data!!
    }

    private suspend fun CommandContext.getCharacter(id: Int): GetCharacterQuery.Data {
        val response = GetCharacterQuery(id).executeQuery()
        assertNonErroneous(response)
        return response.data!!
    }

    private suspend fun CommandContext.getUser(id: Int): GetUserQuery.Data {
        val response = GetUserQuery(id).executeQuery()
        assertNonErroneous(response)
        return response.data!!
    }

    /**
     * Bail if the GraphQL query [Response] has errors
     */
    private suspend fun <T> CommandContext.assertNonErroneous(response: Response<T>) {
        if (response.hasErrors())
            bail(tr("anilist.lookup.failed", response.errors!!.joinToString { it.message }))
    }

    internal class PreferenceArg : Arguments() {
        val preference = enumChoice<AniListLanguagePreference> {
            name = "preference"
            description = "The language you prefer names and titles to be in"
            typeName = "preference"
        }
    }

    internal class UsernameArg : Arguments() {
        val username = string {
            name = "username"
            description = "AniList profile URL or username"
        }
    }

    internal class UserArg : Arguments() {
        val user = optionalUser {
            name = "user"
            description = "Discord user of whom to view AniList profile of"
        }
    }

    internal class LookupArg : Arguments() {
        enum class AnilistItemType(val mediaType: MediaType? = null) : InferredChoiceEnum {
            MANGA(MediaType.MANGA), ANIME(MediaType.ANIME), CHARACTER, USER
        }

        val type = enumChoice<AnilistItemType> {
            name = "type"
            description = "The kind of media to look for"
            typeName = "anilistType"
        }
        val name = stringChoice {
            name = "name"
            description = "The name of the media you're looking for"
        }
        val search = optionalBoolean {
            name = "search"
            description = "Whether or not to show search results"
        }
    }

    private suspend fun <D, T, V> Query<D, T, V>.executeQuery(): Response<T> where D : Operation.Data, V : Operation.Variables {
        val body: String = this.composeRequestBody().utf8()

        val result: String = webManager.httpClient.post("https://graphql.anilist.co") {
            setBody(body)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body()

        return this.parse(result.encodeUtf8())
    }

    private val String.profileStripped: String
        get() = this.remove("https://anilist.co/user", "/")

    private val String.nullIfEmpty: String?
        get() = ifEmpty { null }

}

enum class AniListLanguagePreference : InferredChoiceEnum {
    ENGLISH, ROMAJI, NATIVE;

    fun prefer(english: String? = null, romaji: String? = null, native: String? = null) =
        when (this) {
            ENGLISH -> english ?: romaji ?: native
            ROMAJI -> romaji ?: english ?: native
            NATIVE -> native ?: romaji ?: english
        }
}