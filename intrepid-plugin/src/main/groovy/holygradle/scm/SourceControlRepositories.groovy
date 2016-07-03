package holygradle.scm

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task

public class SourceControlRepositories {
    private static final String TOOL_SETUP_TASK_NAME = "setUpSourceControlTools"

    public static SourceControlRepository create(
        Project rootProject,
        File location,
        boolean useDummyIfNecessary = false
    ) {
        File svnFile = new File(location, ".svn")
        File hgFile = new File(location, ".hg")
        File gitFile = new File(location, ".git")
        if ([svnFile, hgFile, gitFile].every { it.exists() }) {
            throw new RuntimeException(
                "${location} contains Subversion '.svn', Mercurial '.hg' and/o Git '.git' folders, which is not supported, " +
                "because it is impossible to tell which to use for source version information."
            )
        }

        if (svnFile.exists()) {
            new SvnRepository(new CommandLine("svn.exe", rootProject.&exec), location)
        } else if (hgFile.exists()) {
            new HgRepository(new CommandLine("hg.exe", rootProject.&exec), location)
        } else if (gitFile.exists()) {
            new GitRepository(new CommandLine("git.exe", rootProject.&exec), location)
        } else if (useDummyIfNecessary) {
            new DummySourceControl()
        } else {
            null
        }
    }

    public static SourceControlRepository createExtension(Project project) {
        project.extensions.add("sourceControl", create(project, project.projectDir, true)) as SourceControlRepository
    }
}