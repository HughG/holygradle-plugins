package holygradle.artifactory_manager

import java.util.*

interface ArtifactoryAPI {
    val repository: String

    fun getNow(): Date
    
    fun getFolderInfoJson(path: String): Map<String, Any>

    fun getFolderInfo(path: String): FolderInfo

    fun getFileInfoJson(path: String): Map<String, Any>

    fun getFileInfo(path: String): FileInfo

    fun removeItem(path: String)
}