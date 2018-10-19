package holygradle.scm

import holygradle.io.FileHelper
import holygradle.test.AbstractHolyGradleTest
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized)
public class SourceControlRepositoriesMultipleSCMFoldersTest extends AbstractHolyGradleTest {
    private final ArrayList<String> sourceControlNames

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    SourceControlRepositoriesMultipleSCMFoldersTest(ArrayList<String> sourceControlNames) {
        this.sourceControlNames = sourceControlNames
    }

    @Parameterized.Parameters
    public static ArrayList<Object[]> data() {
        [
            [[".git", ".svn"]],
            [[".git", ".hg"]],
            [[".hg", ".svn"]],
            [[".git", ".svn", ".hg"]]
        ]*.toArray()
    }

    @Test
    void testMultipleSourceControlFoldersThrowsException() {
        File projectDir = new File(getTestDir(), "testMultipleSourceControlFoldersThrowsException")
        FileHelper.ensureDeleteDirRecursive(projectDir)
        FileHelper.ensureMkdirs(projectDir)
        for (String folderName : sourceControlNames) {
            File folderNameDir = new File(projectDir, folderName)
            FileHelper.ensureMkdirs(folderNameDir)
        }

        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        // Expect an exception to be thrown when creating project in folder with multiple source control types
        exception.expect(RuntimeException)
        SourceControlRepositories.createExtension(project)
    }
}
