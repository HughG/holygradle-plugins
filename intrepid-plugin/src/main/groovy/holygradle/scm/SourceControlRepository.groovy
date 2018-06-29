package holygradle.scm

public interface SourceControlRepository {
    File getLocalDir()
    
    String getProtocol()
    
    String getUrl()
    
    String getRevision()
    
    boolean hasLocalChanges()

    /**
     * Returns true if and only if the source control system is definitely currently ignoring this file.
     * @param file
     * @return True if and only if the source control system is definitely currently ignoring this file.
     * @throws IllegalArgumentException if the argument does not exist or is something other than a normal file
     * (for example, a directory)
     */
    boolean ignoresFile(File file)
}
