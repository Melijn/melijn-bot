package database.model

import me.melijn.apredgres.cacheable.Cacheable
import me.melijn.apredgres.createtable.CreateTable
import org.jetbrains.exposed.sql.Table

@Cacheable
@CreateTable
object UserData : Table("user_data") {

    val userId = long("user_id")
    val username = text("username")
    val discriminator = text("discriminator")
    val avatarUrl = text("avatar_url").nullable()

    override val primaryKey: PrimaryKey = PrimaryKey(userId)
}