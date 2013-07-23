package holygradle.unpacking

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.tasks.Copy

class UnpackTask extends Copy {
    private File unpackDir
    
    public void initialize(Project project, File unpackDir, Iterable<ResolvedArtifact> artifacts) {
        if (unpackDir == null) {
            throw new RuntimeException("Error: unpackDir is null in UnpackTask.initialize.")
        }
        
        this.unpackDir = unpackDir
        File infoFile = new File(unpackDir, "version_info.txt")
        
        doFirst {
            infoFile.delete()
        }

        File localUnpackDir = unpackDir // give closure access to private field
        artifacts.each { artifact ->
            from project.zipTree(artifact.getFile())
            into localUnpackDir.path
            doLast {
                infoFile.withWriterAppend { BufferedWriter bw ->
                    bw.withPrintWriter { PrintWriter writer ->
                        writer.println("Unpacked from: " + artifact.getFile().name)
                    }
                }

                String infoText = infoFile.text
                logger.info "UnpackTask: doLast: info file ${infoFile} new contents: >>>\n${infoText}<<<"
            }
        }
    }
}    