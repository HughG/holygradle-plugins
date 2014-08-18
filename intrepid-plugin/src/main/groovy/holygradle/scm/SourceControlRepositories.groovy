package holygradle.scm

import holygradle.buildscript.BuildScriptDependencies
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
        if (svnFile.exists()) {
            new SvnRepository(rootProject, location)
        } else if (hgFile.exists()) {
            BuildScriptDependencies deps =
                rootProject.extensions.findByName("buildScriptDependencies") as BuildScriptDependencies
            String hgPath = new File(deps.getPath("Mercurial"), "hg.exe").path
            Task hgUnpackTask = HgRepository.findOrCreateToolSetupTask(rootProject)
            new HgRepository(new HgCommandLine(hgUnpackTask, hgPath, rootProject.&exec), hgUnpackTask, location)
        } else if (useDummyIfNecessary) {
            new DummySourceControl()
        } else {
            null
        }
    }

    public static Task getToolSetupTask(
        Project project
    ) {
        Project rootProject = project.rootProject
        Task toolSetupTask = rootProject.tasks.findByName(TOOL_SETUP_TASK_NAME)
        if (toolSetupTask == null) {
            toolSetupTask = rootProject.task(TOOL_SETUP_TASK_NAME, type: DefaultTask) { Task it ->
                it.description = "Set up support for source control tools used by the Holy Gradle plugins."
                it.dependsOn SvnRepository.findOrCreateToolSetupTask(rootProject)
                it.dependsOn HgRepository.findOrCreateToolSetupTask(rootProject)
            }
        }
        return toolSetupTask
    }

    public static SourceControlRepository createExtension(Project project) {
        project.extensions.add("sourceControl", create(project, project.projectDir, true)) as SourceControlRepository
    }
}