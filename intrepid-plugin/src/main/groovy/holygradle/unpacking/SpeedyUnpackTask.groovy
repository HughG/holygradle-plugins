package holygradle.unpacking

import org.gradle.api.*
import holygradle.dependencies.PackedDependencyHandler
import holygradle.Helper
import holygradle.custom_gradle.util.Symlink
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.process.ExecSpec

class SpeedyUnpackTask extends DefaultTask {
    File unpackDir
    Task sevenZipTask
    
    public void initialize(
        Project project,
        File unpackDir,
        PackedDependencyHandler packedDependency,
        Iterable<ResolvedArtifact> artifacts
    ) {
        if (unpackDir == null) {
            throw new RuntimeException("Error: unpackDir is null in SpeedyUnpackTask.initialize.")
        }
        
        this.unpackDir = unpackDir
        File infoFile = new File(unpackDir, "version_info.txt")
        
        sevenZipTask = project.buildScriptDependencies.getUnpackTask("sevenZip")
        dependsOn sevenZipTask
        
        ext.lazyConfiguration = { Task it ->
            it.onlyIf {
                boolean doInvokeTask = false
                if (infoFile.exists()) {
                    String infoText = infoFile.text
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
            
            it.doFirst {
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

            it.doLast {
                String sevenZipPath = new File(sevenZipTask.destinationDir, "7z.exe").path
                artifacts.each { artifact ->
                    project.exec { ExecSpec spec ->
                        spec.commandLine sevenZipPath, "x", artifact.getFile().path, "-o${unpackDir.path}", "-bd", "-y"
                        spec.setStandardOutput new ByteArrayOutputStream()
                    }

                    infoFile.withPrintWriter { writer ->
                        writer.println("Unpacked from: " + artifact.getFile().name)
                    }
                }
                
                if (packedDependency != null && packedDependency.shouldMakeReadonly()) {
                    Helper.setReadOnlyRecursively(unpackDir)
                }
            }
        }
    }
}    