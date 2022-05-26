# melijn-bot
A multipurpose [discord](https://discord.com) bot written in [kotlin](https://kotlinlang.org/)
Some libraries we use:
 - [kord](https://github.com/kordlib/kord) - kotlin discord api wrapper
 - [kord-extensions](https://github.com/Kord-Extensions/kord-extensions) - kord command framework and utils
 - [kord-kommons](https://github.com/ToxicMushroom/kord-kommons) - kord discord bot utilities and exposed SQL code generation utils
 - [ktor](https://ktor.io/) - kotlin http client/server
 - [exposed](https://github.com/JetBrains/Exposed) - kotlin SQL framework from jetbrains (we don't use the DAO's)

## general information
This is the third rewrite of Melijn (links to [previous](https://github.com/ToxicMushroom/Melijn) and [first](https://github.com/ToxicMushroom/melijn-legacy) repositories).
These projects serve as a fun hobby project and learning place for me. Over the years we have grown a lot in size (amount of users) and our current codebase was messy and kinda unmaintainable (over 260 top level commands and about 80 database tables).
This rewrite will keep scalability and maintainability in mind. Our website will also reside in this repository and use ktor. ~~NO MORE JS FRAMEWORK WOOHOO~~.

## Contributions
Contributions are welcome <3
<br>Please follow the following guidelines to make it a nice experience for everyone.
 1. Check if there is an issue for what you want to add (if not create one)
 2. If there is an issue, check that there is no one working on it already (open draft PRS or replies indicating that someone is working on it).
 3. Don't solve multiple github issues in one PR (bug fixes or typos are acceptable)
 4. Follow our coding style and naming conventions.

## Development environment
You will need: 
- a Postgresql server
- a Redis server
- a discord user account, bot user and server

If you want to use docker for your dev environment: [example-docker-compose.yml](./example-docker-compose.yml) 

Optional (related features won't work unless supplied ofcourse):
- Lavalink server
- Spotify developer application tokens
- Other API keys

*Make sure intellij is using UTF-8 for property files, otherwise unicode might replaced with ? in messages.*
*KSP code generation may sometimes print random errors (e.g. -1), just recompile will fix them normally :/*

Starting the bot is done using the main function in [Melijn.kt](./bot/src/main/kotlin/me/melijn/bot/Melijn.kt) at the bottom.

An example configuration can be found here: [./example.env](./example.env)<br>
Copy it to `.env` and place it in the root (same level as example.env)