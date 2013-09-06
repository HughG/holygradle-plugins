package holygradle.unpacking

import holygradle.test.*
import org.junit.Test
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import static org.junit.Assert.*

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
        modules["root"] = getUnpackModuleVersion("root", "1.0")
        modules["apricot"] = getUnpackModuleVersion("apricot", "1.1", modules["root"])
        modules["blueberry"] = getUnpackModuleVersion("blueberry", "1.2", modules["root"])
        modules["coconut"] = getUnpackModuleVersion("coconut", "1.3", modules["root"])
        modules["date"] = getUnpackModuleVersion("date", "1.4", modules["coconut"])
        modules["eggfruit"] = getUnpackModuleVersion("eggfruit", "1.5", modules["apricot"])
        modules
    }
        
    private File getIvyFile(String fileName) {
        return new File(getTestDir(), fileName)
    }

    private UnpackModuleVersion getUnpackModuleVersion(String moduleName, String moduleVersion, UnpackModuleVersion parent=null) {
        new UnpackModuleVersion(
            new DefaultModuleVersionIdentifier("org", moduleName, moduleVersion),
            getIvyFile(moduleName + ".xml"),
            parent,
            (parent == null) ? new PackedDependencyHandler(moduleName) : null
        )
    }
    
    private Project getProject() {
        Project project = ProjectBuilder.builder().build()
        project.ext.unpackedDependenciesCache = new File("theUnpackCache")
        project.ext.buildScriptDependencies = new DummyBuildScriptDependencies(project)
        project
    }
    
    @Test
    public void testStandAloneModule() {
        Project project = getProject()
        UnpackModuleVersion apricot = getUnpackModuleVersion("apricot", "1.1")

        assertEquals("org:apricot:1.1", apricot.getFullCoordinate())
        assertNotNull("getPackedDependency not null", apricot.getPackedDependency())
        assertEquals("apricot", apricot.getPackedDependency().name)
        assertNotNull("getParentPackedDependency not null", apricot.getParentPackedDependency())
        assertEquals("apricot", apricot.getParentPackedDependency().name)
        assertNull("getParent is null", apricot.getParent())
                
        Task unpackTask = apricot.getUnpackTask(project)
        assertEquals("extractApricot1.1", unpackTask.name)
        assertEquals(new File(project.ext.unpackedDependenciesCache as File, "org/apricot-1.1"), unpackTask.unpackDir)
        assertEquals("Unpacks dependency 'apricot' (version 1.1) to the cache.", unpackTask.description)
        
        Collection<Task> symlinkTasks = apricot.collectParentSymlinkTasks(project)
        assertEquals(1, symlinkTasks.size())
        Task symlinkTask = symlinkTasks.find() // finds the first (non-null) one
        assertEquals("symlinkApricot1.1", symlinkTask.name)
        
        assertEquals("apricot", apricot.getTargetDirName())
        assertEquals(new File(project.projectDir, "apricot"), apricot.getTargetPathInWorkspace(project))
    }
    
    @Test
    public void testSingleModuleApplyUpToDateChecks() {
        Project project = getProject()
        UnpackModuleVersion apricot = getUnpackModuleVersion("apricot", "1.1")
    
        PackedDependencyHandler packedDep = apricot.getPackedDependency()
        packedDep.applyUpToDateChecks = true
        
        Task unpackTask = apricot.getUnpackTask(project)
        assertTrue("Uses to UnpackTask when applyUpToDateChecks=true", unpackTask instanceof UnpackTask)
    }
    
    @Test
    public void testSingleModuleIncludeVersionNumberInPath() {
        PackedDependencyHandler eggfruitPackedDep = new PackedDependencyHandler("../bowl/eggfruit-<version>-tasty")
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
        UnpackModuleVersion coconut = getUnpackModuleVersion("coconut", "1.3")
        
        PackedDependencyHandler coconutPackedDep = coconut.getPackedDependency()
        coconutPackedDep.unpackToCache = false
        
        File targetPath = new File(project.projectDir, "coconut")
        assertEquals(targetPath, coconut.getTargetPathInWorkspace(project))
        assertEquals(new File("coconut"), coconut.getTargetPathInWorkspace(null))
        
        Task unpackTask = coconut.getUnpackTask(project)
        Unpack unpack = unpackTask as Unpack
        assertEquals(targetPath, unpack.unpackDir)
        assertEquals("Unpacks dependency 'coconut' to coconut.", unpackTask.description)
    }
    
    @Test
    public void testOneChild() {
        Project project = getProject()
        UnpackModuleVersion coconut = getUnpackModuleVersion("coconut", "1.3")
        UnpackModuleVersion date = getUnpackModuleVersion("date", "1.4", coconut)
        
        assertNotNull("getParent not null", date.getParent())
        assertEquals(coconut, date.getParent())
        
        Collection<Task> symlinkTasks = date.collectParentSymlinkTasks(project)
        assertEquals(2, symlinkTasks.size())
        assertEquals("symlinkCoconut1.3", symlinkTasks[0].name)
        assertEquals("symlinkDate1.4", symlinkTasks[1].name)
        
        assertEquals(coconut.getPackedDependency(), date.getParentPackedDependency())
        
        File targetPath = new File(project.projectDir, "coconut")
        assertEquals(targetPath, coconut.getTargetPathInWorkspace(project))
        assertEquals(new File("coconut"), coconut.getTargetPathInWorkspace(null))
    }
    
    @Test
    public void testRelativePaths() {
        Project project = getProject()
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