package holygradle.packaging

class PackageArtifactPlainTextFileHandler implements PackageArtifactTextFileHandler {
    private final String name
    private Collection<String> lines = []

    public PackageArtifactPlainTextFileHandler(String name) {
        this.name = name
    }

    public String getName() {
        return name
    }

    public void add(String text) {
        lines.add(text)
    }
  
    public void writeFile(File targetFile) {
        targetFile.write(lines.join("\n"))
    }
}