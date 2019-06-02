package holygradle.links

import holygradle.dependencies.PackedDependenciesSettingsHandler
import holygradle.dependencies.PackedDependencyHandler
import holygradle.dependencies.SourceOverrideHandler
import holygradle.test.AbstractHolyGradleTest
import holygradle.unpacking.DummyBuildScriptDependencies
import holygradle.unpacking.UnpackModuleVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import static org.junit.Assert.*

class LinksToCacheTaskTest extends AbstractHolyGradleTest {
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
            project,
            new DefaultModuleVersionIdentifier("org", moduleName, moduleVersion),
            ((parent == null) ? [] : [parent]).toSet(),
            (parent == null) ? new PackedDependencyHandler(moduleName, getProject()) : null
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
        project.extensions.create("packedDependenciesDefault", PackedDependencyHandler, "rootDefault")
        SourceOverrideHandler.createContainer(project)
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
        task.addUnpackModuleVersion(apricot)

        List<String> expectedLinkParts = ["apricot-1.1"]
        Map<File, File> links = task.getLinks()
        assertLinksMatch(links, expectedLinkParts)
    }

    /**
     * Test that the task isn't going to create links for a module which has no artifacts.
     */
    @Test
    public void testModuleWithNoArtifacts() {
        Project project = getProject()
        UnpackModuleVersion apricot = getUnpackModuleVersion("apricot", "1.1", null, false)

        LinksToCacheTask task = makeLinksTask(project)
        task.addUnpackModuleVersion(apricot)

        List<String> expectedLinkParts = []
        Map<File, File> links = task.getLinks()
        assertLinksMatch(links, expectedLinkParts)
    }

    /**
     * Test that the task returns the expected set of versions to create links for, when there are multiple versions.
     */
    @Test
    public void testMultipleVersions() {
        Project project = getProject()
        UnpackModuleVersion coconut = getUnpackModuleVersion("coconut", "1.3")
        UnpackModuleVersion date = getUnpackModuleVersion("date", "1.4", coconut)

        LinksToCacheTask task = makeLinksTask(project)
        task.addUnpackModuleVersion(coconut)
        task.addUnpackModuleVersion(date)

        List<String> expectedLinkParts = ["coconut-1.3", "date-1.4"]
        Map<File, File> links = task.getLinks()
        assertLinksMatch(links, expectedLinkParts)
    }

    private void assertLinksMatch(Map<File, File> links, Collection<String> expectedLinkParts) {
        Set<String> remainingLinkParts = new HashSet<>()
        remainingLinkParts.addAll(expectedLinkParts)
        boolean linksMissing = false
        links.each { File linkDir, File targetDir ->
            def matchingExpectation = remainingLinkParts.find { targetDir.path.contains(it) }
            if (matchingExpectation == null) {
                linksMissing = true
            } else {
                remainingLinkParts.remove(matchingExpectation)
            }
        }
        assertTrue(
            "Expected links with targets matching ${expectedLinkParts} but got ${links}",
            !linksMissing && remainingLinkParts.empty
        )
    }
}