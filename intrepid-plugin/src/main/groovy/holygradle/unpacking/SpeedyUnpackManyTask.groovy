package holygradle.unpacking

import holygradle.Helper
import holygradle.io.FileHelper
import holygradle.io.Link
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.file.FileVisitDetails

/**
 * This class unpacks multiple packed dependencies, each to a separate target folder.  For each one, there are options
 * specified by an {@link UnpackEntry}.  If the {@link UnpackEntry#applyUpToDateChecks} option is false, this class
 * maintains a "version_info.txt" file in the target folder, which records the names of all ZIP files which have been
 * unpacked into it.  Rather than try to check all the ZIP contents against the unpacked files, which can be slow, it
 * just checks to see if the ZIP file name is in the "version_info.txt" file.  (External changes to that file may
 * break this mechanism, obviously.)
 */
class SpeedyUnpackManyTask
    extends DefaultTask
{
    private Unzipper unzipper
    private Map<ModuleVersionIdentifier, Set<UnpackEntry>> entries =
        new HashMap<ModuleVersionIdentifier, Set<UnpackEntry>>().withDefault { new HashSet<UnpackEntry>() }

    public void initialize(
        Unzipper unzipper
    ) {
        this.unzipper = unzipper
        dependsOn unzipper.dependencies
        Collection<Set<UnpackEntry>> localEntrySets = entries.values() // capture private for closure
        doLast {
            localEntrySets.each { set -> set.each { processEntry it } }
        }
    }

    public void addUnpackModuleVersions(PackedDependenciesStateSource source) {
        // This needs to be done in a lazyConfiguration block (just before task execution), because you can't set a
        // task's inputs/outputs during task execution.
        ext.lazyConfiguration = { Task it ->
            logger.debug "${it.path} before adding entries: inputs=${inputs.files.files}; outputs=${outputs.files.files}"
            source.allUnpackModules.values().each { Collection<UnpackModule> modules ->
                modules.each { UnpackModule module ->
                    module.versions.values().each { UnpackModuleVersion versionInfo ->
                        // Add the unpack entry to unpack the module to the cache or directly to the workspace.
                        addEntry(versionInfo)
                    }
                }
            }
            logger.debug "${it.path} after adding entries: inputs=${inputs.files.files}; outputs=${outputs.files.files}"
        }
    }

    public void addEntry(UnpackModuleVersion versionInfo) {
        logger.debug "Adding entry for ${versionInfo}"
        addEntry(versionInfo.moduleVersion, versionInfo.unpackEntry)
        versionInfo.parents.each {
            parent -> addEntry(parent)
        }
    }

    public void addEntry(ModuleVersionIdentifier id, UnpackEntry entry) {
        if (entries[id].add(entry)) {
            // We haven't seen an equivalent entry yet, so configure the task to unpack this entry's files, too.

            logger.debug "Adding ${id} inputs ${entry.zipFiles}"
            inputs.files entry.zipFiles
            if (entry.applyUpToDateChecks) {
                // Use Gradle's normal pattern for copying files from a ZIP file to the filesystem.
                entry.zipFiles.each { File zipFile ->
                    project.zipTree(zipFile).visit { FileVisitDetails fileDetails ->
                        final File file = new File(entry.unpackDir, fileDetails.path)
                        logger.debug "Adding ${id} output ${file}"
                        outputs.file(file)
                    }
                }
            } else {
                // Just check the special "info file" to decide whether the unzipped contents are up to date.
                final File infoFile = getInfoFile(entry)
                logger.debug "Adding ${id} output ${infoFile}"
                outputs.file infoFile
            }
        }
    }

    // Public only because Groovy doesn't allow access to private members from within closures.
    public void processEntry(
        UnpackEntry entry
    ) {
        File infoFile = getInfoFile(entry)

        Collection<File> zipFilesToUnpack =
            entry.applyUpToDateChecks ?
                entry.zipFiles :
                // If we're not using the normal Gradle mechanism, check the info file.
                getZipFilesToUnpack(infoFile, entry)
        if (zipFilesToUnpack.empty) {
            return
        }

        if (entry.unpackDir.exists()) {
            if (Link.isLink(entry.unpackDir)) {
                logger.info "SpeedyUnpackManyTask: replacing link ${entry.unpackDir} with real directory"
                FileHelper.ensureDeleteDirRecursive(entry.unpackDir)
                FileHelper.ensureMkdirs(entry.unpackDir)
            }
        } else {
            logger.info "SpeedyUnpackManyTask: creating unpack dir ${entry.unpackDir}"
            FileHelper.ensureMkdirs(entry.unpackDir)
        }

        Unzipper localUnzipper = unzipper // capture private for closure
        zipFilesToUnpack.each { File file ->
            logger.lifecycle "Unpack ${file.name}"
            logger.info "SpeedyUnpackManyTask: extracting ${file.path} to ${entry.unpackDir}"
            localUnzipper.unzip(file, entry.unpackDir)

            if (!entry.applyUpToDateChecks) {
                // If we're not using the normal Gradle mechanism, update the info file.
                infoFile.setWritable(true)
                infoFile.withWriterAppend { BufferedWriter bw ->
                    bw.withPrintWriter { PrintWriter writer ->
                        writer.println("Unpacked from: " + file.name)
                    }
                }

                logger.info "SpeedyUnpackManyTask: updated info file ${infoFile}, adding ${file.name}"

                String infoText = infoFile.text
                logger.info "SpeedyUnpackManyTask: info file ${infoFile} new contents: >>>\n${infoText}<<<"
            }
        }

        if (entry.makeReadOnly) {
            Helper.setReadOnlyRecursively(entry.unpackDir)
        }
    }

    private static File getInfoFile(UnpackEntry entry) {
        new File(entry.unpackDir, "version_info.txt")
    }

    private Collection<File> getZipFilesToUnpack(File infoFile, UnpackEntry entry) {
        if (infoFile.exists()) {
            String infoText = infoFile.text
            logger.info "SpeedyUnpackManyTask: existing info file ${infoFile} contents: >>>\n${infoText}<<<"
            Collection<File> toUnpack = entry.zipFiles.findAll { !infoText.contains(it.name) }
            logger.info "SpeedyUnpackManyTask: info file ${infoFile} doesn't contain ${toUnpack*.name}"
            return toUnpack
        } else {
            logger.info "SpeedyUnpackManyTask: didn't find info file ${infoFile}"
            return entry.zipFiles
        }
    }
}