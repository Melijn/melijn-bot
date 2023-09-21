import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("application")
    id("com.apollographql.apollo3") version "3.8.2"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("jvm") version "1.8.20"
    id("com.google.devtools.ksp") version "1.8.20-1.0.10"
    kotlin("plugin.serialization") version "1.8.20"
}

application.mainClass.set("me.melijn.bot.MelijnKt")
group = "me.melijn.bot"
version = "0.0.1-SNAPSHOT"

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

apollo {
    service("anilist") {
        srcDir("src/main/graphql/me/melijn/melijnbot/anilist")
        packageName.set("me.melijn.melijnbot.anilist")
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")

    maven("https://reposilite.melijn.com/snapshots")
    maven("https://reposilite.melijn.com/shitpack")

    maven("https://m2.duncte123.dev/releases")

    // pooppack mirror
    maven("https://nexus.melijn.com/repository/jitpack/")
}

val jackson = "2.14.2" // https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-core

val ktor = "2.3.3"   // https://mvnrepository.com/artifact/io.ktor/ktor-client-cio
val apollo = "3.8.2" // https://mvnrepository.com/artifact/com.apollographql.apollo3/apollo-runtime
val kotlinX = "1.7.2" // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
val kotlin = "1.8.20"
val scrimage = "4.0.34"

val jda = "5.0.0-beta.13"
val kordEx = "1.6.3-SNAPSHOT"
val kordKommons = "0.0.9-SNAPSHOT"

dependencies {
    implementation("net.dv8tion:JDA:$jda")
    implementation("com.kotlindiscord.kord.extensions:kord-extensions:$kordEx")
    ksp("com.kotlindiscord.kord.extensions:annotation-processor:$kordEx")

    // https://mvnrepository.com/artifact/org.scilab.forge/jlatexmath
    implementation("org.scilab.forge:jlatexmath:1.0.7")

    implementation("dev.schlaubi.lavakord", "jda", "5.1.7")

    implementation("me.melijn.kommons:kommons:$kordKommons")
    implementation("me.melijn.kommons:kord-kommons:$kordKommons")
    implementation("me.melijn.kommons:redgres-kommons:$kordKommons")

    // Annotation processors
    val apBot = project(":bot-annotation-processors")
    val apKord = "me.melijn.kommons:annotation-processor:$kordKommons"
    val apKordex = "me.melijn.kommons:annotation-processor-kordex:$kordKommons"
    val apRedgres = "me.melijn.kommons:annotation-processor-redgres:$kordKommons"
    implementation(apBot)
    implementation(apKord)
    implementation(apKordex)
    implementation(apRedgres)
    ksp(apBot)
    ksp(apKord)
    ksp(apKordex)
    ksp(apRedgres)

    // error reporting
    implementation("io.sentry:sentry:6.2.1")

    // https://mvnrepository.com/artifact/club.minnced/discord-webhooks
    implementation("club.minnced:discord-webhooks:0.8.4")
    // https://github.com/freya022/JEmojis
    implementation("com.github.ToxicMushroom:JEmojis:a8c82848f166893f67251c741579c74c80fbb2dd")
    // cron-utils
    implementation("com.cronutils:cron-utils:9.2.1")

    // required for scriptDef.kt
    implementation(kotlin("scripting-common"))
    implementation(kotlin("scripting-jvm"))
    implementation(kotlin("scripting-dependencies"))
    implementation(kotlin("scripting-dependencies-maven"))

    // required for host.kt
    implementation(kotlin("scripting-jvm-host"))

    // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib-jdk8
    implementation(kotlin("stdlib-jdk8"))

    // https://nexus.melijn.com/#browse/browse:maven-public:me%2Fmelijn%2Fjagtag
    // implementation("me.melijn.jagtag:JagTag-Kotlin:2.2.1")

    // Database
    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-core
    val exposed = "0.41.1"
    implementation("org.jetbrains.exposed:exposed-core:$exposed")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposed")
    implementation("org.jetbrains.exposed:exposed-spring-boot-starter:$exposed")

    // https://search.maven.org/artifact/com.zaxxer/HikariCP
    implementation("com.zaxxer:HikariCP:5.0.1")

    // https://mvnrepository.com/artifact/org.postgresql/postgresql
    implementation("org.postgresql:postgresql:42.6.0")

    // expiring map, https://search.maven.org/artifact/net.jodah/expiringmap
    implementation("net.jodah:expiringmap:0.5.10")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinX")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinX")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:${kotlinX}")

    // https://search.maven.org/artifact/org.jetbrains.kotlinx/kotlinx-datetime
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-jdk8
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$kotlinX")

    // https://duncte123.jfrog.io/ui/packages/gav:%2F%2Fme.duncte123:weebJava
    implementation("me.duncte123:weebJava:3.0.1_7")

    // https://mvnrepository.com/artifact/se.michaelthelin.spotify/spotify-web-api-java
    implementation("se.michaelthelin.spotify:spotify-web-api-java:8.0.0")

    // https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
    implementation("ch.qos.logback:logback-classic:1.4.8")


    // https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-core
    implementation("com.fasterxml.jackson.core:jackson-core:$jackson")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jackson")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")

    // https://mvnrepository.com/artifact/io.ktor/ktor-client-cio
    implementation("io.ktor:ktor:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    implementation("io.ktor:ktor-client-logging:$ktor")

    // Ktor Client
    implementation("io.ktor:ktor-client-okhttp:$ktor")
    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")

    // Ktor Server
    implementation("io.ktor:ktor-server-netty:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor")

    // https://nexus.melijn.com/#browse/browse:maven-public:me%2Fmelijn%2Fgifdecoder
    implementation("com.github.zh79325:open-gif:1.0.4")
    implementation("com.sksamuel.scrimage:scrimage-core:$scrimage")
    implementation("com.sksamuel.scrimage:scrimage-filters:$scrimage")
    implementation("com.sksamuel.scrimage:scrimage-webp:$scrimage")
    implementation("com.sksamuel.scrimage:scrimage-formats-extra:$scrimage")

    // https://nexus.melijn.com/#browse/browse:maven-public:me%2Fmelijn%2Fjikankt
    // implementation("me.melijn.jikankt:JikanKt:1.3.2")

    // https://mvnrepository.com/artifact/org.mariuszgromada.math/MathParser.org-mXparser
    implementation("org.mariuszgromada.math:MathParser.org-mXparser:5.2.1")

    // https://mvnrepository.com/artifact/com.apollographql.apollo3/apollo-runtime
    implementation("com.apollographql.apollo3:apollo-runtime:$apollo")

    // https://mvnrepository.com/artifact/io.lettuce/lettuce-core
    implementation("io.lettuce:lettuce-core:6.2.4.RELEASE")

    // https://github.com/cdimascio/dotenv-kotlin
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    // https://github.com/furstenheim/copy-down
    implementation("io.github.furstenheim:copy_down:1.0")

    testImplementation(kotlin("test"))
}

ksp {
    arg("ap_package", "me.melijn.gen")
    arg("ap_bot_package", "me.melijn.gen.uselimits")
    arg("ap_kordex_package", "me.melijn.gen")
    arg("ap_redgres_package", "me.melijn.gen")
    arg("ap_redgres_redis_key_prefix", "melijn:")
    arg(
        "ap_imports",
        "import com.kotlindiscord.kord.extensions.koin.KordExKoinComponent;import org.koin.core.component.get;import org.koin.core.parameter.ParametersHolder;"
    )
    arg("ap_interfaces", "KordExKoinComponent")
    arg("ap_init_placeholder", "get<%className%> { ParametersHolder() }")
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
    sourceSets.test {
        kotlin.srcDir("build/generated/ksp/test/kotlin")
    }
}

tasks {
    withType(JavaCompile::class) {
        options.encoding = "UTF-8"
    }
    withType(KotlinCompile::class) {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xcontext-receivers",
                "-Xskip-prerelease-check"
            )
        }
    }

    shadowJar {
        from("build/generated/ksp/main/kotlin")
        isZip64 = true
        mergeServiceFiles()
        archiveFileName.set("melijn.jar")
    }

    test {
        useJUnitPlatform()
    }
}
