package holygradle

import org.gradle.*
import org.gradle.api.*

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
            new DummySourceControl()
        }
    }
    
    public static def createExtension(Project project) {
        project.extensions.add("sourceControl", get(project.projectDir))
    }
}