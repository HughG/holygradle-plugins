package holygradle.scm

import holygradle.testUtil.ScmUtil
import net.lingala.zip4j.core.ZipFile
import org.gradle.api.Project

import static org.junit.Assert.*

class SourceControlRepositoriesSvnTest extends SourceControlRepositoriesTestBase {
    @Override
    protected File getRepoDir(String testName) {
        return new File(getTestDir(), "testSvn" + testName)
    }

    @Override
    protected void prepareRepoDir(File repoDir) {
        File zipFile = new File(getTestDir().parentFile, "test_svn.zip")
        println "unzipping ${zipFile}"
        new ZipFile(zipFile).extractAll(repoDir.path)
    }

    @Override
    protected void checkInitialState(Project project, SourceControlRepository sourceControl) {
        assertTrue(sourceControl instanceof SvnRepository)
        assertEquals("1", sourceControl.getRevision())
        assertEquals("file:///C:/Projects/DependencyManagement/Project/test_svn_repo/trunk", sourceControl.getUrl())
        assertEquals("svn", sourceControl.getProtocol())
    }

    @Override
    protected void addFile(Project project) {
        File helloFile = new File(project.projectDir, "hello.txt")
        helloFile.write("bonjour")
    }

    @Override
    protected void checkStateWithAddedFile(Project project) {
        SourceControlRepository sourceControl = project.extensions.findByName("sourceControl") as SourceControlRepository

        assertTrue(sourceControl.hasLocalChanges())
    }

    @Override
    protected void ignoreDir(Project project, File repoDir, File dirToIgnore) {
        ScmUtil.svnExec(project, "propset", "svn:ignore", dirToIgnore.name, dirToIgnore.parentFile.absolutePath)
    }
}