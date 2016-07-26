package holygradle.unpacking

import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.Task
import org.gradle.process.ExecSpec

/**
 * Helper class to run {@code 7zip.exe}, assuming it has been added as a
 * {@link holygradle.buildscript.BuildScriptDependency} on a Gradle {@link Project}.
 *
 * This class is factored out separately to help make classes which use it more testable.
 */
class SevenZipHelper implements Unzipper {
    private final Project project
    private final Task sevenZipUnpackTask

    /**
     * Creates a new {@link SevenZipHelper} in the context of a given {@link Project}, which must have a
     * {@link holygradle.buildscript.BuildScriptDependency} set up for "sevenZip".
     * @param project The project which provides a {@link holygradle.buildscript.BuildScriptDependency} for "sevenZip".
     */
    public SevenZipHelper(Project project) {
        this.project = project
        this.sevenZipUnpackTask = project.buildScriptDependencies.getUnpackTask("sevenZip")
        if (!this.sevenZipUnpackTask instanceof Task) {
            throw new IllegalArgumentException("Project does not have a BuildScriptDependency for 'sevenZip'.")
        }
    }

    /**`
     * Returns true if and only if 7Zip can be used, which depends on the executable having been downloaded and unpacked.
     * @return true if and only if 7Zip can be used
     */
    boolean getIsUsable() {
        return project.buildScriptDependencies.getPath("sevenZip") != null
    }

    /**
     * Returns an object suitable for passing to {@link org.gradle.api.Task#dependsOn(java.lang.Object...)}.  Any task
     * which uses this class should be set up to depend on the result of this method.
     * @return Dependencies required for this class to be used.
     */
    @Override
    Object getDependencies() {
        return sevenZipUnpackTask
    }

    /**
     * Unzips {@code zipFile} to directory {@code targetDirectory}.
     * @param zipFile The file to unzip.
     * @param targetDirectory The output directory into which to unzip the {@code zipFile}
     */
    @Override
    void unzip(File zipFile, File targetDirectory) {
        String sevenZipPath = new File((sevenZipUnpackTask.destinationDir as File), "7z.exe").path
        project.exec { ExecSpec spec ->
            spec.commandLine sevenZipPath, "x", zipFile.path, "-o${targetDirectory.path}", "-bd", "-y"

            // If error logging is enabled, output the errors
            if (project.logger.isEnabled(LogLevel.ERROR)) {
                spec.setErrorOutput System.err
            } else {
                // Otherwise redirect to a dead stream
                spec.setErrorOutput new ByteArrayOutputStream()
            }

            // If info logging is enabled, redirect standard output
            if (project.logger.isEnabled(LogLevel.INFO)) {
                spec.setStandardOutput System.out
            } else {
                // Otherwise discard it
                spec.setStandardOutput new ByteArrayOutputStream()
            }
        }
    }
}
