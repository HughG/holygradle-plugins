package holygradle.scm

import org.gradle.api.*

public class SourceControlRepositories {
    public static SourceControlRepository get(File location, boolean useDummyIfNecessary=false) {
        def svnFile = new File(location, ".svn")
        def hgFile = new File(location, ".hg")
        if (svnFile.exists()) {
            new SvnRepository(location)
        } else if (hgFile.exists()) {
            new HgRepository(location)
        } else if (useDummyIfNecessary) {
            new DummySourceControl()
        } else {
            null
        }
    }
    
    public static def createExtension(Project project) {
        project.extensions.add("sourceControl", get(project.projectDir, true))
    }
}