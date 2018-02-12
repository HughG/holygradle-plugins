package holygradle.unpacking

import java.io.File

/**
 * Interface for unzipping a zip file to a given location.
 *
 * This class is factored out separately to help make classes which use it more testable.
 */
interface Unzipper {
    /**
     * Returns an object suitable for passing to {@link org.gradle.api.Task#dependsOn(java.lang.Object...)}, or null if
     * no dependencies are required for this unzipper.  Any task which uses this class should be set up to depend on the
     * result of this method, if it is non-null.
     * @return Dependencies required for this class to be used.
     */
    val dependencies: Any?

    /**
     * Unzips {@code zipFile} to directory {@code targetDirectory}.
     * @param zipFile The file to unzip.
     * @param targetDirectory The output directory into which to unzip the {@code zipFile}
     */
    fun unzip(zipFile: File, targetDirectory: File)
}
