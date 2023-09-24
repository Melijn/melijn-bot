package me.melijn.bot.commands.thirdparty

import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.Query
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.PublicSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.enumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.stringChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalBoolean
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.InlineEmbed
import dev.minn.jda.ktx.messages.InlineMessage
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.commands.thirdparty.AnilistExtension.LookupArg.AnilistItemType.CHARACTER
import me.melijn.bot.commands.thirdparty.AnilistExtension.LookupArg.AnilistItemType.USER
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
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.koin.core.component.inject
import java.awt.Color

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
                    val response = webManager.aniListApolloClient.query(FindUserQuery(username)).execute()

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
                                val anilistId = response.data?.User?.userFragment?.id
                                    ?: bail(tr("anilist.link.failed"))

                                linkManager.store(
                                    AnilistLinkData(
                                        user.idLong,
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
                    val userId = user.idLong
                    when (val storage = linkManager.get(user)) {
                        null -> linkManager.store(AnilistLinkData(userId, null, pref))
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
                            linkManager.get(it)?.anilistId ?: bail(
                                tr(
                                    "anilist.profile.other.noLink",
                                    it.asMention
                                )
                            )
                        }
                        ?: (linkManager.get(user)?.anilistId ?: bail(tr("anilist.profile.you.noLink"))))

                    respond {
                        embed {
                            presentUser(id)
                        }
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
        val shard = this.event.jda

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
        suspend fun <D, E> performLookup(
            query: Query<D>,
            resultExtract: (D) -> List<E>,
            listinator: suspend E.(index: Int) -> String,
            presenter: suspend InlineMessage<MessageCreateData>.(E) -> Unit,
        ) where D : Query.Data {
            val response = webManager.aniListApolloClient.query(query).execute()
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
                    results.forEachIndexed { index, media ->
                        shard.button(ButtonStyle.SECONDARY, "$index") {
                            if (user != this@onLookup.user) return@button
                            respond {
                                presenter(media)
                            }
                        }
                    }
                } else {
                    embeds
                    presenter(results.first())
                }
            }
        }

        val preference = linkManager.get(user)?.preference ?: AniListLanguagePreference.ROMAJI

        // if media (anime or manga)
        val mediaType = type.mediaType
        if (mediaType != null) {
            performLookup(
                SearchMediaQuery(name, mediaType),
                resultExtract = {
                    // the items we're interested in
                    it.Page?.media?.filterNotNull() ?: emptyList()
                },
                listinator = { index ->
                    tr(
                        "anilist.lookup.entry",
                        index,
                        siteUrl ?: "https://www.horse.com/",
                        preference.prefer(
                            english = this.title?.english,
                            romaji = this.title?.romaji,
                            native = this.title?.native
                        ) ?: "No Name"
                    )
                },
                presenter = { medium ->
                    embed {
                        presentMedium(medium.id, mediaType)
                    }
                }
            )
        } else if (type == CHARACTER) {
            performLookup(
                SearchCharacterQuery(name),
                resultExtract = {
                    it.Page?.characters?.filterNotNull() ?: emptyList()
                },
                listinator = { index ->
                    // Try {FirstName LastName}, otherwise {NativeName}, otherwise "No name"
                    val cName = this.name?.let {
                        "${it.first ?: ""} ${it.last ?: ""}".ifEmpty { it.native }
                    } ?: "No name"

                    tr(
                        "anilist.lookup.entry",
                        index,
                        siteUrl ?: "https://www.horse.com/",
                        cName
                    )
                },
                presenter = { char ->
                    embed {
                        presentCharacter(char.id)
                    }
                }
            )
        } else if (type == USER) {
            performLookup(
                SearchUserQuery(name),
                resultExtract = {
                    it.Page?.users?.filterNotNull() ?: emptyList()
                },
                listinator = { index ->
                    tr(
                        "anilist.lookup.entry",
                        index,
                        siteUrl ?: "https://www.horse.com/",
                        this.name
                    )
                },
                presenter = { user -> embed { presentUser(user.id) } }
            )
        } else {
            throw IllegalStateException()
        }
    }

    context(InlineEmbed)
    private suspend fun CommandContext.presentCharacter(id: Int) {
        val character = getCharacter(id).Character?.characterFragment!!
        title = character.name?.let {
            "${it.first ?: ""} ${it.last ?: ""}".ifEmpty { it.native }
        } ?: "No name"
        url = character.siteUrl
        description = character.description?.let { htmlToMarkdown(it) }

        with(character) {
            // Thumbnail
            thumbnail = image?.large

            // Fields
            field(tr("anilist.lookup.embed.firstName"), name?.first ?: "/", inline = true)
            field(tr("anilist.lookup.embed.lastName"), name?.last ?: "/", inline = true)
            field(tr("anilist.lookup.embed.nativeName"), name?.native ?: "/", inline = true)

            // Computed fields
            media?.edges?.let { edge ->
                edge.filterNotNull().batchingJoinToString(1024, "\n") {
                    val tit = (it.node?.title?.romaji ?: "No title").escapeMarkdown()
                    "[$tit](${it.node?.siteUrl!!}) [(${it.node.type?.name!!.ucc()}) ${it.characterRole?.name?.ucc()}]"
                }.forEach { entry ->
                    field(tr("anilist.lookup.embed.appearances"), entry, inline = true)
                }
            }

            // Footer
            favourites?.let { favourites -> footer(tr("anilist.lookup.embed.favourites", favourites)) }
        }
    }

    context(InlineEmbed)
    private suspend fun CommandContext.presentMedium(id: Int, mediaType: MediaType) {
        val preference = linkManager.get(user)?.preference ?: AniListLanguagePreference.ROMAJI

        val media = when (mediaType) {
            MediaType.ANIME -> {
                val anime = getAnime(id).Media!!

                field(tr("anilist.lookup.embed.episodes"), inline = true) {
                    val episodes = anime.animeFragment.episodes ?: 0
                    val len = tr(
                        "anilist.lookup.embed.duration",
                        anime.mediaFragment.duration ?: 0
                    )

                    this.value = "$episodes ($len)"
                }
                anime.mediaFragment
            }

            MediaType.MANGA -> {
                val manga = getManga(id).Media!!
                field(tr("anilist.lookup.embed.volumes"), inline = true) {
                    this.value = (manga.mangaFragment.volumes ?: 0).toString()
                }
                field(tr("anilist.lookup.embed.chapters"), inline = true) {
                    this.value = (manga.mangaFragment.chapters ?: 0).toString()
                }
                manga.mediaFragment
            }

            MediaType.UNKNOWN__ -> throw IllegalStateException()
        }

        // General embed props
        this@InlineEmbed.title = preference.prefer(
            english = media.title?.english,
            romaji = media.title?.romaji,
            native = media.title?.native,
        )
        this@InlineEmbed.url = media.siteUrl
        this@InlineEmbed.description = media.description?.let { htmlToMarkdown(it) }
        media.coverImage?.let { coverImage ->
            this@InlineEmbed.thumbnail = coverImage.extraLarge!!
            this@InlineEmbed.color = coverImage.color?.let { Color.decode(it) }?.rgb
        }

        // Fields
        media.synonyms?.filterNotNull()?.let { synonyms ->
            if (synonyms.isNotEmpty())
                field(tr("anilist.lookup.embed.names"), synonyms.joinToString("\n"), true)
        }
        media.averageScore?.let { score ->
            field(tr("anilist.lookup.embed.score"), "$score%", true)
        }
        media.genres?.joinToString("\n")?.nullIfEmpty?.let { genres ->
            field(tr("anilist.lookup.embed.genres"), genres, true)
        }
        media.format?.name?.let { v ->
            field(tr("anilist.lookup.embed.format"), v.ucc(), inline = true)
        }
        media.status?.name?.let { v ->
            field(tr("anilist.lookup.embed.status"), v.ucc(), inline = true)
        }
        if (media.startDate?.year != null) {
            val (y, m, d) = media.startDate
            field(tr("anilist.lookup.embed.startDate"), "${y!!}-${m!!}-${d!!}", true)
        }
        if (media.endDate?.year != null) {
            val (y, m, d) = media.endDate
            field(tr("anilist.lookup.embed.endDate"), "${y!!}-${m!!}-${d!!}", inline = true)
        }
        // Footer
        media.favourites?.let { favourites ->
            footer(tr("anilist.lookup.embed.favourites", favourites))
        }
    }

    context(InlineEmbed)
    private suspend fun CommandContext.presentUser(id: Int) {
        val preference = linkManager.get(user)?.preference ?: AniListLanguagePreference.ROMAJI

        val user = getUser(id).User?.userFragment!!
        with(user) {
            title = name
            url = siteUrl
            description = about?.let { htmlToMarkdown(it) }
            thumbnail = avatar?.large

            options?.profileColor?.let { pc ->
                color = when (pc) {
                    "blue" -> Color.BLUE
                    "purple" -> Color.MAGENTA
                    "pink" -> Color.PINK
                    "orange" -> Color.ORANGE
                    "red" -> Color.RED
                    "green" -> Color.GREEN
                    "gray" -> Color.GRAY
                    else -> Color.YELLOW
                }.rgb
            }

            fun <E> Collection<E>.nullIfEmpty(): Collection<E>? = takeIf { isNotEmpty() }

            statistics?.anime?.genres?.mapNotNull { it?.genre }?.nullIfEmpty()?.let { genres ->
                field(
                    tr("anilist.lookup.embed.topAnimeGenres"),
                    inline = true
                ) {
                    value = genres.asSequence()
                        .mapIndexed { index, s -> "`$index.` $s" }
                        .joinToString("\n")
                }
            }
            statistics?.manga?.genres?.mapNotNull { it?.genre }?.nullIfEmpty()?.let { genres ->
                field(
                    tr("anilist.lookup.embed.topMangaGenres"),
                    inline = true
                ) {
                    value = genres.asSequence()
                        .mapIndexed { index, s -> "`$index.` $s" }
                        .joinToString("\n")
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
                value = v.ifEmpty { tr("anilist.lookup.embed.statistics.none") }
            }
            favourites?.anime?.nodes?.filterNotNull()?.nullIfEmpty()?.let {
                field(tr("anilist.lookup.embed.favAnime"), inline = true) {
                    value = it.joinToString("\n", limit = 10) {
                        val tit = preference.prefer(
                            romaji = it.title?.romaji,
                            english = it.title?.english,
                        ) ?: "No title"
                        "• [$tit](${it.siteUrl})"
                    }
                }
            }
            favourites?.manga?.nodes?.filterNotNull()?.nullIfEmpty()?.let {
                field(tr("anilist.lookup.embed.favManga"), inline = true) {
                    value = it.joinToString("\n", limit = 10) {
                        val tit = preference.prefer(
                            romaji = it.title?.romaji,
                            english = it.title?.english,
                        ) ?: "No title"
                        "• [$tit](${it.siteUrl})"
                    }
                }
            }
            favourites?.characters?.nodes?.filterNotNull()?.nullIfEmpty()?.let {
                field(tr("anilist.lookup.embed.favCharacter"), inline = true) {
                    value = it.joinToString("\n", limit = 10) {
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
     * Bail if the GraphQL query [ApolloResponse] has errors
     */
    private suspend fun <T : Operation.Data> CommandContext.assertNonErroneous(response: ApolloResponse<T>) {
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

    private suspend fun <D> Query<D>.executeQuery(): ApolloResponse<D> where D : Query.Data {
        return webManager.aniListApolloClient.query(this).execute()
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