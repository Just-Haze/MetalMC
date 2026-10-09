import java.util.Locale

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

if (!file(".git").exists()) {
    val errorText = """

        =====================[ ERROR ]=====================
         The MetalMC project directory is not a properly cloned Git repository.

         In order to build MetalMC from source you must clone
         the MetalMC repository using Git, not download a code
         zip from GitHub.
        ===================================================
    """.trimIndent()
    error(errorText)
}

rootProject.name = "metalmc"

for (name in listOf("metalmc-api", "metalmc-server", "metalmc-checkstyle")) {
    val projName = name.lowercase(Locale.ENGLISH)
    include(projName)
    findProject(":$projName")!!.projectDir = file(name)
}

optionalInclude("test-plugin")

fun optionalInclude(name: String, op: (ProjectDescriptor.() -> Unit)? = null) {
    val settingsFile = file("$name.settings.gradle.kts")
    if (settingsFile.exists()) {
        apply(from = settingsFile)
        findProject(":$name")?.let { op?.invoke(it) }
    } else {
        settingsFile.writeText(
            """
            // Uncomment to enable the '$name' project
            // include(":$name")

            """.trimIndent()
        )
    }
}

gradle.lifecycle.beforeProject {
    val mcVersion = providers.gradleProperty("mcVersion").get().trim()
    val channel = providers.gradleProperty("channel").get().trim()
    val buildNumber = providers.environmentVariable("BUILD_NUMBER").orNull?.trim()?.toInt()
    version = if (buildNumber == null) {
        "$mcVersion.local-SNAPSHOT"
    } else {
        "$mcVersion.build.$buildNumber-${channel.lowercase(Locale.ENGLISH)}"
    }
}
