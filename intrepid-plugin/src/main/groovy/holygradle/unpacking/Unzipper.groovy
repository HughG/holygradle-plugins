package holygradle.unpacking

/**
 * Interface for unzipping a zip file to a given location.
 *
 * This class is factored out separately to help make classes which use it more testable.
 */
interface Unzipper {
    /**
     * Returns an object suitable for passing to {@link org.gradle.api.Task#dependsOn(java.lang.Object...)}.  Any task
     * which uses this class should be set up to depend on the result of this method.
     * @return Dependencies required for this class to be used.
     */
    Object getDependencies()

    /**
     * Unzips {@code zipFile} to directory {@code targetDirectory}.
     * @param zipFile The file to unzip.
     * @param targetDirectory The output directory into which to unzip the {@code zipFile}
     */
    void unzip(File zipFile, File targetDirectory)
}
