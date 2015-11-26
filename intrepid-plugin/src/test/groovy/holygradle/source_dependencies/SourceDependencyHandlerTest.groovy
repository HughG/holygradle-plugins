package holygradle.source_dependencies

import holygradle.test.AbstractHolyGradleTest
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import static org.junit.Assert.*

/**
 * Tests for {@link SourceDependencyHandler}.
 */
class SourceDependencyHandlerTest extends AbstractHolyGradleTest {
    /**
     * Tests that {@link SourceDependencyHandler#getSourceDependencyProject} works as expected.
     *
     * The method should find the project matching a given source dependency handler, given some initial project as a
     * starting point.  Normally the initial project would be the project which has the source dependency.  If the
     * source dependency doesn't include a Gradle project (or, it does but it's just been checked out so we need to
     * re-run Gradle to see it), then the method may return null.
     */
    @Test
    public void testGetSourceDependencyProject() {
        Project rootProject = makeProjectWithSourceDependencies("root", testDir)
        Project projectA = makeProjectWithSourceDependencies(rootProject, "A")
        Project projectB = makeProjectWithSourceDependencies(rootProject, "B")
        // Project C is constructed differently, so that it doesn't appear to be under the root project, to simulate the
        // case where a source dependency has been checked out but not (yet) added to the Gradle project hierarchy.
        /*Project projectC =*/ makeProjectWithSourceDependencies("B", new File(testDir, "B"))

        addSourceDependency(rootProject, "A")
        SourceDependencyHandler projectBSourceDependencyHandler = addSourceDependency(projectA, "B")
        SourceDependencyHandler projectCSourceDependencyHandler = addSourceDependency(projectA, "C")

        // Should be able to find project B.
        assertSame(
            "Find project B SDH",
            projectB,
            projectBSourceDependencyHandler.getSourceDependencyProject()
        )

        // Should fail to find project C.
        assertNull(
            "Fail to find project C SDH",
            projectCSourceDependencyHandler.getSourceDependencyProject()
        )

    }

    public static Project makeProjectWithSourceDependencies(String name, File projectDir) {
        Project project = ProjectBuilder.builder().withName(name).withProjectDir(projectDir).build()
        SourceDependencyHandler.createContainer(project)
        return project
    }

    public static Project makeProjectWithSourceDependencies(Project parent, String name) {
        Project project = ProjectBuilder.builder()
            .withParent(parent)
            .withName(name)
            .withProjectDir(new File(parent.projectDir, "name"))
            .build()
        SourceDependencyHandler.createContainer(project)
        return project
    }

    public static SourceDependencyHandler addSourceDependency(
        Project project,
        String name
    ) {
        NamedDomainObjectContainer<SourceDependencyHandler> sourceDependencies =
            project.sourceDependencies as NamedDomainObjectContainer<SourceDependencyHandler>
        final SourceDependencyHandler handler = sourceDependencies.create(name)
        handler.hg("http://${name}".toString())
        return handler
    }
}
