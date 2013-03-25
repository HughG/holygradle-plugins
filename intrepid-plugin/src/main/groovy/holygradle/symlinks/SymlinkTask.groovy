package holygradle

import holygradle.util.*
import org.gradle.api.*
import org.gradle.api.tasks.*

class SymlinkTask extends DefaultTask {
    public void configure(def project, File linkDir, File targetDir) {        
        def linkExists = linkDir.exists()
        def isSymlink = Symlink.isJunctionOrSymlink(linkDir)
        
        doFirst {
            if (linkExists && !isSymlink) {
                println "-"*80
                println "Could not create a symlink from '${linkDir.path}' to '${targetDir.path}' because the " +
                        "former already exists and is not a symlink. Continuing..."
                println "-"*80
            } else {
                Symlink.rebuild(linkDir, targetDir, project)
            }
        }
    }
}
