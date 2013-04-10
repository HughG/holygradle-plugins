package holygradle.unpacking

import org.gradle.api.Project
import org.gradle.api.tasks.Copy

class UnpackTask extends Copy {
    File unpackDir
    
    public void initialize(Project project, File unpackDir, def artifacts) {
        if (unpackDir == null) {
            throw new RuntimeException("Error: unpackDir is null in UnpackTask.initialize.")
        }
        
        this.unpackDir = unpackDir
        def infoFile = new File(unpackDir, "version_info.txt")
        
        doFirst {
            infoFile.delete()
        }

        artifacts.each { artifact ->
            from project.zipTree(artifact.getFile())
            into unpackDir.path
            doLast {
                def writer = new FileWriter(infoFile, true)
                writer.write("Unpacked from: " + artifact.getFile().name + "\n") 
                writer.close()
            }
        }
    }
}    