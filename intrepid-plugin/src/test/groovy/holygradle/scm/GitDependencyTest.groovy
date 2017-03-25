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
 * Unit tests for {@link holygradle.scm.GitDependency}
 */
class GitDependencyTest extends AbstractHolyGradleTest {
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
        sourceDependencyHandler.git "${dummyUrlString}@dummy_node"
        sourceDependencyHandler.branch = "some_branch"

        // Set up stub commands which just record what calls were made.  I would use Mockito for this but I don't want
        // to check the arguments directly, I want to check the result of applying the closure arguments to something
        // else.  I could do that with Mockito but it would probably take as much code effort, if not more.
        final List<StubCommand.Effect> effects = []
        final StubCommand csCommand = new StubCommand('cs', effects)
        final StubCommand gitCommand = new StubCommand('git', effects, { ExecSpec spec ->
            def value = (spec.args == ['config', '--get', 'credential.helper']) ? "dummy_helper" : ""
            return [0, value]
        })

        GitDependency dependency = new GitDependency(
            project,
            sourceDependencyHandler,
            csCommand,
            gitCommand
        )
        dependency.checkout()

        println "StubCommand calls:"
        effects.each { println it }
        assertEquals("Expected number of calls", 4, effects.size())

        List<String> configArgs = ["config", "--get", "credential.helper"]
        assertEquals(
            "Get the Git credential.helper config value",
            gitCommand.makeEffect(configArgs, projectDir.absoluteFile, false),
            effects[0]
        )

        final URL parsedUrl = new URL(dummyUrlString)
        final String repoScheme = parsedUrl.getProtocol()
        final String repoHost = parsedUrl.getHost()
        final String credentialName = "git:${repoScheme}://${repoHost}"
        List<String> cacheCredArgs = [credentialName, dummyUser, dummyPassword]
        assertEquals(
            "Cache credentials",
            csCommand.makeEffect(cacheCredArgs, null, null),
            effects[1]
        )

        List<String> cloneArgs = ["clone", "--branch", "some_branch", "--", dummyUrlString, depDir.absolutePath]
        assertEquals(
            "Clone the repo",
            gitCommand.makeEffect(cloneArgs, projectDir.absoluteFile, null),
            effects[2]
        )

        List<String> checkoutArgs = ["checkout", "dummy_node"]
        assertEquals(
            "Checkout the branch",
            gitCommand.makeEffect(checkoutArgs, depDir.absoluteFile, null),
            effects[3]
        )
    }
}
