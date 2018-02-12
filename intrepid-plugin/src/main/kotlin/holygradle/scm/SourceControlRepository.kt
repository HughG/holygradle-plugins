package holygradle.scm

import java.io.File

interface SourceControlRepository {
    val localDir: File
    
    val protocol: String
    
    val url: String
    
    val revision: String?
    
    val hasLocalChanges: Boolean
}
