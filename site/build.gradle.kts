plugins {
    kotlin("jvm") version "1.6.20"
    id("com.google.devtools.ksp") version "1.6.20-1.0.4"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    kotlin("plugin.serialization") version "1.6.20"
}

version = "1.0"

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
}

repositories {
    mavenCentral()
    maven("https://nexus.melijn.com/repository/maven-public/")
}

val ktorVersion = "2.0.0"
val logbackVersion = "1.2.11"

val kordKommons = "1.2.8"
val redgresKommons = "0.0.4"
val apKord = "0.1.2"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.3.2")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    implementation("me.melijn.kordkommons:kommons:$kordKommons")
    implementation("me.melijn.kordkommons:redgres-kommons:$redgresKommons")
    implementation("io.github.microutils:kotlin-logging-jvm:2.1.21")

    // Annotation processors
    val siteAp = project(":site-annotation-processors")
    implementation(siteAp)
    ksp(siteAp)

    val kordkommonsAp = "me.melijn.kordkommons:ap:$apKord"
    implementation(kordkommonsAp)
    ksp(kordkommonsAp)

    // ktor client
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-json:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    // ktor server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-compression:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-auto-head-response:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")

    implementation("io.insert-koin:koin-core:3.1.5")

    // JWT Token stuff
    // https://mvnrepository.com/artifact/org.springframework.security/spring-security-web
    implementation("org.springframework.security:spring-security-web:5.6.2")
    // https://github.com/jwtk/jjwt
    // https://mvnrepository.com/artifact/io.jsonwebtoken/jjwt-impl
    implementation("io.jsonwebtoken:jjwt-impl:0.11.2")
    implementation("io.jsonwebtoken:jjwt-jackson:0.11.2")

    // Database
    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-core
    implementation("org.jetbrains.exposed:exposed-core:0.37.3")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.37.3")
    implementation("org.jetbrains.exposed:exposed-spring-boot-starter:0.37.3")

    // https://search.maven.org/artifact/com.zaxxer/HikariCP
    implementation("com.zaxxer:HikariCP:5.0.1")

    // https://mvnrepository.com/artifact/org.postgresql/postgresql
    implementation("org.postgresql:postgresql:42.3.3")
}

ksp {
    arg("ap_package", "me.melijn.gen")
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
    withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class) {
        kotlinOptions {
            jvmTarget = "16"
        }
    }

    shadowJar {
        isZip64 = true
        mergeServiceFiles()
        archiveFileName.set("site.jar")
    }
}