package me.melijn.bot.model.enums

enum class PermState(
    val past: String
) {
    ALLOW("Allowed"),
    DEFAULT("Reset"),
    DENY("Denied");
}