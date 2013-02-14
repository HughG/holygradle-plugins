package holygradle

interface SourceControlRepository {
    File getLocalDir()
    
    String getProtocol()
    
    String getUrl()
    
    String getRevision()
    
    boolean hasLocalChanges()
}

class SourceControlRepositories {
    public static SourceControlRepository get(File location) {
        def svnFile = new File(location, ".svn")
        def hgFile = new File(location, ".hg")
        if (svnFile.exists()) {
            new SvnRepository(location)
        } else if (hgFile.exists()) {
            new HgRepository(location)
        } else {
            //throw new RuntimeException("Unknown repository type at location '${location}'.")
            null
        }
    }
}