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
            project,
            new DefaultModuleVersionIdentifier("org", moduleName, moduleVersion),
            { getIvyFile(moduleName + ".xml") },
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

        assertEquals("apricot", apricot.getTargetDirName())
        assertEquals(new File(project.projectDir, "apricot"), apricot.getTargetPathInWorkspace())
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
            { getIvyFile("eggfruit.xml") },
            null,
            eggfruitPackedDep
        )
        
        assertEquals("eggfruit-1.5-tasty", eggfruit.getTargetDirName())
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

        assertEquals(new File(project.projectDir, "root/../apricot"), modules["apricot"].getTargetPathInWorkspace())
        assertEquals(new File(project.projectDir, "root/../blueberry"), modules["blueberry"].getTargetPathInWorkspace())
        assertEquals(new File(project.projectDir, "root/../coconut"), modules["coconut"].getTargetPathInWorkspace())

        File datePath = modules["date"].getTargetPathInWorkspace()
        assertEquals(new File(project.projectDir, "root/../coconut/../date"), datePath)
        assertEquals(new File(project.projectDir, "date"), datePath.getCanonicalFile())

        File eggfruitPath = modules["eggfruit"].getTargetPathInWorkspace()
        assertEquals(new File(project.projectDir, "root/../apricot/../eggfruit"), eggfruitPath)
        assertEquals(new File(project.projectDir, "eggfruit"), eggfruitPath.getCanonicalFile())

    }

    @Test
    public void testRelativePathsUsingRelativePathFromIvyXml() {
        Project project = makeProject()
        PackedDependenciesSettingsHandler.findOrCreatePackedDependenciesSettings(project).useRelativePathFromIvyXml = true

        Map<String, UnpackModuleVersion> modules = getTestModules(project)

        assertEquals(new File(project.projectDir, "root/aa"), modules["apricot"].getTargetPathInWorkspace())
        assertEquals(new File(project.projectDir, "root/sub/bb"), modules["blueberry"].getTargetPathInWorkspace())
        assertEquals(new File(project.projectDir, "root/sub/coconut"), modules["coconut"].getTargetPathInWorkspace())
        assertEquals(new File(project.projectDir, "root/sub/coconut/date"), modules["date"].getTargetPathInWorkspace())

        File eggfruitPath = modules["eggfruit"].getTargetPathInWorkspace()
        assertEquals(new File(project.projectDir, "root/aa/../eggfruit"), eggfruitPath)
        assertEquals(new File(project.projectDir, "root/eggfruit"), eggfruitPath.getCanonicalFile())
    }
}