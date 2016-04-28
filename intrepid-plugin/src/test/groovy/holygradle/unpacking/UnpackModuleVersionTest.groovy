package holygradle.unpacking

import holygradle.dependencies.PackedDependenciesSettingsHandler
import holygradle.dependencies.SourceOverrideHandler
import holygradle.test.*
import org.junit.Test
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import static org.junit.Assert.*
import static org.hamcrest.collection.IsEmptyCollection.*

import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import holygradle.dependencies.PackedDependencyHandler

class UnpackModuleVersionTest extends AbstractHolyGradleTest {
    // The hierarchy of modules described by the test Ivy files is:
    // root (org:root:1.0) [apricot: "aa", blueberry: "sub/bb", coconut: "sub/"]
    // +---aa (org:apricot:1.1) [eggfruit: "../"]
    // +---sub
    // |   +---bb (org:blueberry:1.2)
    // |   +---coconut (org:coconut:1.3) [date: ""]
    // |       +---date (org:date:1.4)
    // +---eggfruit (org:eggfruit:1.5)
    private Map<String, UnpackModuleVersion> getTestModules() {
        Map<String, UnpackModuleVersion> modules = [:]
        modules["root"] = getUnpackModuleVersion(project, "root", "1.0")
        modules["apricot"] = getUnpackModuleVersion(project, "apricot", "1.1", modules["root"])
        modules["blueberry"] = getUnpackModuleVersion(project, "blueberry", "1.2", modules["root"])
        modules["coconut"] = getUnpackModuleVersion(project, "coconut", "1.3", modules["root"])
        modules["date"] = getUnpackModuleVersion(project, "date", "1.4", modules["coconut"])
        modules["eggfruit"] = getUnpackModuleVersion(project, "eggfruit", "1.5", modules["apricot"])
        modules
    }
        
    private File getIvyFile(String fileName) {
        return new File(getTestDir(), fileName)
    }

