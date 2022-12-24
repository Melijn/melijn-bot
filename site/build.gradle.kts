plugins {
    kotlin("jvm") version "1.7.10"
    id("com.google.devtools.ksp") version "1.7.10-1.0.6"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    kotlin("plugin.serialization") version "1.7.10"
}

version = "1.0"

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
}

repositories {
    mavenCentral()
    maven("https://reposilite.melijn.com/snapshots")
}

val ktorVersion = "2.0.3"
val logbackVersion = "1.2.11"

val kordKommons = "0.0.1-SNAPSHOT"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    implementation("me.melijn.kommons:kommons:$kordKommons")
    implementation("me.melijn.kommons:redgres-kommons:$kordKommons")

    // Annotation processors
    val apKord = "me.melijn.kommons:annotation-processor:$kordKommons"
    val apRedgres = "me.melijn.kommons:annotation-processor-redgres:$kordKommons"
    implementation(apKord)
    implementation(apRedgres)
    ksp(apKord)
    ksp(apRedgres)

    implementation("io.github.microutils:kotlin-logging-jvm:2.1.21")

    // Annotation processors
    val siteAp = project(":site-annotation-processors")
    implementation(siteAp)
    ksp(siteAp)

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

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")

    implementation("io.insert-koin:koin-core:3.2.0")

    // JWT Token stuff
    // https://mvnrepository.com/artifact/org.springframework.security/spring-security-web
    implementation("org.springframework.security:spring-security-web:5.7.5")
    // https://github.com/jwtk/jjwt
    // https://mvnrepository.com/artifact/io.jsonwebtoken/jjwt-impl
    implementation("io.jsonwebtoken:jjwt-impl:0.11.5")
    implementation("io.jsonwebtoken:jjwt-jackson:0.11.5")

    // Database
    // https://mvnrepository.com/artifact/org.jetbrains.exposed/exposed-core
    val exposed = "0.41.1"
    implementation("org.jetbrains.exposed:exposed-core:$exposed")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposed")
    implementation("org.jetbrains.exposed:exposed-spring-boot-starter:$exposed")

    // https://search.maven.org/artifact/com.zaxxer/HikariCP
    implementation("com.zaxxer:HikariCP:5.0.1")

    // https://mvnrepository.com/artifact/org.postgresql/postgresql
    implementation("org.postgresql:postgresql:42.5.0")
}

ksp {
    arg("ap_package", "me.melijn.gen")
    arg("ap_redgres_package", "me.melijn.gen")
    arg("ap_redgres_redis_key_prefix", "melijn:")
    arg("ap_imports", "import org.koin.core.context.GlobalContext;import org.koin.core.component.get;import org.koin.core.parameter.ParametersHolder;")
    arg("ap_interfaces", "")
    arg("ap_init_placeholder", "GlobalContext.get().get<%className%> { ParametersHolder() }")
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