package holygradle.links

import org.gradle.api.Action
import org.gradle.api.Project

class LinkHandler(val fromLocation: String = ".") {
    companion object {
        fun createExtension(project: Project): LinkHandler {
            return project.extensions.create("links", LinkHandler::class.java)
        }
    }

    data class Mapping(val linkPath: String, val targetPath: String )

    val toLocations = mutableListOf<String>()
    private val childHandlers = mutableListOf<LinkHandler>()

    constructor(that: LinkHandler): this(that.fromLocation) {
        this.merge(that)
    }

    // Copy the "to locations" and "child handlers", but not the "fromLocation", from other to this
    private fun merge(other: LinkHandler) {
        to(other.toLocations)
        for (child in other.childHandlers) {
            childHandlers.add(LinkHandler(child))
        }
    }

    fun addFrom(fromLocation: String, handler: LinkHandler) {
        val fromHandler = from(fromLocation)
        fromHandler.merge(handler)
    }
    
    fun from(location: String): LinkHandler {
        var childLocation = location
        if (fromLocation != ".") {
            childLocation = fromLocation + "/" + location
        }
        val child = LinkHandler(childLocation)
        childHandlers.add(child)
        return child
    }
    
    fun from(location: String, c: Action<LinkHandler>): LinkHandler {
        return from(location).apply { c.execute(this) }
    }
    
    fun to(vararg locations: String) {
        to(locations.toList())
    }

    fun to(locations: Iterable<String>) {
        toLocations += locations
    }

    val mappings: Collection<Mapping>
        get() {
            return mutableListOf<Mapping>().apply { collectMappings(this) }
        }
    
    fun countToLocations(): Int {
        return toLocations.size + childHandlers.sumBy { it.countToLocations() }
    }
    
    fun writeScript(str: StringBuilder) {
        if (countToLocations() > 0) {
            str.append("links {\n")
            writeScriptIndented(str, 4)
            str.append("}\n")
            str.append("\n")
        }
    }    
    
    fun writeScriptIndented(str: StringBuilder, indent: Int) {
        var toIndent = indent
        if (toLocations.size > 0 || childHandlers.size > 0) {
            if (fromLocation != ".") {
                str.append(" ".repeat(indent))
                str.append("from(\"")
                str.append(fromLocation)
                str.append("\") {\n")
                toIndent = indent + 4
            }
            if (toLocations.size > 0) {
                str.append(" ".repeat(toIndent))
                str.append("to ")
                str.append(toLocations.map { "\"${it}\"" }.joinToString(", "))
                str.append("\n")
            }
            for (it in childHandlers) {
                it.writeScriptIndented(str, toIndent)
            }
            if (fromLocation != ".") {
                str.append(" ".repeat(indent))
                str.append("}\n")
            }
        }
    }

    private fun getLinkPath(toLocation: String): String {
        val last = toLocation.split("/")[-1]
        return if (fromLocation == ".") {
            last
        } else {
            fromLocation + "/" + last
        }
    }

    private fun getTargetPath(toLocation: String): String {
        return if (fromLocation == ".") {
            toLocation
        } else {
            fromLocation + "/" + toLocation
        }
    }
    
    private fun collectMappings(mappings: MutableCollection<Mapping>) {
        toLocations.mapTo(mappings) { Mapping(getLinkPath(it), getTargetPath(it)) }
        for (child in childHandlers) {
            child.collectMappings(mappings)
        }
    }
}
