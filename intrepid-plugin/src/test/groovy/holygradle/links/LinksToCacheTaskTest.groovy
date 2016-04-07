package holygradle.links

import holygradle.dependencies.PackedDependenciesSettingsHandler
import holygradle.dependencies.PackedDependencyHandler
import holygradle.test.AbstractHolyGradleTest
import holygradle.unpacking.DummyBuildScriptDependencies
import holygradle.unpacking.UnpackModuleVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.core.IsEqual.*

class LinksToCacheTaskTest extends AbstractHolyGradleTest {
    private File getIvyFile(String fileName) {
        return new File(getTestDir(), fileName)
    }

    /**
     * Returns an instance of {@link ResolvedArtifact} whose {@link ResolvedArtifact#getFile()} method will return a
     * {@link File} pointing at {@code fileName}.
     * @param fileName The name to use for the result of {@link ResolvedArtifact#getFile()}.
     * @return A dummy instance of {@link ResolvedArtifact}.
     */
    ResolvedArtifact makeDummyResolvedArtifact(String fileName) {
        File file = new File(fileName)
        return [ getFile : { file } ] as ResolvedArtifact
    }

    private UnpackModuleVersion getUnpackModuleVersion(
        String moduleName,
        String moduleVersion,
        UnpackModuleVersion parent = null,
        boolean addDummyArtifact = true
    ) {
        UnpackModuleVersion version = new UnpackModuleVersion(
            new DefaultModuleVersionIdentifier("org", moduleName, moduleVersion),
            getIvyFile(moduleName + ".xml"),
            parent,
            (parent == null) ? new PackedDependencyHandler(moduleName) : null
        )
        if (addDummyArtifact) {
            // Add a dummy artifact, otherwise the link won't be created (because no artifacts exist to be unpacked).
            version.addArtifacts([makeDummyResolvedArtifact("${moduleName}_dummy_artifact.zip")], "default")
        }
        return version
    }

    private static Project getProject() {
        Project project = ProjectBuilder.builder().build()
        PackedDependenciesSettingsHandler.findOrCreatePackedDependenciesSettings(project).unpackedDependenciesCacheDir =
            new File("theUnpackCache")
        project.ext.buildScriptDependencies = new DummyBuildScriptDependencies(project)
        project
    }

    private static LinksToCacheTask makeLinksTask(Project project) {
        project.task("linksToCache", type: LinksToCacheTask) as LinksToCacheTask
    }

    /**
     * Test that the task returns the expected set of versions to create links for, in the simplest case.
     */
    @Test
    public void testStandAloneModule() {
        Project project = getProject()
        UnpackModuleVersion apricot = getUnpackModuleVersion("apricot", "1.1")

        LinksToCacheTask task = makeLinksTask(project)
        task.addUnpackModuleVersionWithAncestors(apricot)

        Collection<UnpackModuleVersion> versions = task.getOrderedVersions()
        assertThat("Expected versions to be linked", versions.toArray(), equalTo([apricot].toArray()))
    }

    /**
     * Test that the task isn't going to create links for a module which has no artifacts.
     */
    @Test
    public void testModuleWithNoArtifacts() {
        Project project = getProject()
        UnpackModuleVersion apricot = getUnpackModuleVersion("apricot", "1.1", null, false)

        LinksToCacheTask task = makeLinksTask(project)
        task.addUnpackModuleVersionWithAncestors(apricot)

        Collection<UnpackModuleVersion> versions = task.getOrderedVersions()
        assertThat("Expected versions to be linked", versions.toArray(), equalTo([].toArray()))
    }

    /**
     * Test that the task returns the expected set of versions to create links for, when there are two versions, one
     * a dependency of another.
     */
    @Test
    public void testOneChildWithVersionsAddedInOrder() {
        Project project = getProject()
        UnpackModuleVersion coconut = getUnpackModuleVersion("coconut", "1.3")
        UnpackModuleVersion date = getUnpackModuleVersion("date", "1.4", coconut)

        LinksToCacheTask taskWithVersionsAddedInOrder = makeLinksTask(project)
        taskWithVersionsAddedInOrder.addUnpackModuleVersionWithAncestors(coconut)
        taskWithVersionsAddedInOrder.addUnpackModuleVersionWithAncestors(date)

        Collection<UnpackModuleVersion> versions = taskWithVersionsAddedInOrder.getOrderedVersions()
        assertThat("Expected versions to be linked", versions.toArray(), equalTo([coconut, date].toArray()))
    }

    /**
     * Test that the task returns the expected set of versions to create links for, when there are two versions, one
     * a dependency of another, and the versions happen to be added out-of-order.
     */
    @Test
    public void testOneChildWithVersionsAddedOutOfOrder() {
        Project project = getProject()
        UnpackModuleVersion coconut = getUnpackModuleVersion("coconut", "1.3")
        UnpackModuleVersion date = getUnpackModuleVersion("date", "1.4", coconut)

        LinksToCacheTask taskWithVersionsAddedInOrder = makeLinksTask(project)
        taskWithVersionsAddedInOrder.addUnpackModuleVersionWithAncestors(date)
        taskWithVersionsAddedInOrder.addUnpackModuleVersionWithAncestors(coconut)

        Collection<UnpackModuleVersion> versions = taskWithVersionsAddedInOrder.getOrderedVersions()
        assertThat("Expected versions to be linked", versions.toArray(), equalTo([coconut, date].toArray()))
    }
}