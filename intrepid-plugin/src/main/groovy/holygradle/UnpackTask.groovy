package holygradle

import org.gradle.api.tasks.*

class UnpackTask extends Copy {
    File unpackDir
    
    public void initialize(def project, UnpackModuleVersion unpackModuleVersion) {
        unpackDir = unpackModuleVersion.getUnpackDir(project)
        description = unpackModuleVersion.getUnpackDescription()
        
        def infoFile = new File(unpackDir, "version_info.txt")
        doFirst {
            infoFile.delete()
        }

        unpackModuleVersion.artifacts.each { artifact ->
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