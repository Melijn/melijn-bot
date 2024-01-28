package me.melijn.bot.commands.thirdparty

import com.kotlindiscord.kord.extensions.DiscordRelayedException
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.application.ApplicationCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.defaultingEnumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.optionalEnumChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.publicSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.*
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.types.respondingPaginator
import com.sksamuel.scrimage.ImmutableImage
import dev.minn.jda.ktx.messages.InlineEmbed
import dev.minn.jda.ktx.messages.InlineMessage
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import me.melijn.apkordex.command.KordExtension
import me.melijn.bot.database.manager.OsuLinkManager
import me.melijn.bot.database.manager.OsuTokenManager
import me.melijn.bot.model.Cell
import me.melijn.bot.model.enums.Alignment
import me.melijn.bot.utils.EnumUtil.lcc
import me.melijn.bot.utils.InferredChoiceEnum
import me.melijn.bot.utils.KordExUtils.bail
import me.melijn.bot.utils.KordExUtils.tr
import me.melijn.bot.utils.TableBuilder
import me.melijn.bot.utils.TimeUtil.format
import me.melijn.bot.utils.image.ImageUtil
import me.melijn.bot.web.api.WebManager
import me.melijn.gen.OsuLinkData
import me.melijn.gen.Settings
import me.melijn.kordkommons.utils.escapeCodeBlock
import net.dv8tion.jda.api.utils.AttachedFile
import net.dv8tion.jda.api.utils.TimeFormat
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.koin.core.component.inject
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
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

            publicSubCommand(::OsuSetPreferredModeArg) {
                name = "preferredmode"
                description = "Set or shows your preferred osu! gamemode"

                action {
                    val mode = arguments.gameMode
                    val currentProfile = linkManager.get(user)
                    val currentModeName = currentProfile.modePreference?.readableName
                    if (mode == null) {
                        respond {
                            content = tr("osu.preferredMode.show", currentModeName)
                        }
                    } else {
                        val updatedProfile = currentProfile.apply {
                            modePreference = mode
                        }
                        linkManager.store(updatedProfile)
                        respond {
                            content = tr("osu.preferredMode.set", mode.readableName)
                        }
                    }
                }
            }

            publicSubCommand(::OsuAccountArg) {
                name = "link"
                description = "Links your discord and osu! account"

                action {
                    val account = arguments.account
                    val token = assertToken()
                    val currentProfile = linkManager.get(user)

                    if (arguments.reset == true) {
                        val updatedProfile = currentProfile.apply { osuId = null }
                        linkManager.store(updatedProfile)
                        respond {
                            content = tr("osu.link.reset")
                        }
                        return@action
                    }

                    if (account == null) {
                        // show current account
                        respond {
                            content = tr("osu.link.show", currentProfile.osuId.toString())
                        }
                        return@action
                    }

                    val osuUser = getUser(account, token, GameMode.OSU)
                    val updatedProfile = currentProfile.apply {
                        osuId = osuUser.id
                    }
                    linkManager.store(updatedProfile)
                    respond {
                        content = tr("osu.link.succeeded")
                    }
                }
            }

            publicSubCommand(::OsuScoreArg) {
                name = "scores"
                description = "View recent, best and firsts plays"

                action {
                    val type = arguments.type
                    val limit = arguments.limit
                    val offset = arguments.offset
                    val includeFails = if (arguments.includeFails) "1" else "0"
                    val (osuSettings, osuId) = assertAccount(arguments.account, arguments.user)
                    val mode = arguments.gameMode ?: osuSettings?.modePreference ?: GameMode.OSU

                    val token = assertToken()
                    val osuUser = getUser(osuId, token, GameMode.OSU)
                    val requestParams = GetUserScoresRequest(includeFails, mode, limit, offset)
                    val scores = getScores(osuUser.id, type, token, requestParams)
                        .getOrElse { bail(it.message ?: "wfjskld") }
                        .filter { it.beatmap != null && it.beatmapset != null && it.user != null }

                    if (scores.isEmpty()) {
                        respond {
                            content = tr(
                                "osu.scores.noScores",
                                osuUser.username.escapeCodeBlock(),
                                type.readableName.lowercase(),
                                mode.readableName.lowercase()
                            )
                        }
                        return@action
                    }

                    respondingPaginator {
                        for ((i, score) in scores.withIndex()) {
                            val beatmapSet = score.beatmapset ?: bail("This score isn't set on a beatmap set ???")
                            val stats = score.statistics
                            val columns = mutableListOf("300", "100", "50", "miss")
                            val values = mutableListOf(
                                Cell(
                                    stats.count_300.toString(), Alignment.RIGHT
                                ),
                                Cell(stats.count_100.toString(), Alignment.RIGHT),
                                Cell(stats.count_50.toString(), Alignment.RIGHT),
                                Cell(stats.count_miss.toString(), Alignment.RIGHT)
                            )

                            // In mania the combo counters are just extra hit accuracy markers
                            if (mode == GameMode.MANIA) {
                                columns.add(1, "200喝")
                                columns.add(0, "300激")
                                values.add(1, Cell(score.statistics.count_katu.toString(), Alignment.RIGHT))
                                values.add(0, Cell(score.statistics.count_geki.toString(), Alignment.RIGHT))
                            }
                            val table =
                                TableBuilder().setColumns(*columns.toTypedArray()).addRow(*values.toTypedArray())
                                    .build(false).first()

                            val osuScoreUser = score.user ?: bail("a score was set by no user")
                            val beatmap = score.beatmap ?: bail("a score was not set on a beatmap")
                            page {
                                author {
                                    name = osuScoreUser.username
                                    url = "https://osu.ppy.sh/users/" + score.user_id
                                    iconUrl = osuScoreUser.avatar_url
                                }
                                title = "Score #${i}"
                                url = "https://osu.ppy.sh/scores/osu/${score.id}"
                                field {
                                    name = tr("osu.scores.beatmapInfoTitle")
                                    value = tr(
                                        "osu.scores.beatmapInfo",
                                        beatmapSet.title,
                                        "https://osu.ppy.sh/beatmapsets/" + beatmapSet.id,
                                        beatmapSet.creator,
                                        "https://osu.ppy.sh/users/" + beatmapSet.user_id,
                                        beatmapSet.artist,
                                        beatmap.bpm,
                                        beatmap.difficulty_rating,
                                        beatmapSet.favourite_count,
                                        beatmapSet.play_count,
                                        (beatmapSet.favourite_count / beatmapSet.play_count.toDouble()) * 100
                                    )
                                    inline = false
                                }
                                field {
                                    name = tr("osu.scores.scoreInfoTitle")
                                    value = tr(
                                        "osu.scores.scoreInfo",
                                        score.accuracy * 100,
                                        score.rank?.getEmote() ?: "?",
                                        score.pp,
                                        score.max_combo,
                                        score.score,
                                        score.mods.joinToString(),
                                        mode.lcc(),
                                        stats.count_geki,
                                        stats.count_katu,
                                        table,
                                        TimeFormat.DATE_TIME_LONG.format(score.created_at)

                                    )
                                    inline = false
                                }

                                thumbnail = beatmapSet.covers.list2x ?: beatmapSet.covers.list
                            }
                        }
                    }.send()
                }
            }

            publicSubCommand(::UserAndModeArg) {
                name = "profile"
                description =
                    "View an osu! profile, will default to your linked profile if the target is left unspecified."

                action {
                    val (osuSettings, osuId) = assertAccount(arguments.account, arguments.user)

                    val gameMode = arguments.gameMode ?: osuSettings?.modePreference ?: GameMode.OSU
                    val token = assertToken()

                    val osuUser = getUser(osuId, token, gameMode)

                    respond {
                        embed {
                            presentUser(osuUser, gameMode)
                        }
                    }
                }
            }
        }
    }

    context(ApplicationCommandContext)
    private suspend fun assertAccount(
        osuId: String?,
        user: net.dv8tion.jda.api.entities.User?
    ): Pair<OsuLinkData?, String> {
        if (osuId != null) return null to osuId

        val invoker: net.dv8tion.jda.api.entities.User = this@ApplicationCommandContext.user
        val target = user ?: invoker
        val authorOsuSettings = linkManager.get(target)
        val osuSettings =
            linkManager.get(target).takeIf { it.modePreference != null } ?: authorOsuSettings

        val assertedOsuId = osuSettings.osuId?.toString() ?: bail(
            if (target == invoker) tr("osu.profile.you.noLink")
            else tr("osu.profile.other.noLink", target.asMention)
        )
        return osuSettings to assertedOsuId
    }


    /**
     * @param account a string containing an osu username or userId
     * @param gameMode the gameMode you want information about for the [account]
     * @param token osu api oauth token
     *
     * @throws DiscordRelayedException when no user is found for the [account]
     *
     * @return Osu [User] information
     */
    private suspend fun CommandContext.getUser(
        account: String, token: String, gameMode: GameMode
    ): User = get<User, Unit>(
        endpoint("users/$account/${gameMode.name.lowercase()}"), Unit, token
    ).getOrElse { bail(tr("osu.noUser")) }

    private suspend fun getScores(user: Int, type: ScoreType, token: String, req: GetUserScoresRequest) =
        get<List<Score>, GetUserScoresRequest>(
            endpoint("users/$user/scores/${type.lcc()}"), req, token
        )

    inner class OsuSetPreferredModeArg : Arguments() {
        val gameMode by optionalEnumChoice<GameMode> {
            name = "gamemode"
            description = "If provided, sets your preferred osu! gamemode "
            typeName = "gamemode"
        }
    }

    inner class OsuAccountArg : Arguments() {
        val account by optionalString {
            name = "account"
            description = "osu! account username or ID"
        }
        val reset by optionalBoolean {
            name = "reset"
            description = "unlinks your osu account"
        }
    }

    enum class ScoreType : InferredChoiceEnum {
        FIRSTS, RECENT, BEST
    }

    inner class OsuScoreArg : Arguments() {
        val user by optionalUser {
            name = "user"
            description = "Discord user of whom to view osu! profile of"
        }
        val account by optionalString {
            name = "account"
            description = "osu! account username or ID"
        }
        val gameMode by optionalEnumChoice<GameMode> {
            name = "gamemode"
            description = "The selected osu! gamemode"
            typeName = "GameMode"
        }
        val type by defaultingEnumChoice<ScoreType> {
            defaultValue = ScoreType.BEST
            name = "scoretype"
            description = "the types of scores you want to fetch (default: ${defaultValue})"
            typeName = "scoretype"
        }
        val offset by defaultingInt {
            defaultValue = 0
            name = "offset"
            description = "offset for the provided scores (default: ${defaultValue})"
        }
        val limit by defaultingInt {
            defaultValue = 5
            name = "limit"
            description = "limit for the amount of scores to fetch, more can take longer (default: ${defaultValue})"
        }
        val includeFails by defaultingBoolean {
            defaultValue = false
            name = "includefails"
            description = "whether the scores should include fails (default: ${defaultValue})"
        }
    }

    internal class UserAndModeArg : Arguments() {
        val user by optionalUser {
            name = "user"
            description = "Discord user of whom to view osu! profile of"
        }
        val account by optionalString {
            name = "account"
            description = "osu! account username or ID"
        }
        val gameMode by optionalEnumChoice<GameMode> {
            name = "gamemode"
            description = "The selected osu! gamemode (default: ${GameMode.OSU.readableName})"
            typeName = "GameMode"
        }
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
        endpoint: String, body: B, token: String, params: HttpRequestBuilder.() -> Unit = {}
    ): Result<R> = webManager.httpClient.get(endpoint) {
        contentType(ContentType.Application.Json)
        header("Authorization", "Bearer $token")
        params()
        val json = Json.encodeToJsonElement(body)
        json.jsonObject.forEach { t, u ->
            val prim = try {
                u.jsonPrimitive
            } catch (t: IllegalArgumentException) {
                null
            }
            val value: Any =
                prim?.booleanOrNull ?: prim?.intOrNull ?: prim?.longOrNull ?: prim?.floatOrNull ?: prim?.doubleOrNull
                ?: prim?.contentOrNull ?: return@forEach
            parameter(t, value)
        }
    }.catchErroneousResponse()

    private suspend inline fun <reified B, reified R> post(
        endpoint: String, body: B, token: String? = null
    ): Result<R> = webManager.httpClient.post(endpoint) {
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

    context(InlineEmbed, InlineMessage<MessageCreateData>, CommandContext)
    private suspend fun presentUser(user: User, gameMode: GameMode) {
        thumbnail = user.avatar_url

        title = tr("osu.user.title", user.username, gameMode.readableName)
        url = user.siteUrl(gameMode)
        user.profile_colour?.let { color = Color.decode(it).rgb }

        user.statistics?.let {
            field(tr("osu.user.gamesPlayed"), tr("osu.user.gamesPlayed.format", it.play_count), true)
            field(tr("osu.user.accuracy"), tr("osu.user.accuracy.format", it.hit_accuracy), true)
            field(tr("osu.user.level"), tr("osu.user.level.progress", it.level.current, it.level.progress), true)
            field(tr("osu.user.scores"), inline = true) {
                val gc = it.grade_counts
                value = """
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

                value = StringBuilder().apply {
                    if (days != 0L) append("${days}d ")
                    append("${d.toHoursPart()}:${d.toMinutesPart()}:${d.toSecondsPart()}")
                }.toString()
            }
            if (it.rank.global != null || it.rank.country != null) {
                field(tr("osu.user.rank"), inline = true) {
                    value = tr("osu.user.rank.list", it.global_rank, user.country_code.lowercase(), it.rank.country)
                }
            }
        }

        user.rank_history?.let { points ->
            val barr = drawRankHistoryWithSplines(points.data.toMutableList())
            val bais = ByteArrayInputStream(barr)
            files.plusAssign(AttachedFile.fromData(bais, "image.png"))
            image = "attachment://image.png"

            footer {
                name = tr("osu.user.joined", Date.from(user.join_date?.toJavaInstant()))
            }
        }
    }


    companion object {

        fun make_spline(data: Array<Array<Double>>): Array<Array<Double>> {
            // Returns sets of coefficients to create a cubic spline through points
            // contained in data. In each region x_i < x < x_i+1, the spline is
            // given by a_i*(x-x_i)^3 + b_i*(x-x_i)^2 + c_i*(x-x_i) + d_i.
            // The output is is a list of lists make_spline = [a,b,c,d].
            // Solve for ssp in the matrix equation T*ssp = r.
            // at, bt, and ct are the elements of the tridiagonal matrix T.
            // r is the column vector on the right-hand side.
            // ssd holds the values of the second derivative of the spline polynomial
            // at each data point.
            val bt = ArrayList<Double>(data.size).also { it.add(1.0) }
            val at = ArrayList<Double>(data.size).also { it.add(0.0) }
            val ct = ArrayList<Double>(data.size).also { it.add(data[1][0] - data[0][0]) }
            val r = ArrayList<Double>(data.size).also { it.add(0.0) }
            for (i in 1 until data.size - 1) {
                bt.add(2 * (data[i + 1][0] - data[i - 1][0]))
                ct.add(data[i + 1][0] - data[i][0])
                at.add(data[i][0] - data[i - 1][0])
                var r1 = (data[i + 1][1] - data[i][1]) / (data[i + 1][0] - data[i][0])
                r1 -= (data[i][1] - data[i - 1][1]) / (data[i][0] - data[i - 1][0])
                r.add(6 * r1)
            }

            at.add(data[data.size - 1][0] - data[data.size - 2][0])
            bt.add(1.0)
            ct.add(0.0)
            r.add(0.0)

            // Solve the matrix equation for ssd.beta = [bt[0]] # beta and rho are short-hand variables.
            val beta = Array(data.size) { 0.0 }.also { it[0] = bt[0] }
            val rho = Array(data.size) { 0.0 }.also { it[0] = r[0] }
            val ssd = Array(data.size) { 0.0 }
            for (j in 1 until data.size) {
                beta[j] = (bt[j] - at[j] * ct[j - 1] / beta[j - 1])
                rho[j] = (r[j] - at[j] * rho[j - 1] / beta[j - 1])
            }

            ssd[data.size - 1] = rho[data.size - 1] / beta[data.size - 1]
            for (j in (2..data.size - 2))
                ssd[data.size - j] =
                    (rho[data.size - j] - ct[data.size - j] * ssd[data.size - j + 1]) / beta[data.size - j]


            // Spline has been determined . Prepare output.a = []
            val a = Array(data.size) { 0.0 }
            val b = Array(data.size) { 0.0 }
            val c = Array(data.size) { 0.0 }
            val d = Array(data.size) { 0.0 }

            for (i in 0 until data.size - 1) {
                d[i] = data[i][1]
                b[i] = ssd[i] / 2.0
                a[i] = (ssd[i + 1] - ssd[i]) / (6 * (data[i + 1][0] - data[i][0]))
                var c1 = (data[i + 1][1] - data[i][1]) / (data[i + 1][0] - data[i][0])
                c1 -= ((data[i + 1][0] - data[i][0]) * (ssd[i + 1] + 2 * ssd[i]) / 6)
                c[i] = c1
            }

            return arrayOf(a, b, c, d)
        }

        fun drawRankHistoryWithSplines(points: MutableList<Long>): ByteArray {
            points.add(points.last())
            val yMax = 300
            val xOff = 20
            val yOff = 20
            val xMax = 800
            val canvas = ImmutableImage.create(xMax, yMax, BufferedImage.TYPE_4BYTE_ABGR)
            val g2d = canvas.awt().createGraphics()
            g2d.background = Color.decode("#1C1719")
            g2d.paint = Color.decode("#1C1719")
            g2d.fillRect(0, 0, xMax, yMax)
            g2d.paint = Color.DARK_GRAY
            for (i in 0 until 8) {
                g2d.drawLine(i * 100 + xOff, 0, i * 100 + xOff, yMax - yOff)
                g2d.drawLine(xOff, i * 100 - yOff, xMax, i * 100 - yOff)
            }

            val maxPoint = points.max()
            val minPoint = points.min()

            g2d.paint = Color.RED
            val dataSet = Array(points.size) { Array(2) { 0.0 } }
            for ((i, point) in points.withIndex()) {
                val scaledX = (i / (points.size - 1).toDouble()) * (xMax - xOff) + xOff
                val scaledY = ((point - minPoint) / (maxPoint - minPoint).toDouble()) * (yMax - yOff)
                dataSet[i][0] = scaledX
                dataSet[i][1] = scaledY
            }

            val spline = make_spline(dataSet)
            val a = spline[0]
            val b = spline[1]
            val c = spline[2]
            val d = spline[3]

            var i = 0
            var prevY = -1
            for (x in (xOff until xMax)) {
                while (i < (dataSet.size - 1) && dataSet[i + 1][0] < x) i++
                val xdiff = x - dataSet[i][0]
                val value = a[i] * xdiff.pow(3) + b[i] * xdiff.pow(2) + c[i] * xdiff + d[i]
                val splinedY = max(0, min(value.toInt(), yMax - 1))
                val from = if (prevY != -1) prevY else splinedY
                g2d.drawLine(x, from, x, splinedY)
                prevY = splinedY
            }

            val baos = ByteArrayOutputStream()
            ImageUtil.writeSafe(canvas.awt(), "png", baos)
            return baos.toByteArray()
        }
    }
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
    val ss: Int, val ssh: Int, val s: Int, val sh: Int, val a: Int
)

@Serializable
private data class Level(
    val current: Int,
    val progress: Int,
)

@Serializable
private data class Rank(
    val global: Int? = null, val country: Int? = null
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
    val monthly_playcounts: List<MomentCount>? = null,
    val rank_history: RankHistory? = null
) {
    fun siteUrl(mode: GameMode) = "https://osu.ppy.sh/users/$id/${mode.name.lowercase()}"
}

@Serializable
data class RankHistory(
    val mode: String,
    val data: List<Long>
)

@Serializable
data class MomentCount(
    val start_date: String,
    val count: Long
)

@Serializable
enum class GameMode : ChoiceEnum {
    @SerialName("fruits")
    FRUITS {
        override val readableName: String = "osu!catch"
    },

    @SerialName("mania")
    MANIA {
        override val readableName: String = "osu!mania"
    },

    @SerialName("osu")
    OSU {
        override val readableName: String = "osu!"
    },

    @SerialName("taiko")
    TAIKO {
        override val readableName: String = "osu!taiko"
    }
}

@Serializable
enum class ScoreRank(val emoteName: String, val emoteId: Long) : InferredChoiceEnum {
    @SerialName("SSSilver")
    SS_SILVER("SSSilver", 744300240370139226),

    @SerialName("SS")
    SS("SS", 744300239946514433),

    @SerialName("SSilver")
    S_SILVER("GradeSSilver", 744300240269475861),

    @SerialName("S")
    S("GradeS", 744300240202367017),

    @SerialName("A")
    A("GradeA", 744300239867084842),

    @SerialName("B")
    B("GradeB", 744300240114417665),

    @SerialName("C")
    C("GradeC", 744300239954903062),

    @SerialName("D")
    D("GradeD", 744300240248635503),

    @SerialName("F")
    F("GradeF", 982218246604337192);

    fun getEmote(): String {
        return "<:${emoteName}:$emoteId>"
    }
}

@Serializable
private data class Weight(
    val percentage: Double, val pp: Double
)

@Serializable
private data class Beatmap(
    val mode_int: Int,
    val ranked: Int,
    val beatmapset_id: Long,
    val id: Long,
    val total_length: Long,
    val user_id: Long,
    val bpm: Double,
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
    @SerialName("cover@2x") val cover2x: String? = null,
    val card: String,
    @SerialName("card2x") val card2x: String? = null,
    val list: String,
    @SerialName("list2x") val list2x: String? = null,
    val slimcover: String,
    @SerialName("slimcover2x") val slimcover2x: String? = null,
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
    val best_id: Long? = null,
    val max_combo: Long,
    val score: Long,
    val user_id: Long,
    val mode_int: Int,
    val accuracy: Double,
    val pp: Double = 0.0,
    val created_at: Instant,
    val mode: String,
    val rank: ScoreRank? = null,
    val passed: Boolean,
    val perfect: Boolean,
    val replay: Boolean,
    val mods: Array<String>,
    val weight: Weight = Weight(0.0, 0.0),
    val statistics: Statistics,
    val user: User? = null,
    val beatmap: Beatmap? = null,
    val beatmapset: BeatmapSet? = null
)

@Serializable
private data class GetUserScoresRequest(
    val include_fails: String? = "0",
    val mode: GameMode? = null,
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
    val error: String = "",
    @SerialName("error_description") val errorDescription: String = "",
    val hint: String = "",
    override val message: String = "null",
) : Throwable()

@Serializable
private data class TokenResponse(
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expire: Int,
    @SerialName("access_token") val accessToken: String,
)