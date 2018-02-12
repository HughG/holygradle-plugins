package holygradle.unpacking

import holygradle.buildscript.BuildScriptDependencies
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.script.lang.kotlin.getValue
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Helper class to run {@code 7zip.exe}, assuming it has been added as a
 * {@link holygradle.buildscript.BuildScriptDependency} on a Gradle {@link Project}.
 *
 * This class is factored out separately to help make classes which use it more testable.
 */
class SevenZipHelper
/**
 * Creates a new {@link SevenZipHelper} in the context of a given {@link Project}, which must have a
 * {@link holygradle.buildscript.BuildScriptDependency} set up for "sevenZip".
 * @param project The project which provides a {@link holygradle.buildscript.BuildScriptDependency} for "sevenZip".
 */
constructor(private val project: Project)
: Unzipper {
    private val buildScriptDependencies: BuildScriptDependencies by project.extensions

    private val sevenZipUnpackTask: Copy = buildScriptDependencies.getUnpackTask("sevenZip")
            ?: throw IllegalArgumentException("Project does not have a BuildScriptDependency for 'sevenZip'.")

    /**
     * Returns true if and only if 7Zip can be used, which depends on the executable having been downloaded and unpacked.
     * @return true if and only if 7Zip can be used
     */
    val isUsable: Boolean get() = buildScriptDependencies.getPath("sevenZip") != null

    override val dependencies: Any?
        get() = sevenZipUnpackTask

    override fun unzip(zipFile: File, targetDirectory: File) {
        val sevenZipPath = File((sevenZipUnpackTask.destinationDir as File), "7z.exe").path
        project.exec { spec ->
            spec.commandLine(sevenZipPath, "x", zipFile.path, "-o${targetDirectory.path}", "-bd", "-y")

            // If error logging is enabled, output the errors
            if (project.logger.isEnabled(LogLevel.ERROR)) {
                spec.errorOutput = System.err
            } else {
                // Otherwise redirect to a dead stream
                spec.errorOutput = ByteArrayOutputStream()
            }

            // If info logging is enabled, redirect standard output
            if (project.logger.isEnabled(LogLevel.INFO)) {
                spec.standardOutput = System.out
            } else {
                // Otherwise discard it
                spec.standardOutput = ByteArrayOutputStream()
            }
        }
    }
}
