package holygradle

import holygradle.util.*
import org.gradle.api.*
import org.gradle.api.tasks.*

class SpeedyUnpackTask extends DefaultTask {
    File unpackDir
    Task sevenZipTask
    
    public void initialize(Project project, File unpackDir, PackedDependencyHandler packedDependency, def artifacts) {
        if (unpackDir == null) {
            throw new RuntimeException("Error: unpackDir is null in SpeedyUnpackTask.initialize.")
        }
        
        this.unpackDir = unpackDir
        def infoFile = new File(unpackDir, "version_info.txt")
        
        sevenZipTask = project.buildScriptDependencies.getUnpackTask("sevenZip")
        dependsOn sevenZipTask
        
        ext.lazyConfiguration = {
            onlyIf {
                boolean doInvokeTask = false
                if (infoFile.exists()) {
                    def infoText = infoFile.text
                    for (art in artifacts) { 
                        if (!infoText.contains(art.getFile().name)) {
                            doInvokeTask = true
                        }
                    }
                } else {
                    doInvokeTask = true
                }
                doInvokeTask
            }
            
            doFirst {
                if (unpackDir.exists()) {
                    if (Symlink.isJunctionOrSymlink(unpackDir)) {
                        unpackDir.delete()
                        unpackDir.mkdir()
                    }
                } else {
                    unpackDir.mkdir()
                }
                if (infoFile.exists()) {
                    infoFile.delete()
                    infoFile.createNewFile()
                }
            }
            doLast {
                def sevenZipPath = new File(sevenZipTask.destinationDir, "7z.exe").path
                artifacts.each { artifact ->
                    project.exec {
                        commandLine sevenZipPath, "x", artifact.getFile().path, "-o${unpackDir.path}", "-bd", "-y"
                        setStandardOutput new ByteArrayOutputStream()
                    }
                    
                    def writer = new FileWriter(infoFile, true)
                    writer.write("Unpacked from: " + artifact.getFile().name + "\n") 
                    writer.close()
                }
                
                if (packedDependency != null && packedDependency.shouldMakeReadonly()) {
                    Helper.setReadOnlyRecursively(unpackDir)
                }
            }
        }
    }
}    