package holygradle.scm

import holygradle.buildscript.BuildScriptDependencies
import holygradle.source_dependencies.SourceDependencyHandler
import holygradle.test.AbstractHolyGradleTest
import holygradle.testUtil.ExecUtil
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.process.ExecSpec
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*
import static org.hamcrest.CoreMatchers.*

/**
 * Unit tests for {@link holygradle.scm.GitDependency}
 */
class GitDependencyTest extends AbstractHolyGradleTest {
    @Test
    public void test() {
        File projectDir = new File(getTestDir(), "project")
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        final String depDirName = "dep"
        final File depDir = new File(projectDir, depDirName)
        SourceDependencyHandler sourceDependencyHandler = new SourceDependencyHandler(depDirName, project)
        sourceDependencyHandler.git "dummy_url@dummy_node"
        sourceDependencyHandler.branch = "some_branch"
        // We're not checking out from a URL which requires authentication, so we can get away with not really setting
        // up the BuildScriptDependencies.
        BuildScriptDependencies buildScriptDependencies = BuildScriptDependencies.initialize(project)

        // Set up a stub GitCommand which just records what calls were made.
        List<ExecSpec> stubSpecs = []
        final Command gitCommand = [
            execute : { Action<ExecSpec> configure ->
                ExecSpec stubSpec = ExecUtil.makeStubExecSpec()
                stubSpecs.add(stubSpec)
                configure.execute(stubSpec)
                return ""
            }
        ] as Command

        GitDependency dependency = new GitDependency(
            project,
            sourceDependencyHandler,
            buildScriptDependencies,
            gitCommand
        )
        dependency.Checkout()

        ExecSpec cloneSpec = stubSpecs[0]
        List<String> expectedCloneArgs = ["clone", "--branch", "some_branch", "--", "dummy_url", depDir.absolutePath]
        assertThat("clone args", cloneSpec.args, is(equalTo(expectedCloneArgs as List<String>)))
        assertEquals("clone working dir", projectDir.absolutePath, cloneSpec.workingDir.absolutePath)

        ExecSpec updateSpec = stubSpecs[1]
        assertThat("update args", updateSpec.args, is(equalTo(["checkout", "dummy_node"] as List<String>)))
        assertEquals("update dir", depDir.absolutePath, updateSpec.workingDir.absolutePath)
    }

}
