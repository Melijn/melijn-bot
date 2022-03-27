plugins {
    kotlin("jvm") version "1.6.10"
    id("com.google.devtools.ksp") version "1.6.10-1.0.4"
    id("com.github.johnrengelman.shadow") version "7.1.2"
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

val ktorVersion = "1.6.8"
val logbackVersion = "1.2.11"
val kordKommons = "1.1.0"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    implementation("me.melijn.kordkommons:kord-kommons:$kordKommons")
    implementation("io.github.microutils:kotlin-logging-jvm:2.1.21")

    // Annotation preprocessors
    implementation(project(":site-annotation-processors"))
    ksp(project(":site-annotation-processors"))

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-json:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization:$ktorVersion")
}

ksp {}

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