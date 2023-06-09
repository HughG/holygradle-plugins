package holygradle.unpacking

import holygradle.dependencies.PackedDependenciesSettingsHandler
import holygradle.dependencies.PackedDependencyOptionsHandler
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
            ((parent == null) ? [] : [parent]).toSet(),
            (parent == null) ? new PackedDependencyHandler(moduleName, makeProject()) : null
        )
    }
    
    private static Project makeProject() {
        Project project = ProjectBuilder.builder().build()
        PackedDependenciesSettingsHandler.findOrCreatePackedDependenciesSettings(project).unpackedDependenciesCacheDir =
            new File("theUnpackCache")
        PackedDependencyHandler.createContainer(project)
        project.ext.buildScriptDependencies = new DummyBuildScriptDependencies(project)
        SourceOverrideHandler.createContainer(project)
        project
    }
    
    @Test
    public void testStandAloneModule() {
        Project project = makeProject()
        UnpackModuleVersion apricot = getUnpackModuleVersion(project, "apricot", "1.1")

        assertEquals("org:apricot:1.1", apricot.getFullCoordinate())
        assertNotNull("getPackedDependency not null", apricot.getPackedDependency())
        assertEquals("apricot", apricot.getPackedDependency().name)
        assertEquals("getSelfOrAncestorPackedDependency has a single entry", apricot.getSelfOrAncestorPackedDependencies().size(), 1)
        assertEquals("apricot", apricot.getSelfOrAncestorPackedDependencies().first().name)
        assertEquals("getParent is empty", apricot.getParents().size(), 0)

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

        PackedDependencyHandler eggfruitPackedDep =
                new PackedDependencyHandler("../bowl/eggfruit-<version>-tasty", project)
        UnpackModuleVersion eggfruit = new UnpackModuleVersion(
                project,
                new DefaultModuleVersionIdentifier("org", "eggfruit", "1.5"),
                new HashSet<UnpackModuleVersion>(),
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

        assertNotEquals("getParents not empty", date.getParents().size(), 0)
        assertEquals([coconut].toSet(), date.getParents())

        UnpackEntry unpackEntry = coconut.getUnpackEntry()
        File unpackCache =
            PackedDependenciesSettingsHandler.findOrCreatePackedDependenciesSettings(project).unpackedDependenciesCacheDir
        assertEquals(new File(unpackCache, "org/coconut-1.3"), unpackEntry.unpackDir)
        assertThat("no zip files", unpackEntry.zipFiles, empty())
        assertFalse("applyUpToDateChecks", unpackEntry.applyUpToDateChecks)
        assertTrue("makeReadOnly", unpackEntry.makeReadOnly)

        assertEquals(coconut.getPackedDependency(), date.getSelfOrAncestorPackedDependencies().first())
        
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