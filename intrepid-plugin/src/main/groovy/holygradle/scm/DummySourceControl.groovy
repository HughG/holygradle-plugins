package holygradle.scm

class DummySourceControl implements SourceControlRepository {
    @Override
    public File getLocalDir() { null }

    @Override
    public String getProtocol() { "n/a" }

    @Override
    public String getUrl() { null }

    @Override
    public String getRevision() { null }

    @Override
    public boolean hasLocalChanges() { false }

    @Override
    public boolean ignoresFile(File file) { false }
}
