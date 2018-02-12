package holygradle.custom_gradle.util

object CamelCase {
    fun build(components: Collection<String>): String {
        val camelCase = StringBuilder()
        var firstCharacterLowerCase = true
        components.forEach { component ->
            component.split("[_\\-\\s/]+").forEach { chunk ->
                if (chunk.length > 0) {
                    if (firstCharacterLowerCase) {
                        camelCase.append(chunk[0].toLowerCase())
                    } else {
                        camelCase.append(chunk[0].toUpperCase())
                    }
                    if (chunk.length > 1) {
                        camelCase.append(chunk.subSequence(1..(chunk.length - 1)))
                    }
                    firstCharacterLowerCase = false
                }
            }
        }
        return camelCase.toString()
    }
    
    fun build(vararg components: String): String = build(components.toList())
}