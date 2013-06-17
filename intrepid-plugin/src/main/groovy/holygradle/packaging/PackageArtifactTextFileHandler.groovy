package holygradle.packaging

interface PackageArtifactTextFileHandler {
    final String name

    void writeFile(File targetFile)
}