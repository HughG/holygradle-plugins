package holygradle.scm

import holygradle.buildscript.BuildScriptDependencies
import org.gradle.api.Project

public class SourceControlRepositories {
    public static SourceControlRepository get(
        Project rootProject,
        File location,
        boolean useDummyIfNecessary = false
    ) {
        File svnFile = new File(location, ".svn")
        File hgFile = new File(location, ".hg")
        if (svnFile.exists()) {
            new SvnRepository(location)
        } else if (hgFile.exists()) {
            BuildScriptDependencies deps =
                rootProject.extensions.findByName("buildScriptDependencies") as BuildScriptDependencies
            String hgPath = new File(deps.getPath("Mercurial"), "hg.exe").path
            new HgRepository(new HgCommandLine(hgPath, rootProject.&exec), location)
        } else if (useDummyIfNecessary) {
            new DummySourceControl()
        } else {
            null
        }
    }
    
    public static SourceControlRepository createExtension(Project project) {
        project.extensions.add("sourceControl", get(project, project.projectDir, true)) as SourceControlRepository
    }
}