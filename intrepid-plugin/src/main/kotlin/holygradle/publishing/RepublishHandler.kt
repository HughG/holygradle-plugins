package holygradle.publishing

import org.gradle.api.Project

open class RepublishHandler {
    companion object {
        @JvmStatic
        fun createExtension(project: Project): RepublishHandler = project.extensions.create("republish", RepublishHandler::class.java)
    }

    private val _replacements = mutableMapOf<String, String>()
    var fromRepository: String? = null
        private set
    var toRepository: String? = null
        private set

    fun replace(find: String, replace: String) {
        _replacements[find] = replace
    }
    
    fun from(fromUrl: String) {
        fromRepository = fromUrl
    }
    
    fun to(toUrl: String) {
        toRepository = toUrl
    }

    val replacements: Map<String, String>
        get() {
            return mutableMapOf<String, String>().apply {
                putAll(_replacements)
                val fromRepo = fromRepository
                val toRepo = toRepository
                if (fromRepo != null && toRepo != null) {
                    this[fromRepo] = toRepo
                }
            }
        }
    
    fun writeScript(str: StringBuilder, indent: Int) {
        val indentString = " ".repeat(indent)
        val indentMoreString = " ".repeat(indent + 4)
        str.append(indentString)
        str.append("republish {\n")
        if (fromRepository != null) {
            str.append(indentMoreString)
            str.append("from \"")
            str.append(fromRepository)
            str.append("\"\n")
        }
        if (toRepository != null) {
            str.append(indentMoreString)
            str.append("to \"")
            str.append(toRepository)
            str.append("\"\n")
        }
        for ((replFrom, replTo) in _replacements) {
            str.append(indentMoreString)
            str.append("replace \"")
            str.append(replFrom)
            str.append("\", \"")
            str.append(replTo)
            str.append("\"\n")
        }
        str.append(indentString)
        str.append("}\n")
    }
}
