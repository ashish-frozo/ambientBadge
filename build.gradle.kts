import groovy.json.JsonSlurper
import java.io.File
import java.util.Locale

plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    id("org.cyclonedx.bom") version "1.8.2"
}

data class ComponentLicense(
    val group: String,
    val name: String,
    val version: String,
    val licenses: List<String>
)

val allowedLicenseIds = setOf(
    "APACHE-2.0",
    "MIT",
    "BSD-2-CLAUSE",
    "BSD-3-CLAUSE",
    "ISC",
    "CC0-1.0"
)

val cyclonedxOutputDir = layout.buildDirectory.dir("reports/cyclonedx")
val cyclonedxOutputFile = cyclonedxOutputDir.map { it.file("ambient-scribe.json").asFile }

tasks.cyclonedxBom {
    outputFormat.set("json")
    outputName.set("ambient-scribe")
    includeConfigs.set(listOf("debugRuntimeClasspath", "releaseRuntimeClasspath"))
    destination.set(cyclonedxOutputDir.get().asFile)
}

fun parseComponentsFromBom(bomFile: File): List<ComponentLicense> {
    if (!bomFile.exists()) {
        throw GradleException("CycloneDX BOM not found at ${bomFile.path}. Run `./gradlew cyclonedxBom` first.")
    }

    @Suppress("UNCHECKED_CAST")
    val bom = JsonSlurper().parse(bomFile) as Map<String, Any?>
    val components = bom["components"] as? List<Map<String, Any?>> ?: emptyList()

    return components.map { component ->
        val group = component["group"] as? String ?: ""
        val name = component["name"] as? String ?: ""
        val version = component["version"] as? String ?: ""
        val licenses = (component["licenses"] as? List<Map<String, Any?>>)
            ?.mapNotNull { licenseEntry ->
                val details = licenseEntry["license"] as? Map<String, Any?>
                val id = details?.get("id") as? String
                val title = details?.get("name") as? String
                (id ?: title)?.trim()?.ifBlank { null }
            }
            ?.ifEmpty { listOf("UNKNOWN") }
            ?: listOf("UNKNOWN")

        ComponentLicense(group, name, version, licenses)
    }
}

fun buildNoticeContent(components: List<ComponentLicense>): String {
    val sorted = components.sortedWith(compareBy({ it.group.lowercase(Locale.US) }, { it.name.lowercase(Locale.US) }))

    return buildString {
        appendLine("# Ambient Scribe Open Source Attributions")
        appendLine()
        appendLine("This NOTICE file is auto-generated from the CycloneDX SBOM. Run `./gradlew generateNotice` after dependency updates.")
        appendLine()
        appendLine("| Component | Version | Licenses |")
        appendLine("| --- | --- | --- |")
        sorted.forEach { component ->
            val coordinate = listOf(component.group, component.name)
                .filter { it.isNotBlank() }
                .joinToString(":")
                .ifBlank { component.name }
            val version = component.version.ifBlank { "unspecified" }
            val licenses = component.licenses.joinToString(", ")
            appendLine("| $coordinate | $version | $licenses |")
        }
    }
}

tasks.register("verifyLicenseAllowlist") {
    dependsOn("cyclonedxBom")
    val bomFileProvider = cyclonedxOutputFile

    doLast {
        val components = parseComponentsFromBom(bomFileProvider.get())
        val violations = components.filter { component ->
            component.licenses.none { license ->
                allowedLicenseIds.contains(license.uppercase(Locale.US))
            }
        }

        if (violations.isNotEmpty()) {
            val message = violations.joinToString(separator = "\n") { component ->
                val coordinate = listOf(component.group, component.name)
                    .filter { it.isNotBlank() }
                    .joinToString(":")
                    .ifBlank { component.name }
                "$coordinate:${component.version.ifBlank { "unspecified" }} → ${component.licenses.joinToString(", ")}"
            }
            throw GradleException("Disallowed or unknown licenses detected:\n$message")
        }
    }
}

tasks.register("generateNotice") {
    dependsOn("cyclonedxBom")
    val bomFileProvider = cyclonedxOutputFile
    val noticeFile = rootProject.layout.projectDirectory.file("NOTICE.md")

    outputs.file(noticeFile)

    doLast {
        val components = parseComponentsFromBom(bomFileProvider.get())
        val content = buildNoticeContent(components)

        val file = noticeFile.asFile
        file.parentFile.mkdirs()
        if (!file.exists() || file.readText() != content) {
            file.writeText(content)
        }
    }
}

tasks.register("verifyNoticeUpToDate") {
    dependsOn("cyclonedxBom")
    val bomFileProvider = cyclonedxOutputFile
    val noticeFile = rootProject.layout.projectDirectory.file("NOTICE.md")

    doLast {
        val components = parseComponentsFromBom(bomFileProvider.get())
        val expected = buildNoticeContent(components)
        val current = if (noticeFile.asFile.exists()) noticeFile.asFile.readText() else ""
        if (expected != current) {
            throw GradleException("NOTICE.md is out of date. Run `./gradlew generateNotice` and commit the result.")
        }
    }
}
