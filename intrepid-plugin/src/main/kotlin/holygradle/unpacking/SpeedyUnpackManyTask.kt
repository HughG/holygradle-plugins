package holygradle.unpacking

import holygradle.gradle.api.lazyConfiguration
import holygradle.io.FileHelper
import holygradle.io.Link
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ModuleVersionIdentifier
import holygradle.util.addingDefault
import java.io.File

/**
 * This class unpacks multiple packed dependencies, each to a separate target folder.  For each one, there are options
 * specified by an {@link UnpackEntry}.  If the {@link UnpackEntry#applyUpToDateChecks} option is false, this class
 * maintains a "version_info.txt" file in the target folder, which records the names of all ZIP files which have been
 * unpacked into it.  Rather than try to check all the ZIP contents against the unpacked files, which can be slow, it
 * just checks to see if the ZIP file name is in the "version_info.txt" file.  (External changes to that file may
 * break this mechanism, obviously.)
 */
open class SpeedyUnpackManyTask : DefaultTask() {
    private lateinit var unzipper: Unzipper
    private val entries = mutableMapOf<ModuleVersionIdentifier, MutableSet<UnpackEntry>>().addingDefault { mutableSetOf() }

    fun initialize(
        unzipper: Unzipper
    ) {
        this.unzipper = unzipper
        val dependencies = unzipper.dependencies
        if (dependencies != null) {
            dependsOn(dependencies)
        }
        doLast {
            for (entrySet in entries.values) {
                for (entry in entrySet) {
                    processEntry(entry)
                }
            }
        }
    }

    fun addUnpackModuleVersions(source: PackedDependenciesStateSource) {
        // This needs to be done in a lazyConfiguration block (just before task execution), because you can't set a
        // task's inputs/outputs during task execution.
        lazyConfiguration {
            logger.debug("${path} before adding entries: inputs=${inputs.files.files}; outputs=${outputs.files.files}")
            source.allUnpackModules.values.forEach { modules ->
                modules.forEach { module ->
                    module.versions.values.forEach { version ->
                        // Add the unpack entry to unpack the module to the cache or directly to the workspace.
                        addEntry(version)
                    }
                }
            }
            logger.debug("${path} after adding entries: inputs=${inputs.files.files}; outputs=${outputs.files.files}")
        }
    }

    private fun addEntry(versionInfo: UnpackModuleVersion) {
        logger.debug("Adding entry for ${versionInfo}")
        addEntry(versionInfo.moduleVersion, versionInfo.unpackEntry)
        for (parent in versionInfo.parents) {
            addEntry(parent)
        }
    }

    // Only public for testing
    fun addEntry(id: ModuleVersionIdentifier, entry: UnpackEntry) {
        if (entries[id]!!.add(entry)) {
            // We haven't seen an equivalent entry yet, so configure the task to unpack this entry's files, too.

            logger.debug("Adding ${id} inputs ${entry.zipFiles}")
            inputs.files(entry.zipFiles)
            if (entry.applyUpToDateChecks) {
                // Use Gradle's normal pattern for copying files from a ZIP file to the filesystem.
                for (zipFile in entry.zipFiles) {
                    project.zipTree(zipFile).visit { fileDetails ->
                        val file = File(entry.unpackDir, fileDetails.path)
                        logger.debug("Adding ${id} output ${file}")
                        outputs.file(file)
                    }
                }
            } else {
                // Just check the special "info file" to decide whether the unzipped contents are up to date.
                val infoFile = getInfoFile(entry)
                logger.debug("Adding ${id} output ${infoFile}")
                outputs.file(infoFile)
            }
        }
    }

    private fun processEntry(entry: UnpackEntry) {
        val infoFile = getInfoFile(entry)

        val zipFilesToUnpack = if (entry.applyUpToDateChecks) {
            entry.zipFiles
        } else {
            // If we're not using the normal Gradle mechanism, check the info file.
            getZipFilesToUnpack(infoFile, entry)
        }
        if (zipFilesToUnpack.isEmpty()) {
            return
        }

        if (entry.unpackDir.exists()) {
            if (Link.isLink(entry.unpackDir)) {
                logger.info("SpeedyUnpackManyTask: replacing link ${entry.unpackDir} with real directory")
                FileHelper.ensureDeleteDirRecursive(entry.unpackDir)
                FileHelper.ensureMkdirs(entry.unpackDir)
            }
        } else {
            logger.info("SpeedyUnpackManyTask: creating unpack dir ${entry.unpackDir}")
            FileHelper.ensureMkdirs(entry.unpackDir)
        }

        for (file in zipFilesToUnpack) {
            logger.lifecycle("Unpack ${file.name}")
            logger.info("SpeedyUnpackManyTask: extracting ${file.path} to ${entry.unpackDir}")
            unzipper.unzip(file, entry.unpackDir)

            if (!entry.applyUpToDateChecks) {
                // If we're not using the normal Gradle mechanism, update the info file.
                infoFile.setWritable(true)
                infoFile.appendText("Unpacked from: " + file.name + System.lineSeparator())

                logger.info("SpeedyUnpackManyTask: updated info file ${infoFile}, adding ${file.name}")

                val infoText = infoFile.readText()
                logger.info("SpeedyUnpackManyTask: info file ${infoFile} new contents: >>>\n${infoText}<<<")
            }
        }

        if (entry.makeReadOnly) {
            FileHelper.setReadOnlyRecursively(entry.unpackDir)
        }
    }

    private fun getInfoFile(entry: UnpackEntry): File = File(entry.unpackDir, "version_info.txt")

    private fun getZipFilesToUnpack(infoFile: File, entry: UnpackEntry): Collection<File> {
        return if (infoFile.exists()) {
            val infoText = infoFile.readText()
            logger.info("SpeedyUnpackManyTask: existing info file ${infoFile} contents: >>>\n${infoText}<<<")
            val toUnpack = entry.zipFiles.filter { !infoText.contains(it.name) }
            logger.info("SpeedyUnpackManyTask: info file ${infoFile} doesn't contain ${toUnpack.map { it.name }}")
            toUnpack
        } else {
            logger.info("SpeedyUnpackManyTask: didn't find info file ${infoFile}")
            entry.zipFiles
        }
    }
}