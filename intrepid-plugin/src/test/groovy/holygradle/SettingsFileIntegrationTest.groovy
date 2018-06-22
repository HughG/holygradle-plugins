package holygradle

import holygradle.io.FileHelper
import holygradle.source_dependencies.RecursivelyFetchSourceTask
import holygradle.test.AbstractHolyGradleIntegrationTest
import holygradle.test.WrapperBuildLauncher
import holygradle.testUtil.ScmUtil
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

class SettingsFileIntegrationTest extends AbstractHolyGradleIntegrationTest {
    @Test
    public void testSubprojectsWithoutBuildFiles() {
        File withoutBuildScriptsDir = new File(getTestDir(), "withoutBuildScripts")
        FileHelper.ensureDeleteFile(new File(withoutBuildScriptsDir, "settings.gradle"))
        FileHelper.ensureDeleteFile(new File(withoutBuildScriptsDir, "settings-subprojects.txt"))

        invokeGradle(withoutBuildScriptsDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(RecursivelyFetchSourceTask.NEW_SUBPROJECTS_MESSAGE)
        }

        invokeGradle(withoutBuildScriptsDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
        }
    }

    /*
     * Tests that fetchAllDependencies correctly identifies and signals that "gw fAD" needs to be run again.  Tests are
     * run under the daemon, so the plugin can't auto-restart Gradle, but in that case we now throw an exception, and
     * we can look for the error message.
     */
    @Test
    public void testRecursiveFAD() {
        File baseDir = new File(getTestDir(), "recursiveFAD")
        File src1Dir = copyDirFromTemplate(baseDir, "src1")
        initRepo(src1Dir)
        File src2Dir = copyDirFromTemplate(baseDir, "src2")
        initRepo(src2Dir)
        def rootProjectDir = new File(baseDir, "root")
        FileHelper.ensureDeleteDirRecursive(new File(rootProjectDir, "src1"))
        FileHelper.ensureDeleteFile(new File(rootProjectDir, "settings-subprojects.txt"))

        // First ":fAD" should see that src1 is a src dep of root, add "src1" to the settings file, clone src1, then
        // re-run (both because the settings file changed, and because src1 was fetched).
        System.err.println("fAD 1")
        invokeGradle(rootProjectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(RecursivelyFetchSourceTask.NEW_SUBPROJECTS_MESSAGE)
        }
        // Second ":fAD" should see that there's a new src dep (src1 dep on src2), add "src1/src2" to the settings file,
        // then re-un (because the settings file changed).
        System.err.println("fAD 2")
        invokeGradle(rootProjectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(RecursivelyFetchSourceTask.NEW_SUBPROJECTS_MESSAGE)
        }
        // Third ":fAD" should just succeed.  Then ":src1:fAD" should get a chance to run, clone src2, then re-run (the
        // settings file didn't change, but a new source dep was fetched, which might have more source deps of its own.)
        System.err.println("fAD 3")
        invokeGradle(rootProjectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
            launcher.expectFailure(RecursivelyFetchSourceTask.NEW_SUBPROJECTS_MESSAGE)
        }
        // On the fourth run there are no new source deps, so all of ":fAD", ":src1:fAD", and ":src2:fAD" should
        // succeed.
        System.err.println("fAD 4")
        invokeGradle(rootProjectDir) { WrapperBuildLauncher launcher ->
            launcher.forTasks("fetchAllDependencies")
        }
    }

    private File copyDirFromTemplate(File baseDir, String dirName) {
        File templateDir = new File(baseDir, "${dirName}in")
        File dir = new File(baseDir, dirName)
        FileHelper.ensureDeleteDirRecursive(dir)
        FileUtils.copyDirectory(templateDir, dir)
        return dir
    }

    private void initRepo(File src1Dir) {
        Project project1 = ProjectBuilder.builder().withProjectDir(src1Dir).build()
        ScmUtil.hgExec(project1, "init")
        ScmUtil.hgExec(project1, "add", "build.gradle")
        ScmUtil.hgExec(
            project1,
            "commit",
            "-m", "Initial test state.",
            "-u", "TestUser"
        )
        ScmUtil.hgExec(project1, "phase", "--public", "tip")
    }
}
