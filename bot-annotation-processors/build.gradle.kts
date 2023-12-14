plugins {
    kotlin("jvm") version "1.9.21"
}

version = "1.0.0"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")

    maven("https://reposilite.melijn.com/snapshots")
    maven("https://reposilite.melijn.com/shitpack")

    maven("https://duncte123.jfrog.io/artifactory/maven")

    // pooppack mirror
    maven("https://nexus.melijn.com/repository/jitpack/")
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
val kordKommons = "0.0.12-SNAPSHOT"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.google.devtools.ksp:symbol-processing-api:1.9.21-1.0.16")

    implementation("me.melijn.kommons:redgres-kommons:$kordKommons")
    val apKord = "me.melijn.kommons:annotation-processor:$kordKommons"
    val apKordex = "me.melijn.kommons:annotation-processor-kordex:$kordKommons"
    val apRedgres = "me.melijn.kommons:annotation-processor-redgres:$kordKommons"
    implementation(apKord)
    implementation(apKordex)
    implementation(apRedgres)
}

tasks {
    withType(JavaCompile::class) {
        options.encoding = "UTF-8"
    }
    withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class) {
        kotlinOptions {
            jvmTarget = "21"
        }
    }
}