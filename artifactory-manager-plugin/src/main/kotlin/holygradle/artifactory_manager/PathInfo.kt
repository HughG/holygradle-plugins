package holygradle.artifactory_manager

import org.gradle.api.Action
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.function.Predicate

/**
 * Class representing one of the levels in an Ivy-style artifact path: group, module, and version.
 *
 * Instances for groups and modules may have children, and will have a null version value.  Instances for versions will
 * have a non-null version, and won't have children.
 */
internal class PathInfo(
        val parent: PathInfo?,
        artifactory: ArtifactoryAPI,
        val path: String
) {
    private val splitPath = "[^/]+/[^/]+/([^/]+)".toRegex().matchEntire(path)

    val version: String? = splitPath?.groupValues?.get(1)
    private val folderInfo: FolderInfo = artifactory.getFolderInfo(path)
    private var children = folderInfo.children
            .filter { it.folder }
            .map { PathInfo(this, artifactory, path + it.uri) }
    
    constructor(artifactory: ArtifactoryAPI, path: String) : this(null, artifactory, path)

    val creationDate: Date = folderInfo.created

    fun getNewestChild(): PathInfo? {
        var newestChild: PathInfo? = null
        var newestChildCreationDate: Date? = null
        for (child in children) {
            val creation = child.creationDate
            if (newestChildCreationDate == null || creation.after(newestChildCreationDate)) {
                newestChild = child
                newestChildCreationDate = creation
            }
        }
        return newestChild
    }
    
    fun filter(test: Predicate<PathInfo>): Boolean {
        if (children.isEmpty()) {
            return test.test(this)
        } else {
            val newChildren = mutableListOf<PathInfo>()
            for (child in children) {
                if (!child.filter(test)) {
                    newChildren.add(child)
                } else {
                    //println "filtering: ${child.path}"
                }
            }
            children = newChildren
            return false
        }
    }
    
    fun all(action: Action<PathInfo>) {
        if (children.isEmpty()) {
            action.execute(this)
        } else {
            for (child in children) {
                child.all(action)
            }
        }
    }

    override fun toString(): String {
        val size = children.size
        return "${path} (${size} ${if (size == 1) "child" else "children"})"
    }
}