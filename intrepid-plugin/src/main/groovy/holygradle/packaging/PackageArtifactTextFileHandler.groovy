package holygradle.packaging

interface PackageArtifactTextFileHandler {
    String getName()

    void writeFile(File targetFile)
}