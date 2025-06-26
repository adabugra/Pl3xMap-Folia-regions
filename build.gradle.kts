//file:noinspection SpellCheckingInspection

plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.17"
}

group = "me.adabugra"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://api.modrinth.com/maven/") {
        name = "modrinth-repo"
    }
}

dependencies {
    paperweight.foliaDevBundle("1.21.4-R0.1-SNAPSHOT")
    compileOnly("maven.modrinth:pl3xmap:1.21.5-527")
}

val targetJavaVersion = 21
java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"

    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
