package holygradle

import holygradle.source_dependencies.SourceDependencyHandlerTest
import holygradle.test.AbstractHolyGradleTest
import org.gradle.api.Project
import org.junit.Test

import static org.junit.Assert.*
import static org.hamcrest.CoreMatchers.*

class HelperTest extends AbstractHolyGradleTest {
    /**
     * Tests that {@link Helper#getTransitiveSourceDependencies} works as expected when sub-projects aren't added to the
     * root project yet.
     *
     * getTransitiveSourceDependencies is called on each run of {@code fetchAllDependencies}, to update the settings
     * file (see {@link SettingsFileHelper#writeSettingsFileAndDetectChange(org.gradle.api.Project)}).  In this case it
     * is supposed to include "dummy" project entries for projects which have a source dependency pointing to them, but
     * which haven't been added to the current Gradle run.  The calling code looks to see if the overall set of projects
     * has changed, and exits with a special code which causes our custom batch file to re-run Gradle, at which point
     * those new projects will be included in the set of "real" projects for the build.  If the Gradle file in the
     * newly checked-out source dependency folder itself contains further source dependencies, those won't be seen
     * until the next run.
     *
     * This test is to check that dummy projects are included in the returned list, but not recursively.
     */
    @Test
    public void testGetTransitiveSourceDependenciesWhenSubProjectsNotAdded() {
        Project rootProject = SourceDependencyHandlerTest.makeProjectWithSourceDependencies("root", testDir)
        Project projectA = SourceDependencyHandlerTest.makeProjectWithSourceDependencies("A", new File(testDir, "A"))
        Project projectB = SourceDependencyHandlerTest.makeProjectWithSourceDependencies("B", new File(testDir, "B"))
        Project projectC = SourceDependencyHandlerTest.makeProjectWithSourceDependencies("C", new File(testDir, "C"))

        SourceDependencyHandlerTest.addSourceDependency(rootProject, "A")
        SourceDependencyHandlerTest.addSourceDependency(projectA, "B")
        SourceDependencyHandlerTest.addSourceDependency(projectB, "C")

        checkSourceDependencies(["http://A"], rootProject)
    }

    /**
     * This test checks that source dependencies are correctly returned for projects which are all in the set of "real"
     * projects; it also checks that the method works for any project, not just the root project.
     *
     * Note that this test has an extra level of transitive dependency, C -> D, to regression-test for a problem where
     * only first-level dependencies are found if the initial project isn't the root project.
     */
    @Test
    public void testGetTransitiveSourceDependenciesWhenSubProjectsAreAdded() {
        Project rootProject = SourceDependencyHandlerTest.makeProjectWithSourceDependencies("root", testDir)
        Project projectA = SourceDependencyHandlerTest.makeProjectWithSourceDependencies(rootProject, "A")
        Project projectB = SourceDependencyHandlerTest.makeProjectWithSourceDependencies(rootProject, "B")
        Project projectC = SourceDependencyHandlerTest.makeProjectWithSourceDependencies(rootProject, "C")
        Project projectD = SourceDependencyHandlerTest.makeProjectWithSourceDependencies(rootProject, "D")

        SourceDependencyHandlerTest.addSourceDependency(rootProject, "A")
        SourceDependencyHandlerTest.addSourceDependency(projectA, "B")
        SourceDependencyHandlerTest.addSourceDependency(projectA, "C")
        SourceDependencyHandlerTest.addSourceDependency(projectB, "C")
        SourceDependencyHandlerTest.addSourceDependency(projectC, "D")

        checkSourceDependencies(["http://A", "http://B", "http://C", "http://D"], rootProject)
        checkSourceDependencies(["http://B", "http://C", "http://D"], projectA)
    }

    private static void checkSourceDependencies(Iterable<String> expectedUrls, Project project) {
        assertThat(
            Helper.getTransitiveSourceDependencies(project)*.url.sort().unique() as String[],
            is(equalTo(expectedUrls as String[]))
        )
    }
}