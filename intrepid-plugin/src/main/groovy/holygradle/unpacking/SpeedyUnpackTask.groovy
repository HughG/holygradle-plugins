package holygradle.unpacking

import org.gradle.api.*
import holygradle.dependencies.PackedDependencyHandler
import holygradle.Helper
import holygradle.custom_gradle.util.Symlink
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.process.ExecSpec

class SpeedyUnpackTask
    extends DefaultTask
    implements Unpack
{
    private File unpackDir

    public void initialize(
        SevenZipHelper sevenZipHelper,
        File unpackDir,
        PackedDependencyHandler packedDependency,
        Iterable<ResolvedArtifact> artifacts
    ) {
        if (unpackDir == null) {
            throw new RuntimeException("Error: unpackDir is null in SpeedyUnpackTask.initialize.")
        }
        
        this.unpackDir = unpackDir
        File infoFile = new File(unpackDir, "version_info.txt")
        
        dependsOn sevenZipHelper.dependencies

        File localUnpackDir = unpackDir // give closure access to private field
        ext.lazyConfiguration = { Task it ->
            it.onlyIf {
                boolean doInvokeTask = false
                if (infoFile.exists()) {
                    String infoText = infoFile.text
                    logger.info "SpeedyUnpackTask: onlyIf: existing info file ${infoFile} contents: >>>\n${infoText}<<<"
                    for (art in artifacts) {
                        if (!infoText.contains(art.getFile().name)) {
                            logger.info "SpeedyUnpackTask: onlyIf: info file ${infoFile} doesn't contain '${art.getFile().name}'"
                            doInvokeTask = true
                            break
                        }
                    }
                    if (doInvokeTask) {
                        logger.info "SpeedyUnpackTask: onlyIf: info file ${infoFile} doesn't contain some of ${artifacts}"
                    }
                } else {
                    logger.info "SpeedyUnpackTask: onlyIf: didn't find info file ${infoFile}"
                    doInvokeTask = true
                }
                doInvokeTask
            }
            
            it.doFirst {
                if (localUnpackDir.exists()) {
                    if (Symlink.isJunctionOrSymlink(localUnpackDir)) {
                        logger.info "SpeedyUnpackTask: doFirst: replacing symlink ${localUnpackDir} with real directory"
                        localUnpackDir.delete()
                        localUnpackDir.mkdir()
                    }
                } else {
                    logger.info "SpeedyUnpackTask: doFirst: creating directory symlink ${localUnpackDir}"
                    localUnpackDir.mkdir()
                }
                if (infoFile.exists()) {
                    logger.info "SpeedyUnpackTask: doFirst: re-creating info file ${infoFile}"
                    infoFile.delete()
                    infoFile.createNewFile()
                }
            }

            it.doLast {
                artifacts.each { artifact ->
                    logger.info "SpeedyUnpackTask: doLast: extracting ${artifact.getFile().path} to ${localUnpackDir.path}"
                    sevenZipHelper.unzip(artifact.file, localUnpackDir)

                    infoFile.withWriterAppend { BufferedWriter bw ->
                        bw.withPrintWriter { PrintWriter writer ->
                            writer.println("Unpacked from: " + artifact.getFile().name)
                        }
                    }

                    logger.info "SpeedyUnpackTask: doLast: updated info file ${infoFile}, adding ${artifact.getFile().name}"

                    String infoText = infoFile.text
                    logger.info "SpeedyUnpackTask: doLast: info file ${infoFile} new contents: >>>\n${infoText}<<<"
                }

                if (packedDependency != null && packedDependency.shouldMakeReadonly()) {
                    Helper.setReadOnlyRecursively(localUnpackDir)
                }
            }
        }
    }

    @Override
    File getUnpackDir() {
        return unpackDir
    }
}