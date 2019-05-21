package holygradle.artifacts

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier

private val MODULE_VERSION_IDENTIFIER_REGEX = "([^:]+):([^:]+):([^:]+)".toRegex()

fun String.tryToModuleVersionIdentifier() : ModuleVersionIdentifier? {
    val match = MODULE_VERSION_IDENTIFIER_REGEX.matchEntire(this)
    return if (match == null) {
        null
    } else {
        val values = match.groupValues
        DefaultModuleVersionIdentifier(values[1], values[2], values[3])
    }
}
fun String.toModuleVersionIdentifier() : ModuleVersionIdentifier {
    return this.tryToModuleVersionIdentifier()
        ?: throw RuntimeException("Failed to parse module version identifier: '${this}'")
}