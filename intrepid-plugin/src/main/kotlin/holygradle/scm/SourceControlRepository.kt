package holygradle.scm

import java.io.File

interface SourceControlRepository {
    val localDir: File
    
    val protocol: String
    
    val url: String
    
    val revision: String?
    
    val hasLocalChanges: Boolean

    /**
     * Returns true if and only if the source control system is definitely currently ignoring this file.
     * @param file
     * @return True if and only if the source control system is definitely currently ignoring this file.
     * @throws IllegalArgumentException if the argument does not exist or is something other than a normal file
     * (for example, a directory)
     */
    fun ignoresFile(file: File): Boolean
}
