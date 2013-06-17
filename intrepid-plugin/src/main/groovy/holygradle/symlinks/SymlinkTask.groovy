package holygradle.symlinks

import holygradle.custom_gradle.util.Symlink
import org.gradle.api.DefaultTask
import org.gradle.api.Project

class SymlinkTask extends DefaultTask {
    public void configure(Project project, File linkDir, File targetDir) {
        boolean linkExists = linkDir.exists()
        boolean isSymlink = Symlink.isJunctionOrSymlink(linkDir)
        
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