    private UnpackModuleVersion getUnpackModuleVersion(
        Project project,
        String moduleName,
        String moduleVersion,
        UnpackModuleVersion parent = null
    ) {
        new UnpackModuleVersion(
            new DefaultModuleVersionIdentifier("org", moduleName, moduleVersion),
            getIvyFile(moduleName + ".xml"),
            (parent == null) ? [] : [parent],
            (parent == null) ? new PackedDependencyHandler(moduleName, project) : null
        )
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
    
    @Test
    public void testStandAloneModule() {
        Project project = getProject()
        UnpackModuleVersion apricot = getUnpackModuleVersion(project, "apricot", "1.1")

        assertEquals("org:apricot:1.1", apricot.getFullCoordinate())
        assertNotNull("getPackedDependency not null", apricot.getPackedDependency())
        assertEquals("apricot", apricot.getPackedDependency().name)
        assertEquals("getSelfOrAncestorPackedDependency has a single entry", apricot.getSelfOrAncestorPackedDependencies().size(), 1)
        assertEquals("apricot", apricot.getSelfOrAncestorPackedDependencies().first().name)
        assertEquals("getParent is empty", apricot.getParents().size(), 0)

        UnpackEntry unpackEntry = apricot.getUnpackEntry(project)
        File unpackCache = PackedDependenciesSettingsHandler.findOrCreatePackedDependenciesSettings(project).unpackedDependenciesCacheDir
        assertEquals(new File(unpackCache, "org/apricot-1.1"), unpackEntry.unpackDir)
        assertThat("no zip files", unpackEntry.zipFiles, empty())
        assertFalse("applyUpToDateChecks", unpackEntry.applyUpToDateChecks)
        assertTrue("makeReadOnly", unpackEntry.makeReadOnly)

        assertEquals("apricot", apricot.getTargetDirName())
        assertEquals(new File(project.projectDir, "apricot"), apricot.getTargetPathInWorkspace(project))
    }
    
    @Test
    public void testSingleModuleApplyUpToDateChecks() {
        Project project = getProject()
        UnpackModuleVersion apricot = getUnpackModuleVersion(project, "apricot", "1.1")
    
        PackedDependencyHandler packedDep = apricot.getPackedDependency()
        packedDep.applyUpToDateChecks = true
        
        UnpackEntry unpackEntry = apricot.getUnpackEntry(project)
        assertTrue("UnpackEntry reports correct applyUpToDateChecks value", unpackEntry.applyUpToDateChecks)

    }
    
    @Test
    public void testSingleModuleIncludeVersionNumberInPath() {
        PackedDependencyHandler eggfruitPackedDep = new PackedDependencyHandler("../bowl/eggfruit-<version>-tasty", project)
        UnpackModuleVersion eggfruit = new UnpackModuleVersion(
            new DefaultModuleVersionIdentifier("org", "eggfruit", "1.5"),
            getIvyFile("eggfruit.xml"),
            null,
            eggfruitPackedDep
        )
        
        assertEquals("eggfruit-1.5-tasty", eggfruit.getTargetDirName())
    }
    
    @Test
    public void testSingleModuleNotUnpackToCache() {
        Project project = getProject()
        UnpackModuleVersion coconut = getUnpackModuleVersion(project, "coconut", "1.3")
        
        PackedDependencyHandler coconutPackedDep = coconut.getPackedDependency()
        coconutPackedDep.unpackToCache = false
        
        File targetPath = new File(project.projectDir, "coconut")
        assertEquals(targetPath, coconut.getTargetPathInWorkspace(project))
        assertEquals(new File("coconut"), coconut.getTargetPathInWorkspace(null))

        UnpackEntry unpackEntry = coconut.getUnpackEntry(project)
        assertEquals("unpackDir should be as expected", targetPath, unpackEntry.unpackDir)
        assertThat("no zip files", unpackEntry.zipFiles, empty())
        assertFalse("applyUpToDateChecks", unpackEntry.applyUpToDateChecks)
        assertTrue("makeReadOnly", unpackEntry.makeReadOnly)
    }
    
    @Test
    public void testOneChild() {
        Project project = getProject()
        UnpackModuleVersion coconut = getUnpackModuleVersion(project, "coconut", "1.3")
        UnpackModuleVersion date = getUnpackModuleVersion(project, "date", "1.4", coconut)

        assertNotEquals("getParents not empty", date.getParents().size(), 0)
        assertEquals([coconut], date.getParents())

        UnpackEntry unpackEntry = coconut.getUnpackEntry(project)
        File unpackCache =
            PackedDependenciesSettingsHandler.findOrCreatePackedDependenciesSettings(project).unpackedDependenciesCacheDir
        assertEquals(new File(unpackCache, "org/coconut-1.3"), unpackEntry.unpackDir)
        assertThat("no zip files", unpackEntry.zipFiles, empty())
        assertFalse("applyUpToDateChecks", unpackEntry.applyUpToDateChecks)
        assertTrue("makeReadOnly", unpackEntry.makeReadOnly)

        assertEquals(coconut.getPackedDependency(), date.getSelfOrAncestorPackedDependencies().first())
        
        File targetPath = new File(project.projectDir, "coconut")
        assertEquals(targetPath, coconut.getTargetPathInWorkspace(project))
        assertEquals(new File("coconut"), coconut.getTargetPathInWorkspace(null))
    }
    
    @Test
    public void testRelativePaths() {
        Project project = getProject()
        Map<String, UnpackModuleVersion> modules = getTestModules()

        assertEquals(new File(project.projectDir, "root/../apricot"), modules["apricot"].getTargetPathInWorkspace(project))
        assertEquals(new File(project.projectDir, "root/../blueberry"), modules["blueberry"].getTargetPathInWorkspace(project))
        assertEquals(new File(project.projectDir, "root/../coconut"), modules["coconut"].getTargetPathInWorkspace(project))

        File datePath = modules["date"].getTargetPathInWorkspace(project)
        assertEquals(new File(project.projectDir, "root/../coconut/../date"), datePath)
        assertEquals(new File(project.projectDir, "date"), datePath.getCanonicalFile())

        File eggfruitPath = modules["eggfruit"].getTargetPathInWorkspace(project)
        assertEquals(new File(project.projectDir, "root/../apricot/../eggfruit"), eggfruitPath)
        assertEquals(new File(project.projectDir, "eggfruit"), eggfruitPath.getCanonicalFile())

    }

    @Test
    public void testRelativePathsUsingRelativePathFromIvyXml() {
        Project project = getProject()
        PackedDependenciesSettingsHandler.findOrCreatePackedDependenciesSettings(project).useRelativePathFromIvyXml = true

        Map<String, UnpackModuleVersion> modules = getTestModules()

        assertEquals(new File(project.projectDir, "root/aa"), modules["apricot"].getTargetPathInWorkspace(project))
        assertEquals(new File(project.projectDir, "root/sub/bb"), modules["blueberry"].getTargetPathInWorkspace(project))
        assertEquals(new File(project.projectDir, "root/sub/coconut"), modules["coconut"].getTargetPathInWorkspace(project))
        assertEquals(new File(project.projectDir, "root/sub/coconut/date"), modules["date"].getTargetPathInWorkspace(project))

        File eggfruitPath = modules["eggfruit"].getTargetPathInWorkspace(project)
        assertEquals(new File(project.projectDir, "root/aa/../eggfruit"), eggfruitPath)
        assertEquals(new File(project.projectDir, "root/eggfruit"), eggfruitPath.getCanonicalFile())
    }
}