package holygradle.scm

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.tmatesoft.svn.core.wc.SVNClientManager

class SvnRepository implements SourceControlRepository {
    private static final String TOOL_SETUP_TASK_NAME = "setUpSubversion"

    private final Task dummyToolSetupTask
    private final File workingCopyDir

    public static findOrCreateToolSetupTask(Project project) {
        Project rootProject = project.rootProject
        Task dummyToolSetupTask = rootProject.tasks.findByName(TOOL_SETUP_TASK_NAME)
        if (dummyToolSetupTask == null) {
            dummyToolSetupTask = rootProject.task(TOOL_SETUP_TASK_NAME, type: DefaultTask) { Task it ->
                it.description = "Dummy task for setting up SVN support."
                it.enabled = false
            }
        }
        return dummyToolSetupTask
    }

    public SvnRepository(Project project, File localPath) {
        dummyToolSetupTask = findOrCreateToolSetupTask(project)
        workingCopyDir = localPath
    }

    @Override
    Task getToolSetupTask() {
        return dummyToolSetupTask
    }

    public File getLocalDir() {
        workingCopyDir
    }
    
    public String getProtocol() {
        "svn"
    }
    
    public String getUrl() {
        SVNClientManager clientManager = SVNClientManager.newInstance();
        clientManager.getStatusClient().doStatus(workingCopyDir, false).getURL().toString()
    }
    
    public String getRevision() {
        SVNClientManager clientManager = SVNClientManager.newInstance();
        clientManager.getStatusClient().doStatus(workingCopyDir, false).getRevision().getNumber()
    }
    
    public boolean hasLocalChanges() {
        // TODO
        return false
    }
}