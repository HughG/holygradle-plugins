package holygradle

class PackageArtifactTextFileHandler {
    public final String name
    private def lines = []

    public PackageArtifactTextFileHandler(String name) {
        this.name = name
    }
    
    public void add(String text) {
        lines.add(text)
    }
  
    public void writeFile(File parentDir) {
        new File(parentDir, name).write(lines.join("\n"))
    }
}