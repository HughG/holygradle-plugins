package holygradle.scm

import holygradle.buildscript.BuildScriptDependencies
import org.gradle.api.Project

public class SourceControlRepositories {
    public static SourceControlRepository get(
        Project project,
        boolean useDummyIfNecessary = false
    ) {
        File svnFile = new File(project.projectDir, ".svn")
        File hgFile = new File(project.projectDir, ".hg")
        if (svnFile.exists()) {
            new SvnRepository(project.projectDir)
        } else if (hgFile.exists()) {
            BuildScriptDependencies deps =
                project.extensions.findByName("buildScriptDependencies") as BuildScriptDependencies
            String hgPath = new File(deps.getPath("Mercurial"), "hg.exe").path
            File hgrcPath = new File((String)project.ext.hgConfigFile)
            new HgRepository(new HgCommandLine(hgPath, hgrcPath, project.&exec), project.projectDir)
        } else if (useDummyIfNecessary) {
            new DummySourceControl()
        } else {
            null
        }
    }
    
    public static SourceControlRepository createExtension(Project project) {
        project.extensions.add("sourceControl", get(project, true)) as SourceControlRepository
    }
}