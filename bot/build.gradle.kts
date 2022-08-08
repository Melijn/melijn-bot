import com.apollographql.apollo.gradle.api.ApolloExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("application")
    id("com.apollographql.apollo") version "2.5.11"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    kotlin("jvm") version "1.7.10"
    id("com.google.devtools.ksp") version "1.7.10-1.0.6"
    kotlin("plugin.serialization") version "1.7.10"
}

application.mainClass.set("me.melijn.bot.MelijnKt")
group = "me.melijn.bot"
version = "0.0.1-SNAPSHOT"

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
}

configure<ApolloExtension> {
    generateKotlinModels.set(true) // or false for Java models

    service("anilist") {
        rootPackageName.set("me.melijn.melijnbot.anilist")
        sourceFolder.set("me/melijn/melijnbot/anilist")
    }

    customTypeMapping.map {
        ("StartDate" to "me.melijn.melijnbot.internals.models.AnilistDateKt")
        ("MediaFragment.StartDate" to "me.melijn.melijnbot.internals.models.AnilistDateKt")
    }
}

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")

    maven("https://maven.kotlindiscord.com/repository/maven-snapshots/")
    maven("https://maven.kotlindiscord.com/repository/maven-releases/")
    maven("https://nexus.melijn.com/repository/maven-public/")
    maven("https://nexus.melijn.com/repository/jcenter-mirror/")
    mavenLocal()
    maven("https://duncte123.jfrog.io/artifactory/maven")

    // pooppack mirror
    maven("https://nexus.melijn.com/repository/jitpack/")
}

val jackson = "2.13.2" // https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-core
val ktor = "2.0.3"   // https://mvnrepository.com/artifact/io.ktor/ktor-client-cio
val apollo = "2.5.11" // https://mvnrepository.com/artifact/com.apollographql.apollo/apollo-runtime
val kotlinX = "1.6.4" // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
val kotlin = "1.7.10"
val scrimage = "4.0.22"

// val kord = "0.8.0-M13"
val kordEx = "1.5.5-SNAPSHOT"
val kordKommons = "1.3.0"
val apKordVersion = "0.2.2"
val apKordex = "0.1.0"
val redgresKommons = "0.1.0"

dependencies {
    // implementation("dev.kord:kord-core:$kord")   let kord-ex handle kord version
    implementation("com.kotlindiscord.kord.extensions:kord-extensions:$kordEx")
    ksp("com.kotlindiscord.kord.extensions:annotation-processor:$kordEx")

    // https://mvnrepository.com/artifact/org.scilab.forge/jlatexmath
    implementation("org.scilab.forge:jlatexmath:1.0.7")

    implementation("dev.schlaubi.lavakord", "kord", "3.6.3")

    implementation("me.melijn.kordkommons:kommons:$kordKommons")
    implementation("me.melijn.kordkommons:redgres-kommons:$redgresKommons")

    // Annotation processors
    val apKord = "me.melijn.kordkommons:ap:$apKordVersion"
    val apKordex = "me.melijn.kordkommons:apkordex:$apKordex"
    implementation(apKord) {
        exclude("me.melijn.kordkommons", "kommons")
    }

    implementation(apKordex)
    ksp(apKord)
    ksp(apKordex)

    implementation("io.sentry:sentry:6.2.1")

    // https://mvnrepository.com/artifact/club.minnced/discord-webhooks
    implementation("club.minnced:discord-webhooks:0.8.2")
    // https://github.com/freya022/JEmojis
    implementation("com.github.ToxicMushroom:JEmojis:a8c82848f166893f67251c741579c74c80fbb2dd")


    api("org.jetbrains.kotlin:kotlin-script-util:$kotlin")
    api("org.jetbrains.kotlin:kotlin-compiler:$kotlin")
    api("org.jetbrains.kotlin:kotlin-scripting-compiler:$kotlin")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib-jdk8
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin")

    // https://nexus.melijn.com/#browse/browse:maven-public:me%2Fmelijn%2Fjagtag
    implementation("me.melijn.jagtag:JagTag-Kotlin:2.2.1")

    // Database
    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-core
    val exposed = "0.38.2"
    implementation("org.jetbrains.exposed:exposed-core:$exposed")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposed")
    implementation("org.jetbrains.exposed:exposed-spring-boot-starter:$exposed")

    // https://search.maven.org/artifact/com.zaxxer/HikariCP
    implementation("com.zaxxer:HikariCP:5.0.1")

    // https://mvnrepository.com/artifact/org.postgresql/postgresql
    implementation("org.postgresql:postgresql:42.4.0")

    // expiring map, https://search.maven.org/artifact/net.jodah/expiringmap
    implementation("net.jodah:expiringmap:0.5.10")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinX")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$kotlinX")

    // https://search.maven.org/artifact/org.jetbrains.kotlinx/kotlinx-datetime
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-jdk8
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$kotlinX")

    // https://duncte123.jfrog.io/ui/packages/gav:%2F%2Fme.duncte123:weebJava
    implementation("me.duncte123:weebJava:3.0.1_4")

    // https://mvnrepository.com/artifact/se.michaelthelin.spotify/spotify-web-api-java
    implementation("se.michaelthelin.spotify:spotify-web-api-java:7.1.0")

    // https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
    implementation("ch.qos.logback:logback-classic:1.2.11")


    // https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-core
    implementation("com.fasterxml.jackson.core:jackson-core:$jackson")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jackson")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jackson")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")

    // https://mvnrepository.com/artifact/io.ktor/ktor-client-cio
    implementation("io.ktor:ktor:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    implementation("io.ktor:ktor-client-logging:$ktor")

    // Ktor Client
    implementation("io.ktor:ktor-client-okhttp:$ktor")
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
    implementation("me.melijn.jikankt:JikanKt:1.3.2")

    // https://mvnrepository.com/artifact/org.mariuszgromada.math/MathParser.org-mXparser
    implementation("org.mariuszgromada.math:MathParser.org-mXparser:5.0.3")

    // https://mvnrepository.com/artifact/com.apollographql.apollo/apollo-runtime
    implementation("com.apollographql.apollo:apollo-runtime:$apollo")
    implementation("com.apollographql.apollo:apollo-coroutines-support:$apollo")

    // https://mvnrepository.com/artifact/io.lettuce/lettuce-core
    implementation("io.lettuce:lettuce-core:6.1.8.RELEASE")

    // https://github.com/cdimascio/dotenv-kotlin
    implementation("io.github.cdimascio:dotenv-kotlin:6.2.2")

    // https://github.com/furstenheim/copy-down
    implementation("io.github.furstenheim:copy_down:1.0")
}

ksp {
    arg("apkordex_package", "me.melijn.gen")
    arg("ap_package", "me.melijn.gen")
    arg("ap_imports", "import com.kotlindiscord.kord.extensions.koin.KordExKoinComponent;import org.koin.core.component.get;import org.koin.core.parameter.ParametersHolder;")
    arg("ap_interfaces", "KordExKoinComponent")
    arg("ap_init_placeholder", "get<%className%> { ParametersHolder() }")
    arg("ap_redis_key_prefix", "melijn:")
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
            jvmTarget = "16"
            freeCompilerArgs = listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xcontext-receivers"
            )
        }
    }

    shadowJar {
        from("build/generated/ksp/main/kotlin")
        isZip64 = true
        mergeServiceFiles()
        archiveFileName.set("melijn.jar")
    }
}
