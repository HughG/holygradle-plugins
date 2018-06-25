package holygradle.scm

import holygradle.io.FileHelper
import holygradle.testUtil.ScmUtil
import org.gradle.api.Project

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

class SourceControlRepositoriesSvnTest extends SourceControlRepositoriesTestBase {
    private static File getRepoServerDir(File repoDir) {
        new File(repoDir.parentFile, repoDir.name + "_repo")
    }

    private static String getAsServerUrl(File dir) {
        // On Windows, "svn checkout" requires the file URL to start with "file:///C:/..." for a repo on drive C, for
        // example.  Java just turns file paths into "file:/C:/..." so we need to adjust that.
        return dir.toURI().toString()
                .replaceFirst("^file:/([a-zA-Z]:)", "file:///\$1")
    }

    @Override
    protected File getRepoDir(String testName) {
        return new File(getTestDir(), "testSvn" + testName)
    }

    @Override
    protected void prepareRepoDir(File repoDir) {
        File repoServerDir = getRepoServerDir(repoDir.absoluteFile)
        FileHelper.ensureDeleteDirRecursive(repoServerDir)
        ScmUtil.exec(repoDir, 'svnadmin', 'create', repoServerDir.toString())

        Object repoServerUrl = getAsServerUrl(repoServerDir)
        ScmUtil.svnExec(repoDir, "checkout", repoServerUrl, ".")
    }

    @Override
    protected void checkInitialState(Project project, SourceControlRepository sourceControl) {
        // Add a file.
        (new File(project.projectDir, EXAMPLE_FILE)).text = "ahoy"
        ScmUtil.svnExec(project, "add", EXAMPLE_FILE)
        // Set the commit message, user, and date, so that the hash will be the same every time.
        ScmUtil.svnExec(project,
                "commit",
                "-m", "Added another file."
        )
        ScmUtil.svnExec(project, "update") // so we don't have a mixed-revision working copy

        assertTrue(
                "An SvnRepository instance has been created for ${project}",
                sourceControl instanceof SvnRepository
        )
        assertEquals("The initial revision is as expected", "1", sourceControl.getRevision())
        assertEquals(
                "The repo URL is as expected",
                getAsServerUrl(getRepoServerDir(project.projectDir)),
                sourceControl.getUrl() + "/"
        )
        assertEquals(
                "The SourceControlRepository reports its protocol correctly",
                "svn",
                sourceControl.getProtocol()
        )
    }

    @Override
    protected void modifyWorkingCopy(Project project) {
        new File(project.projectDir, EXAMPLE_FILE as String).withPrintWriter {
            PrintWriter w -> w.println("two")
        }
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