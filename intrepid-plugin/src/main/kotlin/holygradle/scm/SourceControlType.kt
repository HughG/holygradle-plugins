package holygradle.scm

interface SourceControlType {
    val stateDirName: String
    val executableName: String
    val repositoryClass: Class<out SourceControlRepository>
}