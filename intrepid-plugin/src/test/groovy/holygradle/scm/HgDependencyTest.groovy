package holygradle.scm

import holygradle.custom_gradle.plugin_apis.CredentialSource
import holygradle.source_dependencies.SourceDependencyHandler
import holygradle.test.AbstractHolyGradleTest
import org.gradle.api.Project
import org.gradle.process.ExecSpec
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.assertEquals
/**
 * Unit tests for {@link holygradle.scm.HgDependency}
 */
class HgDependencyTest extends AbstractHolyGradleTest {
    @Test
    public void test() {
        File projectDir = new File(getTestDir(), "project")
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        final String dummyUser = "dummy_user"
        final String dummyPassword = "dummy_password"
        project.extensions.add("my", [
            getUsername : { dummyUser },
            getPassword : { dummyPassword },
            username : { String credentialBasis -> dummyUser },
            password : { String credentialBasis -> dummyPassword }
        ] as CredentialSource)
        final String depDirName = "dep"
        final File depDir = new File(projectDir, depDirName)
        final String dummyUrlString = "https://dummy_url"
        SourceDependencyHandler sourceDependencyHandler = new SourceDependencyHandler(depDirName, project)
        sourceDependencyHandler.hg "${dummyUrlString}@dummy_node"
        sourceDependencyHandler.branch = "some_branch"

        // Set up stub commands which just record what calls were made.  I would use Mockito for this but I don't want
        // to check the arguments directly, I want to check the result of applying the closure arguments to something
        // else.  I could do that with Mockito but it would probably take as much code effort, if not more.
        final List<StubCommand.Effect> effects = []
        boolean cachedCredentials = false
        final StubCommand csCommand = new StubCommand('cs', effects, { ExecSpec spec ->
            cachedCredentials = true
            return [0, ""]
        })
        final StubCommand hgCommand = new StubCommand('hg', effects, { ExecSpec spec ->
            if (!cachedCredentials) {
                return [1, "In test: Credentials not cached yet"]
            }
            return [0, ""]
        })

        HgDependency dependency = new HgDependency(
            project,
            sourceDependencyHandler,
            csCommand,
            hgCommand
        )
        dependency.checkout()

        println "StubCommand calls:"
        effects.each { println it }
        assertEquals("Expected number of calls", 4, effects.size())

        List<String> cloneArgs = ["clone", "--branch", "some_branch", "--", dummyUrlString, depDir.absolutePath]
        assertEquals(
            "Clone the repo (failing)",
            hgCommand.makeEffect(cloneArgs, projectDir.absoluteFile, null),
            effects[0]
        )

        final String credUrl = dummyUrlString.split("@")[0]
        final String credentialName = "${dummyUser}@@${credUrl}@Mercurial"
        List<String> cacheCredArgs = [credentialName, dummyUser, dummyPassword]
        assertEquals(
            "Cache credentials",
            csCommand.makeEffect(cacheCredArgs, null, null),
            effects[1]
        )

        assertEquals(
            "Clone the repo (successfully)",
            hgCommand.makeEffect(cloneArgs, projectDir.absoluteFile, null),
            effects[2]
        )

        List<String> checkoutArgs = ["update", "-r", "dummy_node"]
        assertEquals(
            "Update to the revision",
            hgCommand.makeEffect(checkoutArgs, depDir.absoluteFile, null),
            effects[3]
        )
    }

}
