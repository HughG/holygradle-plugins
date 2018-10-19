package holygradle.scm

interface SourceControlType {
    String getStateDirName()
    String getExecutableName()
    Class<SourceControlRepository> getRepositoryClass()
}