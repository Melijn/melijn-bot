@file:Suppress("ArrayInDataClass")

package me.melijn.bot.commands

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.defaultingEnumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalUser
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.types.respondingPaginator
import dev.kord.common.DiscordTimestampStyle
import dev.kord.common.kColor
import dev.kord.common.toMessageFormat
import dev.kord.rest.builder.message.EmbedBuilder
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.OsuLinkManager
import me.melijn.bot.database.manager.OsuTokenManager
import me.melijn.bot.model.Cell
import me.melijn.bot.model.enums.Alignment
import me.melijn.bot.utils.InferredChoiceEnum
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.TableBuilder
import me.melijn.bot.web.api.WebManager
import me.melijn.gen.OsuLinkData
import me.melijn.gen.Settings
import org.koin.core.component.inject
import java.awt.Color
import java.net.URI
import java.util.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@KordExtension
class OsuExtension : Extension() {

    override val name: String = "osu"
    private val settings by inject<Settings>()
    private val webManager by inject<WebManager>()
    private val linkManager by inject<OsuLinkManager>()
    private val tokenManager by inject<OsuTokenManager>()

    override suspend fun setup() {
        publicSlashCommand {
            name = "osu"
            description = "View osu! profiles and statistics"

            publicSubCommand(::OsuAccountArg) {
                name = "link"
                description = "Links your discord and osu! account"

                action {
                    val account = arguments.account
                    val token = assertToken()
                    val osuUser = getUser(account, token, GameMode.OSU)

                    respond {
                        linkManager.store(
                            OsuLinkData(
                                user.id.value,
                                osuUser.id,
                            )
                        )

                        content = tr("osu.link.succeeded")
                    }
                }
            }

            publicSubCommand(::OsuAccountAndModeArg) {
                name = "lookup"
                description = "View osu! profile by username or ID"

                action {
                    val account = arguments.account
                    val token = assertToken()
                    val osuUser = getUser(account, token, GameMode.OSU)

                    respond {
                        embeds.add(presentUser(osuUser, arguments.gameMode))
                    }
                }
            }

            publicSubCommand(::OsuAccountArg) {
                name = "scores"
                description = "View recent, best and firsts plays"

                action {
                    val account = arguments.account
                    val token = assertToken()
                    val osuUser = getUser(account, token, GameMode.OSU)
                    val scores = getScores(osuUser.id, "best", token, GetUserScoresRequest())
                        .getOrElse { bail(it.message ?: "wfjskld") }

                    respondingPaginator {
                        for ((i, score) in scores.withIndex()) {
                            println(score)
                            val beatmapset = score.beatmapset
                            val stats = score.statistics
                            val table = TableBuilder()
                                .setColumns("300", "100", "50", "miss")
                                .addRow(
                                    Cell(stats.count_300.toString(), Alignment.RIGHT),
                                    Cell(stats.count_100.toString(), Alignment.RIGHT),
                                    Cell(stats.count_50.toString(), Alignment.RIGHT),
                                    Cell(stats.count_miss.toString(), Alignment.RIGHT),
                                )
                                .build(false)
                                .first()

                            val hits = stats.count_300 + stats.count_100 + stats.count_50
                            val total = hits + stats.count_miss
                            page {
                                title = "Score #${i}"
                                url = score.beatmap.url
                                description = tr(
                                    "osu.scores.layout",
                                    beatmapset.title,
                                    beatmapset.favourite_count,
                                    beatmapset.play_count,
                                    beatmapset.favourite_count / beatmapset.play_count.toDouble() * 100,
                                    hits,
                                    total,
                                    hits / total.toDouble() * 100,
                                    score.accuracy * 100,
                                    stats.count_katu, stats.count_geki,
                                    table,
                                    score.created_at.toMessageFormat(DiscordTimestampStyle.LongDateTime)
                                )

                                thumbnail {
                                    url = beatmapset.covers.list2x ?: beatmapset.covers.list
                                }
                            }
                        }
                    }.send()
                }
            }

            publicSubCommand(::UserAndModeArg) {
                name = "profile"
                description = "View osu! profile of discord user"

                action {
                    val id = (arguments.user
                        ?.let {
                            linkManager.get(it.id)?.osuId
                                ?: bail(tr("osu.profile.other.noLink", it.mention))
                        } ?: (linkManager.get(getUser().id)?.osuId
                        ?: bail(tr("osu.profile.you.noLink"))))

                    val token = assertToken()
                    val gameMode = arguments.gameMode
                    val osuUser = getUser("$id", token, gameMode)

                    respond {
                        embeds.add(presentUser(osuUser, gameMode))
                    }
                }
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun CommandContext.getUser(
        account: String,
        token: String,
        gameMode: GameMode
    ): User = get<User, Unit>(
        endpoint("users/$account/${gameMode.name.lowercase()}"),
        Unit,
        token
    ).getOrElse { bail(tr("osu.noUser")) }

    private suspend fun getScores(user: Int, type: String, token: String, req: GetUserScoresRequest) =
        get<List<Score>, GetUserScoresRequest>(
            endpoint("users/$user/scores/$type"),
            req,
            token
        )

    inner class OsuAccountArg : Arguments() {
        val account by string {
            name = "account"
            description = "osu! account username or ID"
        }
    }

    internal class UserAndModeArg : Arguments() {
        val user by optionalUser {
            name = "user"
            description = "Discord user of whom to view osu! profile of"
        }
        val gameMode by gameModeArg()
    }

    internal class OsuAccountAndModeArg : Arguments() {
        val account by string {
            name = "account"
            description = "osu! account username or ID"
        }
        val gameMode by gameModeArg()
    }

    /**
     * Get the token, if valid, from cache.
     *
     * Otherwise, fetch a new one and store it.
     *
     * @return the token
     */
    private suspend fun assertToken(): String = tokenManager.get() ?: obtainToken().getOrThrow().also {
        tokenManager.store(it.accessToken, it.expire)
    }.accessToken

    private suspend inline fun <reified R, reified B> get(
        endpoint: String,
        body: B,
        token: String,
        params: HttpRequestBuilder.() -> Unit = {}
    ): Result<R> =
        webManager.httpClient.get(endpoint) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            params()
            setBody(body)
        }.catchErroneousResponse()

    private suspend inline fun <reified B> post(endpoint: String, token: String? = null) =
        post<Unit, B>(endpoint, Unit, token)

    private suspend inline fun <reified B, reified R> post(
        endpoint: String,
        body: B,
        token: String? = null
    ): Result<R> =
        webManager.httpClient.post(endpoint) {
            token?.let {
                header("Authorization", "Bearer $it")
            }
            contentType(ContentType.Application.Json)
            setBody(body)
        }.catchErroneousResponse()

    private suspend fun obtainToken(): Result<TokenResponse> =
        post("https://osu.ppy.sh/oauth/token", AuthRequest(settings.api.osu.clientId, settings.api.osu.secret))

    private suspend inline fun <reified T> HttpResponse.catchErroneousResponse(): Result<T> =
        if (this.status.isSuccess()) {
            Result.success(body())
        } else {
            Result.failure(body<ErrorResponse>())
        }

    private suspend fun CommandContext.presentUser(user: User, gameMode: GameMode) = EmbedBuilder().apply {
        thumbnail {
            url = user.avatar_url
        }

        title = tr("osu.user.title", user.username, gameMode.humanName)
        url = user.siteUrl(gameMode)
        user.profile_colour?.let { color = Color.decode(it).kColor }

        user.statistics?.let {
            field(tr("osu.user.gamesPlayed"), inline = true) { tr("osu.user.gamesPlayed.format", it.play_count) }
            field(tr("osu.user.accuracy"), inline = true) { tr("osu.user.accuracy.format", it.hit_accuracy) }
            field(tr("osu.user.level"), inline = true) {
                tr("osu.user.level.progress", it.level.current, it.level.progress)
            }
            field(tr("osu.user.scores"), inline = true) {
                val gc = it.grade_counts
                """
                    <:GradeSSSilver:744300240370139226>: ${gc.ssh}
                    <:GradeSS:744300239946514433>: ${gc.ss}
                    <:GradeSSilver:744300240269475861>: ${gc.sh}
                    <:GradeS:744300240202367017>: ${gc.s}
                    <:GradeA:744300239867084842>: ${gc.a}
                """.trimIndent()
            }
            field(tr("osu.user.playtime"), inline = true) {
                val d = it.play_time.seconds.toJavaDuration()
                val days = d.toDays()

                StringBuilder().apply {
                    if (days != 0L) append("${days}d ")
                    append("${d.toHoursPart()}:${d.toMinutesPart()}:${d.toSecondsPart()}")
                }.toString()
            }
            if (it.rank.global != null || it.rank.country != null) {
                field(tr("osu.user.rank"), inline = true) {
                    tr("osu.user.rank.list", it.global_rank, user.country_code.lowercase(), it.rank.country)
                }
            }
        }

        footer {
            text = tr("osu.user.joined", Date.from(user.join_date?.toJavaInstant()))
        }
    }

}

private fun Arguments.gameModeArg() = defaultingEnumChoice<GameMode> {
    name = "gamemode"
    description = "The selected osu! gamemode"
    typeName = "GameMode"
    defaultValue = GameMode.OSU
}

private fun endpoint(suffix: String) = URI("https", "osu.ppy.sh", "/api/v2/$suffix", null).toURL().toString()

@Serializable
private data class UserStatistics(
    val level: Level,
    val pp: Float,
    val global_rank: Int?,
    val ranked_score: Long,
    val hit_accuracy: Float,
    val play_count: Int,
    val play_time: Int,
    val total_score: Long,
    val maximum_combo: Int,
    val replays_watched_by_others: Int,
    val is_ranked: Boolean,
    val grade_counts: GradeCounts,
    val rank: Rank,
)

@Serializable
private data class GradeCounts(
    val ss: Int,
    val ssh: Int,
    val s: Int,
    val sh: Int,
    val a: Int
)

@Serializable
private data class Level(
    val current: Int,
    val progress: Int,
)

@Serializable
private data class Rank(
    val global: Int? = null,
    val country: Int? = null
)

@Serializable
private data class User(
    /// UserCompact
    // url of user's avatar
    val avatar_url: String,
    // two-letter code representing user's country
    val country_code: String,
    // unique identifier for user
    val id: Int,
    // colour of username/profile highlight, hex code (e.g. #333333)
    val profile_colour: String?,
    // user's display name
    val username: String,
    val statistics: UserStatistics? = null,

    /// User
    val discord: String? = null,
    val join_date: Instant? = null,
    val location: String? = null,
    val playmode: GameMode? = null,
) {
    fun siteUrl(mode: GameMode) = "https://osu.ppy.sh/users/$id/${mode.name.lowercase()}"
}

@Serializable
enum class GameMode(val humanName: String) : InferredChoiceEnum {
    @SerialName("fruits")
    FRUITS("osu!catch"),

    @SerialName("mania")
    MANIA("osu!mania"),

    @SerialName("osu")
    OSU("osu!"),

    @SerialName("taiko")
    TAIKO("osu!taiko")
}

@Serializable
private data class Weight(
    val percentage: Double,
    val pp: Double
)

@Serializable
private data class Beatmap(
    val mode_int: Int,
    val ranked: Int,
    val beatmapset_id: Long,
    val id: Long,
    val total_length: Long,
    val user_id: Long,
    val bpm: Long,
    val count_circles: Long,
    val count_sliders: Long,
    val count_spinners: Long,
    val hit_length: Long,
    val passcount: Long,
    val playcount: Long,
    val drain: Double,
    val difficulty_rating: Double,
    val accuracy: Double,
    val ar: Double,
    val cs: Double,
    val mode: String,
    val status: String,
    val version: String,
    val last_updated: String,
    val deleted_at: String?,
    val url: String,
    val checksum: String,
    val convert: Boolean,
    val is_scoreable: Boolean,
)

@Serializable
private data class Covers(
    val cover: String,
    @SerialName("cover@2x")
    val cover2x: String? = null,
    val card: String,
    @SerialName("card2x")
    val card2x: String? = null,
    val list: String,
    @SerialName("list2x")
    val list2x: String? = null,
    val slimcover: String,
    @SerialName("slimcover2x")
    val slimcover2x: String? = null,
)

@Serializable
private data class BeatmapSet(
    val artist: String,
    val artist_unicode: String,
    val creator: String,
    val preview_url: String,
    val status: String,
    val title: String,
    val title_unicode: String,
    val source: String,
    val covers: Covers,
    val favourite_count: Long,
    val id: Long,
    val offset: Long,
    val play_count: Long,
    val track_id: Long? = null,
    val user_id: Long,
    val nsfw: Boolean,
    val spotlight: Boolean,
    val video: Boolean,
)

@Serializable
private data class Statistics(
    val count_100: Int,
    val count_300: Int,
    val count_50: Int,
    val count_geki: Int,
    val count_katu: Int,
    val count_miss: Int,
)

@Serializable
private data class Score(
    val id: Long,
    val best_id: Long,
    val max_combo: Long,
    val score: Long,
    val user_id: Long,
    val mode_int: Int,
    val accuracy: Double,
    val pp: Double,
    val created_at: Instant,
    val mode: String,
    val rank: String,
    val passed: Boolean,
    val perfect: Boolean,
    val replay: Boolean,
    val mods: Array<String>,
    val weight: Weight,
    val statistics: Statistics,
    val user: User,
    val beatmap: Beatmap,
    val beatmapset: BeatmapSet
)

@Serializable
private data class GetUserScoresRequest(
    val include_fails: String? = "0",
    val mode: String? = null,
    val limit: Int? = null,
    val offset: Int? = null,
)

@Serializable
private data class AuthRequest(
    val client_id: Int,
    val client_secret: String,
    val grant_type: String = "client_credentials",
    val scope: String = "public",
)

@Serializable
private data class ErrorResponse(
    val error: String,
    @SerialName("error_description") val errorDescription: String,
    val hint: String,
    override val message: String,
) : Throwable()

@Serializable
private data class TokenResponse(
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expire: Int,
    @SerialName("access_token") val accessToken: String,
)