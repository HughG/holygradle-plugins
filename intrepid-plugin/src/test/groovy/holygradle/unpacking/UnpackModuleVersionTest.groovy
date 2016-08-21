package holygradle.unpacking

import holygradle.dependencies.PackedDependenciesSettingsHandler
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
    private Map<String, UnpackModuleVersion> getTestModules(Project project) {
        Map<String, UnpackModuleVersion> modules = [:]
        modules["root"] = getUnpackModuleVersion(project, "root", "1.0")
        modules["apricot"] = getUnpackModuleVersion(project, "apricot", "1.1", modules["root"])
        modules["blueberry"] = getUnpackModuleVersion(project, "blueberry", "1.2", modules["root"])
        modules["coconut"] = getUnpackModuleVersion(project, "coconut", "1.3", modules["root"])
        modules["date"] = getUnpackModuleVersion(project, "date", "1.4", modules["coconut"])
        modules["eggfruit"] = getUnpackModuleVersion(project, "eggfruit", "1.5", modules["apricot"])
        modules
    }

    private UnpackModuleVersion getUnpackModuleVersion(
        Project project,
        String moduleName,
        String moduleVersion,
        UnpackModuleVersion parent = null
    ) {
        new UnpackModuleVersion(
            project,
            new DefaultModuleVersionIdentifier("org", moduleName, moduleVersion),
            parent,
            (parent == null) ? new PackedDependencyHandler(moduleName) : null
        )
    }
    
    private static Project makeProject() {
        Project project = ProjectBuilder.builder().build()
        PackedDependenciesSettingsHandler.findOrCreatePackedDependenciesSettings(project).unpackedDependenciesCacheDir =
            new File("theUnpackCache")
        project.ext.buildScriptDependencies = new DummyBuildScriptDependencies(project)
        project
    }
    
    @Test
    public void testStandAloneModule() {
        Project project = makeProject()
        UnpackModuleVersion apricot = getUnpackModuleVersion(project, "apricot", "1.1")

        assertEquals("org:apricot:1.1", apricot.getFullCoordinate())
        assertNotNull("getPackedDependency not null", apricot.getPackedDependency())
        assertEquals("apricot", apricot.getPackedDependency().name)
        assertNotNull("getSelfOrAncestorPackedDependency not null", apricot.getSelfOrAncestorPackedDependency())
        assertEquals("apricot", apricot.getSelfOrAncestorPackedDependency().name)
        assertNull("getParent is null", apricot.getParent())

        UnpackEntry unpackEntry = apricot.getUnpackEntry()
        File unpackCache = PackedDependenciesSettingsHandler.findOrCreatePackedDependenciesSettings(project).unpackedDependenciesCacheDir
        assertEquals(new File(unpackCache, "org/apricot-1.1"), unpackEntry.unpackDir)
        assertThat("no zip files", unpackEntry.zipFiles, empty())
        assertFalse("applyUpToDateChecks", unpackEntry.applyUpToDateChecks)
        assertTrue("makeReadOnly", unpackEntry.makeReadOnly)

        assertEquals("apricot", apricot.targetDirName)
        assertEquals(new File(project.projectDir, "apricot"), apricot.targetPathInWorkspace)
    }
    
    @Test
    public void testSingleModuleApplyUpToDateChecks() {
        Project project = makeProject()
        UnpackModuleVersion apricot = getUnpackModuleVersion(project, "apricot", "1.1")
    
        PackedDependencyHandler packedDep = apricot.getPackedDependency()
        packedDep.applyUpToDateChecks = true
        
        UnpackEntry unpackEntry = apricot.getUnpackEntry()
        assertTrue("UnpackEntry reports correct applyUpToDateChecks value", unpackEntry.applyUpToDateChecks)

    }
    
    @Test
    public void testSingleModuleIncludeVersionNumberInPath() {
        Project project = makeProject()

        PackedDependencyHandler eggfruitPackedDep = new PackedDependencyHandler("../bowl/eggfruit-<version>-tasty")
        UnpackModuleVersion eggfruit = new UnpackModuleVersion(
            project,
            new DefaultModuleVersionIdentifier("org", "eggfruit", "1.5"),
            null,
            eggfruitPackedDep
        )
        
        assertEquals("eggfruit-1.5-tasty", eggfruit.targetDirName)
    }
    
    @Test
    public void testSingleModuleNotUnpackToCache() {
        Project project = makeProject()
        UnpackModuleVersion coconut = getUnpackModuleVersion(project, "coconut", "1.3")
        
        PackedDependencyHandler coconutPackedDep = coconut.getPackedDependency()
        coconutPackedDep.unpackToCache = false
        
        File targetPath = new File(project.projectDir, "coconut")
        assertEquals(targetPath, coconut.getTargetPathInWorkspace())

        UnpackEntry unpackEntry = coconut.getUnpackEntry()
        assertEquals("unpackDir should be as expected", targetPath, unpackEntry.unpackDir)
        assertThat("no zip files", unpackEntry.zipFiles, empty())
        assertFalse("applyUpToDateChecks", unpackEntry.applyUpToDateChecks)
        assertTrue("makeReadOnly", unpackEntry.makeReadOnly)
    }
    
    @Test
    public void testOneChild() {
        Project project = makeProject()
        UnpackModuleVersion coconut = getUnpackModuleVersion(project, "coconut", "1.3")
        UnpackModuleVersion date = getUnpackModuleVersion(project, "date", "1.4", coconut)
        
        assertNotNull("getParent not null", date.getParent())
        assertEquals(coconut, date.getParent())

        UnpackEntry unpackEntry = coconut.getUnpackEntry()
        File unpackCache =
            PackedDependenciesSettingsHandler.findOrCreatePackedDependenciesSettings(project).unpackedDependenciesCacheDir
        assertEquals(new File(unpackCache, "org/coconut-1.3"), unpackEntry.unpackDir)
        assertThat("no zip files", unpackEntry.zipFiles, empty())
        assertFalse("applyUpToDateChecks", unpackEntry.applyUpToDateChecks)
        assertTrue("makeReadOnly", unpackEntry.makeReadOnly)

        assertEquals(coconut.getPackedDependency(), date.getSelfOrAncestorPackedDependency())
        
        File targetPath = new File(project.projectDir, "coconut")
        assertEquals(targetPath, coconut.getTargetPathInWorkspace())
    }
    
    @Test
    public void testRelativePaths() {
        Project project = makeProject()
        Map<String, UnpackModuleVersion> modules = getTestModules(project)

        assertEquals(new File(project.projectDir, "apricot"), modules["apricot"].targetPathInWorkspace)
        assertEquals(new File(project.projectDir, "blueberry"), modules["blueberry"].targetPathInWorkspace)
        assertEquals(new File(project.projectDir, "coconut"), modules["coconut"].targetPathInWorkspace)
        assertEquals(new File(project.projectDir, "date"), modules["date"].targetPathInWorkspace)
        assertEquals(new File(project.projectDir, "eggfruit"), modules["eggfruit"].targetPathInWorkspace)
    }
}