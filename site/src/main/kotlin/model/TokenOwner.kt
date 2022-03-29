package model

class TokenOwner(
    val oauthToken: Oauth2Token,
    val partialDiscordUser: PartialDiscordUser
)