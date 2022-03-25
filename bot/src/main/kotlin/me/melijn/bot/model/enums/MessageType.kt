package me.melijn.bot.model.enums

enum class MessageType(val base: String, val messageTemplate: MessageTemplate? = null) {

    // Join/Leave things
    PRE_VERIFICATION_JOIN("PreVerificationJoin"),
    PRE_VERIFICATION_LEAVE("PreVerificationLeave"),
    JOIN("Join"),
    LEAVE("Leave"),
    BANNED("Banned"),
    KICKED("Kicked"),

    // Special Events
    BIRTHDAY("Birthday"),
    BOOST("Boost"),

    // Punishments
    BAN("Ban", MessageTemplate.BAN),
    TEMP_BAN("TempBan", MessageTemplate.TEMP_BAN),
    MASS_BAN("MassBan", MessageTemplate.MASS_BAN),
    SOFT_BAN("SoftBan", MessageTemplate.SOFT_BAN),
    UNBAN("Unban", MessageTemplate.UNBAN),

    MUTE("Mute", MessageTemplate.MUTE),
    TEMP_MUTE("TempMute", MessageTemplate.TEMP_MUTE),
    UNMUTE("Unmute", MessageTemplate.UNMUTE),

    MASS_KICK("MassKick", MessageTemplate.MASS_KICK),
    KICK("Kick", MessageTemplate.KICK),
    WARN("Warn", MessageTemplate.WARN),

    MASS_BAN_LOG("MassBanLog", MessageTemplate.MASS_BAN_LOG),
    BAN_LOG("BanLog", MessageTemplate.BAN_LOG),
    TEMP_BAN_LOG("TempBanLog", MessageTemplate.TEMP_BAN_LOG),
    SOFT_BAN_LOG("SoftBanLog", MessageTemplate.SOFT_BAN_LOG),
    UNBAN_LOG("UnbanLog", MessageTemplate.UNBAN_LOG),

    MUTE_LOG("MuteLog", MessageTemplate.MUTE_LOG),
    TEMP_MUTE_LOG("TempMuteLog", MessageTemplate.TEMP_MUTE_LOG),
    UNMUTE_LOG("UnmuteLog", MessageTemplate.UNMUTE_LOG),

    MASS_KICK_LOG("MassKickLog", MessageTemplate.MASS_KICK_LOG),
    KICK_LOG("KickLog", MessageTemplate.KICK_LOG),
    WARN_LOG("WarnLog", MessageTemplate.WARN_LOG),

    VERIFICATION_LOG("VerificationLog");


    val text: String = "${base}Message"

    companion object {

        fun getMatchingTypesFromNode(node: String): List<MessageType> {
            return values().filter { msgType ->
                node.equals("all", true)
                    || msgType.text.equals(node, true)
                    || msgType.base.equals(node, true)
                    || msgType.toString().equals(node, true)
            }
        }
    }
}