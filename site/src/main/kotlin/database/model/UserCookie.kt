package database.model

import me.melijn.ap.cacheable.Cacheable
import me.melijn.ap.createtable.CreateTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

@Cacheable
@CreateTable
object UserCookie : Table("user_cookies") {

    val userId = long("user_id")
    val cookie = text("cookie")
    val created = datetime("created")

    // token info
    val token = text("token")
    val tokenType = text("token_type")
    val expiresInSeconds = long("expires_in_seconds")
    val refreshToken = text("refresh_token")
    val scope = text("scope")

    override val primaryKey: PrimaryKey = PrimaryKey(userId)

    init {
        index("cookie_index", true, cookie)
    }
}